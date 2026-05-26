"""Per-user encrypted credential store.

Each user's Webull (and optional AI) credentials are stored encrypted at rest
in a small SQLite database. The encryption key is provided at process start
via the ``CREDENTIALS_MASTER_KEY`` env var (a Fernet key). The Android client
ships its credentials over HTTPS to ``POST /api/v1/setup`` once, and then
sends ``X-User-Id`` on subsequent requests so the backend can look them up.

Storage path defaults to ``/data/credentials.sqlite`` which is mounted as a
persistent Fly.io volume (or local Docker volume) so creds survive restarts.
"""
from __future__ import annotations

import json
import logging
import os
import sqlite3
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from cryptography.fernet import Fernet, InvalidToken

logger = logging.getLogger(__name__)


# ─── Public dataclass ────────────────────────────────────────────────────────

@dataclass(frozen=True)
class UserCredentials:
    """Immutable container for a single user's stored secrets."""
    user_id: str
    webull_device_id: Optional[str] = None
    webull_access_token: Optional[str] = None
    webull_refresh_token: Optional[str] = None
    webull_account_id: Optional[str] = None
    anthropic_api_key: Optional[str] = None
    openai_api_key: Optional[str] = None
    extras: dict[str, str] = field(default_factory=dict)

    def as_dict(self) -> dict[str, str]:
        """Serialize for encryption (only non-null fields)."""
        d: dict[str, str] = {}
        for key in (
            "webull_device_id",
            "webull_access_token",
            "webull_refresh_token",
            "webull_account_id",
            "anthropic_api_key",
            "openai_api_key",
        ):
            value = getattr(self, key)
            if value:
                d[key] = value
        if self.extras:
            d["_extras"] = json.dumps(self.extras)
        return d

    @classmethod
    def from_dict(cls, user_id: str, payload: dict[str, str]) -> "UserCredentials":
        extras_raw = payload.pop("_extras", None)
        extras: dict[str, str] = {}
        if extras_raw:
            try:
                extras = json.loads(extras_raw)
            except json.JSONDecodeError:
                logger.warning("Could not decode extras for user_id=%s", user_id)
        return cls(
            user_id=user_id,
            webull_device_id=payload.get("webull_device_id"),
            webull_access_token=payload.get("webull_access_token"),
            webull_refresh_token=payload.get("webull_refresh_token"),
            webull_account_id=payload.get("webull_account_id"),
            anthropic_api_key=payload.get("anthropic_api_key"),
            openai_api_key=payload.get("openai_api_key"),
            extras=extras,
        )


# ─── Store ───────────────────────────────────────────────────────────────────

class CredentialStoreError(Exception):
    """Raised when the credential store can't be opened or decrypted."""


class CredentialStore:
    """Thread-safe encrypted credential store backed by SQLite + Fernet."""

    _SCHEMA = """
        CREATE TABLE IF NOT EXISTS user_credentials (
            user_id      TEXT PRIMARY KEY,
            ciphertext   BLOB NOT NULL,
            created_at   INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
            updated_at   INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
        );
    """

    def __init__(self, db_path: str, master_key: str) -> None:
        if not master_key:
            raise CredentialStoreError(
                "CREDENTIALS_MASTER_KEY not set. Generate one with: "
                "python -c 'from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())'"
            )
        try:
            self._fernet = Fernet(master_key.encode("utf-8"))
        except (ValueError, TypeError) as exc:
            raise CredentialStoreError(
                f"CREDENTIALS_MASTER_KEY is not a valid Fernet key: {exc}"
            ) from exc

        self._db_path = db_path
        self._lock = threading.RLock()

        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as conn:
            conn.executescript(self._SCHEMA)
            conn.commit()
        logger.info("CredentialStore ready at %s", db_path)

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self._db_path, isolation_level=None)
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA synchronous=NORMAL;")
        return conn

    def save(self, creds: UserCredentials) -> None:
        payload = creds.as_dict()
        if not payload:
            raise CredentialStoreError("Refusing to save empty credential set")
        ciphertext = self._fernet.encrypt(json.dumps(payload).encode("utf-8"))

        with self._lock, self._connect() as conn:
            conn.execute(
                """
                INSERT INTO user_credentials (user_id, ciphertext)
                VALUES (?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    ciphertext = excluded.ciphertext,
                    updated_at = strftime('%s', 'now');
                """,
                (creds.user_id, ciphertext),
            )

    def load(self, user_id: str) -> Optional[UserCredentials]:
        with self._lock, self._connect() as conn:
            row = conn.execute(
                "SELECT ciphertext FROM user_credentials WHERE user_id = ?",
                (user_id,),
            ).fetchone()
        if not row:
            return None
        try:
            plaintext = self._fernet.decrypt(row[0])
        except InvalidToken as exc:
            raise CredentialStoreError(
                f"Could not decrypt credentials for {user_id} — master key changed?"
            ) from exc
        return UserCredentials.from_dict(user_id, json.loads(plaintext.decode("utf-8")))

    def delete(self, user_id: str) -> bool:
        with self._lock, self._connect() as conn:
            cursor = conn.execute(
                "DELETE FROM user_credentials WHERE user_id = ?",
                (user_id,),
            )
            return cursor.rowcount > 0

    def exists(self, user_id: str) -> bool:
        with self._lock, self._connect() as conn:
            row = conn.execute(
                "SELECT 1 FROM user_credentials WHERE user_id = ?",
                (user_id,),
            ).fetchone()
        return row is not None


# ─── Module singleton ────────────────────────────────────────────────────────

_store: Optional[CredentialStore] = None
_store_lock = threading.Lock()


def get_store() -> CredentialStore:
    """Return the process-wide credential store, building it lazily."""
    global _store
    if _store is None:
        with _store_lock:
            if _store is None:
                db_path = os.environ.get(
                    "CREDENTIALS_DB_PATH", "/data/credentials.sqlite"
                )
                master_key = os.environ.get("CREDENTIALS_MASTER_KEY", "")
                _store = CredentialStore(db_path=db_path, master_key=master_key)
    return _store


def reset_store_for_tests() -> None:
    """Test helper — discard the cached singleton."""
    global _store
    with _store_lock:
        _store = None
