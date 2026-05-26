# InvestPro Install Page — Design Decisions

Generated 2026-05-25 from the **UI UX Pro Max v2.0** design-system generator
(`~/.claude/skills/ui-ux-pro-max/scripts/design_system.py`).

Raw recommendation: [design-system-recommendation.json](./design-system-recommendation.json)

## What was applied (kept from the generator)

| Layer | Recommendation | How it shows up in `index.html` |
|---|---|---|
| **Pattern** | "App Store Style Landing" — hero with device mockup, features w/ icons, prominent download CTAs | CSS-only phone mockup framing a green stock-card screen at the top; primary CTA above the fold; 4-card features grid below |
| **Section: features with icons** | 4 short feature cards w/ SVG icons | Live Portfolio, Buy/Sell Signals, AI Chat, Encrypted — each with a Lucide icon |
| **Pre-delivery checklist** | No emojis as icons (use SVG: Heroicons/Lucide), `cursor: pointer` on all clickables, hover transitions 150–300ms, visible focus states, `prefers-reduced-motion` respected, responsive at 375/768/1024/1440 | All applied — Lucide SVGs replaced emoji icons, `:focus-visible` rules added, `@media (prefers-reduced-motion: reduce)` block added, responsive breakpoint at 480px |
| **Anti-patterns to avoid** | "Complex navigation + hidden contact info" | Already a single-page layout with contact in footer — no change needed |

## What was overridden (and why)

| Recommendation | Override | Reason |
|---|---|---|
| **Style: "Social Proof-Focused"** with testimonial carousel, client logos, review star ratings | Skipped — no testimonials or reviews section added | Cousin is the only user. Fabricating social proof for a 1-user personal app would feel dishonest. |
| **Colors: sky-blue palette** (`#0EA5E9 / #38BDF8 / #F0F9FF / #0C4A6E`) | Kept existing dark mode (`#0f1115` bg, `#00d68f` accent, `#f5f5f7` text) | The generator's "category detection" pegged this as a generic Service Landing Page and ignored the "dark mode, premium finance vibe" half of the query. Dark + green is the right genre signal for a trading app. |
| **Typography: Caveat (heading) + Quicksand (body)** — handwritten, "personal, friendly, casual, warm, charming" | Replaced with **Inter** (loaded from Google Fonts, with `cv02 cv03 cv04 cv11` font-feature-settings for monospace numerals) | Caveat is a handwriting font. Wrong vibe for a finance app. Inter is the de-facto standard for fintech UI (Stripe, Linear, Robinhood, Mercury) — it nails the "premium finance" half of the query. |
| **Section: "Reviews/ratings"** | Skipped | Same reason as social proof — none exist. |
| **Section: "Screenshots carousel"** | Skipped (for now — single phone mockup serves the same purpose) | No real screenshots until cousin installs the app. The CSS-only phone mockup with a live-looking stock card covers the visual-proof slot until real screenshots exist. Easy to add a `<section class="screenshots">` later once the cousin has the app installed. |

## Overall verdict on the generator

Useful but blunt. It correctly identified the structural pattern ("App Store Style Landing") and the pre-delivery quality checklist — both genuine wins. It overfit to "Android app" → "social proof B2B SaaS" and ignored the dark/premium half of the query, producing a color + typography stack that would have been actively wrong.

**Net: ~60% of the recommendations applied verbatim, ~40% overridden.** The pattern slot was the high-leverage win — it surfaced "add a device mockup hero" and "features with icons" which weren't in the original page and meaningfully improved it.

## Future-proofing

The Webull stock card in the phone mockup is a static placeholder. Once the cousin has the app installed and confirmed working, the page can be upgraded to:

1. Add a real screenshots carousel (Portfolio / Signals / AI Chat screens)
2. Add a `<section class="metrics">` with stat-counter animations (e.g., "0 outages", "100% encrypted") — generator recommended these animations under "Key Effects"
3. Add an optional dark-mode-only logo asset (`/favicon.png`, `/og-image.png` referenced in the `<head>` but not yet created)
