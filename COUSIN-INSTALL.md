# Install InvestPro on Your Phone

> The whole thing takes about 60 seconds.

## 1. Tap this link from your Android phone

**https://investpro.nhsindy.com**

You'll see a big green button. Tap it.

## 2. Install the app

When the download finishes, tap the file in your notification bar (or open
your **Downloads** folder and tap `InvestPro-latest.apk`).

The first time you ever install an app this way, Android shows a warning:

> *"For your security, your phone isn't allowed to install unknown apps from this source."*

That's normal. Tap **Settings** → flip on **Allow from this source** → hit
your back button → tap **Install**.

## 3. Connect your Webull account

The app opens to a **Setup** screen with four boxes:

| Field | Where to find it |
|---|---|
| **Account ID** | Open Webull in a desktop browser → sign in → check the URL after `/account/`. It's an 8–9 digit number. |
| **Access Token** | Desktop browser → press `F12` → **Network** tab → click any request to `tradeapi.webullbroker.com` → look for the `access_token:` header. |
| **Refresh Token** *(optional)* | Same place as above — the `refresh_token:` header. |
| **Device ID** *(optional)* | Same place — the `did:` header. |

Paste them in, tap **Save & Connect**. Done.

## 4. You're in

- **Market** — search any ticker, see live quotes + charts.
- **Signals** — the AI scans your watchlist every minute and notifies you on buy/sell signals.
- **Portfolio** — your real Webull positions, P&L, buying power.
- **AI Chat** — ask anything. *"Should I buy NVDA today?" "What's my AAPL cost basis?"*
- **Settings** — re-enter creds if Webull logs you out, or sign out entirely.

## Troubleshooting

**"App not installed" error**
→ You probably have an older test version. Long-press the InvestPro icon →
**App info** → **Uninstall** → try again.

**"Can't reach server" inside the app**
→ The backend is sleeping. Wait 30 seconds and tap refresh — it auto-wakes.

**Webull says my session expired**
→ Open the app → bottom-right **Settings** → paste a fresh `access_token`
from the desktop browser → **Save & Connect**.

**Something's broken**
→ Text Nick: **317-893-3717**.

---

*Built by Nick's Handyman Service, LLC for personal use. Not financial advice.
Trades execute against your real Webull account — double-check before you tap.*
