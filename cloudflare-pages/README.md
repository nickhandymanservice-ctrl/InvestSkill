# InvestPro Install Landing Page (Cloudflare Pages)

Static one-pager served at **https://investpro.nhsindy.com/**.

## What it does

- Shows a big green **Download & Install** button.
- Shows a QR code so the user can scan from a different device.
- Walks the cousin through the 5-step install flow.
- Auto-discovers the latest APK from the GitHub Releases API, so when CI
  publishes a new release the button updates without any HTML edit.

## Deploy

### One-time setup

```bash
# From the repo root, install wrangler if you haven't:
npm i -g wrangler
wrangler login

# Create a new Pages project pointed at this folder.
cd cloudflare-pages
wrangler pages project create investpro
```

### Each deploy

```bash
cd cloudflare-pages
wrangler pages deploy . --project-name investpro --branch main
```

### Custom domain

In the Cloudflare dashboard:

1. Pages → **investpro** project → **Custom domains** → **Set up a custom domain**
2. Enter `investpro.nhsindy.com`
3. Cloudflare auto-creates the CNAME if `nhsindy.com` is already on your CF account.

## Companion: APK hosting on R2

The button points at `https://apk.nhsindy.com/InvestPro-latest.apk` which is
served from a public Cloudflare R2 bucket.

```bash
# One-time bucket creation
wrangler r2 bucket create investpro-apk

# In dashboard → R2 → investpro-apk → Settings → Public access
# → "Connect Custom Domain" → apk.nhsindy.com

# Each upload (CI does this automatically — see .github/workflows/android-build.yml)
wrangler r2 object put investpro-apk/InvestPro-latest.apk \
  --file=app-release.apk \
  --content-type=application/vnd.android.package-archive
```
