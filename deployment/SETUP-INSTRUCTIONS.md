# InvestPro — One-Time Operator Setup

This is the **owner-side** runbook. Do this once. After that, every push to
`main` auto-builds the APK, publishes a release, and uploads to Cloudflare R2.

The cousin only needs [COUSIN-INSTALL.md](../COUSIN-INSTALL.md).

---

## Architecture recap

```
┌─────────────────────────┐         ┌──────────────────────────────┐
│ Cousin's Android phone  │   HTTPS │ Fly.io: investpro-api        │
│  - APK from R2          │ ──────► │  - FastAPI                   │
│  - X-User-Id header     │         │  - Encrypted SQLite cred DB  │
│  - In-app Webull creds  │         │  - Talks to Webull API       │
└──────────┬──────────────┘         └──────────────────────────────┘
           │
           │ download
           ▼
┌─────────────────────────┐         ┌──────────────────────────────┐
│ apk.nhsindy.com (R2)    │ ◄────── │ GitHub Actions on push       │
└─────────────────────────┘         │  - Build signed APK          │
                                    │  - Upload to R2              │
┌─────────────────────────┐         │  - Attach to Release         │
│ investpro.nhsindy.com   │         └──────────────────────────────┘
│ (CF Pages landing page) │
└─────────────────────────┘
```

---

## Step 1 — Generate the Android signing keystore (5 min)

This is the key that signs every release APK. Generate **once**, never
regenerate (otherwise existing installs won't be able to upgrade).

```bash
# From any machine with JDK 17 installed:
keytool -genkey -v \
  -keystore investpro-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias investpro \
  -storepass 'CHANGE-ME-STRONG-PASSWORD' \
  -keypass  'CHANGE-ME-STRONG-PASSWORD' \
  -dname "CN=Nick's Handyman Service LLC, OU=InvestPro, O=NHS, L=Greenwood, ST=IN, C=US"

# Base64-encode it for GitHub secrets:
base64 -w 0 investpro-release.jks > investpro-release.jks.b64
```

Store `investpro-release.jks` in your password manager. **Losing this file
means you can never publish an upgrade.**

---

## Step 2 — Set GitHub repo secrets (3 min)

```bash
# From the repo root, with `gh` authenticated:
gh secret set ANDROID_KEYSTORE_BASE64 < investpro-release.jks.b64
gh secret set ANDROID_KEYSTORE_PASSWORD --body 'YOUR-STRONG-PASSWORD'
gh secret set ANDROID_KEY_ALIAS        --body 'investpro'
gh secret set ANDROID_KEY_PASSWORD     --body 'YOUR-STRONG-PASSWORD'
```

---

## Step 3 — Deploy the backend to Fly.io (10 min)

```bash
# Install flyctl once:
#   PowerShell:  iwr https://fly.io/install.ps1 -useb | iex
#   macOS/Linux: curl -L https://fly.io/install.sh | sh

fly auth login

# From repo root:
cd backend

# Launch the app (this reads fly.toml and creates the app + volume).
fly launch --copy-config --name investpro-api --region ord --no-deploy

# Create the 1 GB persistent volume for the encrypted credential DB.
fly volumes create investpro_data --region ord --size 1 --yes

# Generate the master encryption key and set it as a secret.
python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())" \
  | xargs -I{} fly secrets set CREDENTIALS_MASTER_KEY='{}'

# Set CORS to your landing page domain.
fly secrets set CORS_ALLOWED_ORIGINS='https://investpro.nhsindy.com'

# Deploy.
fly deploy

# Verify:
fly status
curl https://investpro-api.fly.dev/health
```

Now point a Cloudflare DNS record at the Fly app:

1. Cloudflare dashboard → `nhsindy.com` → **DNS**
2. Add a **CNAME** record:
   - Name: `api.investpro`
   - Target: `investpro-api.fly.dev`
   - Proxy status: **Proxied** (orange cloud)

You can now hit `https://api.investpro.nhsindy.com/health` and it should
return `{"status":"healthy","version":"1.0.0"}`.

---

## Step 4 — Deploy the install landing page (5 min)

```bash
npm i -g wrangler
wrangler login

cd cloudflare-pages
wrangler pages project create investpro --production-branch main
wrangler pages deploy . --project-name investpro --branch main
```

In the Cloudflare dashboard:

1. Pages → `investpro` → **Custom domains** → add `investpro.nhsindy.com`

---

## Step 5 — Create the R2 bucket for APK hosting (5 min)

```bash
wrangler r2 bucket create investpro-apk
```

In the Cloudflare dashboard:

1. R2 → `investpro-apk` → **Settings**
2. **Public access** → connect custom domain → `apk.nhsindy.com`

Then add three more GitHub secrets so CI can upload to R2:

```bash
gh secret set CF_ACCOUNT_ID         --body 'YOUR_ACCOUNT_ID'
gh secret set CF_R2_ACCESS_KEY_ID   --body 'YOUR_R2_TOKEN_ID'
gh secret set CF_R2_SECRET_ACCESS_KEY --body 'YOUR_R2_TOKEN_SECRET'
```

(Generate the R2 token at: dashboard → R2 → **Manage R2 API tokens** →
**Create API token** → permission: **Object Read & Write** → bucket: `investpro-apk`.)

---

## Step 6 — Cut the first release (1 min)

```bash
gh workflow run android-build.yml -f release_tag=v1.0.0
```

In ~5 minutes:
- A signed APK is attached to `https://github.com/nickhandymanservice-ctrl/InvestSkill/releases/tag/v1.0.0`
- `https://apk.nhsindy.com/InvestPro-latest.apk` serves it
- `https://investpro.nhsindy.com` shows the install page

---

## Step 7 — Send the cousin the link

That's it.

```text
Hey — your trading app is ready.

  👉  https://investpro.nhsindy.com

Tap from your phone, follow the 5 steps on the page,
paste your Webull stuff into the first screen, you're trading.

Text me at 317-893-3717 if anything blows up.

— Nick
```

---

## Future updates

Push any change to `android-app/` on the `main` branch → CI auto-builds a
nightly APK and replaces `InvestPro-latest.apk` in R2. The cousin just
re-downloads from the same link to update.

For an official versioned release: `gh workflow run android-build.yml -f release_tag=v1.0.1`.
