# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) and other AI coding assistants when working with code in this repository.

## Project Overview

**InvestSkill** is a multi-platform "skills/prompts" project: a collection of **21 professional investment-analysis frameworks** for US stock markets. It is **not traditional software** — there is no application runtime. Each "skill" is a structured Markdown framework (a prompt) that guides an AI assistant through an investment-analysis workflow (e.g. stock evaluation, DCF valuation, earnings-call analysis) and ends with a standardized **Investment Signal Block**.

The same content is packaged for multiple AI platforms simultaneously: Claude Code (native plugin with slash commands), Cursor IDE, Gemini CLI, GitHub Copilot, and "any LLM" via copy-paste of the universal prompt files. The Node.js code in `scripts/` exists only for testing, validation, release automation, and docs-site generation — never for serving analysis.

- **Version**: 1.6.0 (authoritative source: the two JSON manifests — see Version Consistency below)
- **Skills**: 21 (auto-discovered from `plugins/us-stock-analysis/skills/`)
- **Universal prompts**: 21 files in `prompts/`
- **Node**: ≥18.0.0 (CI uses Node 20)
- **License**: MIT
- **Disclaimer**: Educational/research only; not financial advice.

## Repository Structure

```
InvestSkill/
├── plugins/us-stock-analysis/          # Claude Code plugin
│   ├── .claude-plugin/plugin.json      # Plugin manifest (no skills[] — auto-discovery)
│   ├── README.md                       # Plugin-level skill catalog
│   └── skills/<name>/SKILL.md          # 21 skills, each with YAML frontmatter
├── .claude-plugin/marketplace.json     # Claude marketplace listing (lists the plugin)
├── prompts/<name>.md                   # 21 universal prompts (frontmatter stripped)
├── .cursor/rules/invest-skill.mdc      # Cursor IDE auto-loading rules
├── .github/copilot-instructions.md     # GitHub Copilot auto-loading instructions
├── GEMINI.md                           # Gemini CLI auto-loading context
├── scripts/                            # Node.js: tests, validation, release automation
├── docs/                               # GitHub Pages site generator (build-site.js + assets)
├── .github/workflows/                  # CI/CD pipelines
└── *.md                                # Documentation (see Docs Map)
```

### Skill Categories (21 total)
- **Core analysis (6):** stock-eval, fundamental-analysis, technical-analysis, dcf-valuation, stock-valuation, economics-analysis
- **Financial reports (2):** financial-report-analyst, earnings-call-analysis
- **Market monitoring (4):** insider-trading, institutional-ownership, dividend-analysis, short-interest
- **Advanced research (4):** competitor-analysis, options-analysis, portfolio-review, sector-analysis
- **Meta & output (5):** research-bundle, full-report, report-generator, chart-master, result-validator

## The Dual-File Distribution Model

Each skill exists in **two synchronized forms** that must stay in sync:

1. **`plugins/us-stock-analysis/skills/<name>/SKILL.md`** — Claude Code form. Begins with YAML frontmatter (`---\ndescription: ...\n---`) followed by the framework body. Used by the Claude Code plugin as a slash command (`/us-stock-analysis:<name>`).
2. **`prompts/<name>.md`** — Universal, AI-agnostic form. Identical body with the **frontmatter removed** and no platform-specific syntax (no slash commands). This is what Cursor, Gemini CLI, GitHub Copilot, ChatGPT, and any other LLM consume.

The body content should be effectively identical between the two; only the frontmatter and platform-specific syntax differ.

### Investment Signal Block (enforced by tests)
Every `SKILL.md` and every `prompts/*.md` must end with a standardized Investment Signal Block built from UTF-8 box-drawing characters. The marker the test suite greps for is the top border line:
```
╔══════════════════════════════════════════════╗
║              INVESTMENT SIGNAL               ║
...
```
**Exception:** `report-generator` is exempt — it *renders* signal blocks into HTML rather than producing one. It is excluded from both the signal-block check and the prompts-sync check (`PROMPTS_EXCLUDED` / `SIGNAL_EXCLUDED` in `scripts/test-skills.js`).

Universal prompts must also include a "not financial advice" disclaimer (warning if missing).

## Platform Config Files

| File | Platform | Role |
|------|----------|------|
| `plugins/us-stock-analysis/.claude-plugin/plugin.json` | Claude Code | Plugin manifest |
| `.claude-plugin/marketplace.json` | Claude Code | Marketplace listing |
| `.cursor/rules/invest-skill.mdc` | Cursor IDE | Auto-loaded rules (MDC: needs `description:` + `alwaysApply:` frontmatter, references `prompts/`) |
| `.github/copilot-instructions.md` | GitHub Copilot | Auto-loaded workspace instructions (references `prompts/`) |
| `GEMINI.md` | Gemini CLI | Auto-loaded context (references `prompts/*.md`) |

## Commands

```bash
npm test                    # Unit tests — scripts/test-skills.js (294 tests currently)
npm run validate            # Validate prompt contents/format — scripts/validate-prompts.js
npm run verify              # Verify local setup is correct — scripts/setup-verify.js
npm run pre-release         # pre-release-check + validate + test (full gate)
npm run pre-release-check   # scripts/pre-release-check.js only
npm run release             # Interactive release helper — scripts/release-interactive.js
npm run release:dry-run     # Preview a release without making changes
npm run integration-tests   # scripts/integration-tests.js (add :verbose for detail)
npm run publish:cursor      # Package Cursor rules (release step)
npm run publish:gemini      # Package Gemini prompts (release step)
npm run publish:release-notes  # Generate per-platform release notes
npm run record:deploy       # Append to DEPLOYMENTS.md
npm run notify:release      # Release notification
```

Validate JSON manifests manually:
```bash
jq empty plugins/us-stock-analysis/.claude-plugin/plugin.json
jq empty .claude-plugin/marketplace.json
```

There are **no runtime dependencies** in `package.json` (no `dependencies`/`devDependencies` block). All test/validation scripts use only Node built-ins. The docs site generator (`docs/build-site.js`) uses `markdown-it`/`markdown-it-anchor`, which the Pages workflow installs on demand; building docs locally requires installing those packages first.

## Skills Registry: Auto-Discovery (important)

Skills are **auto-discovered** by scanning the `plugins/us-stock-analysis/skills/` directory for subfolders containing a `SKILL.md`. **`plugin.json` does NOT and should NOT contain a bare-name `skills[]` array** — `scripts/test-skills.js` Test 4 actively *fails* if it finds a `skills[]` with bare string names (a `skills[]` is only valid if every entry is a `./...` path string; otherwise omit it entirely).

> Pitfall: `ADDING-NEW-SKILLS.md` Step 4 still instructs you to add the skill name to a `plugin.json` `skills[]` array. That guidance is **stale/incorrect** — do not add a bare-name `skills[]`. Adding the `SKILL.md` directory is sufficient; the test asserts the field is absent.

## Adding a New Skill

Full walkthrough: **`ADDING-NEW-SKILLS.md`** (treat its `skills[]` registration step as obsolete — see above). Essential steps:

1. Create `plugins/us-stock-analysis/skills/<name>/SKILL.md` with YAML frontmatter (`description:`), ≥50 lines / ≥400 words, ending in the Investment Signal Block.
2. Create `prompts/<name>.md` — same body, **no frontmatter**, no slash commands, include the disclaimer.
3. Do **not** edit `plugin.json`'s skills list (auto-discovery). 
4. Bump the version in **both** manifests so they match (see Version Consistency).
5. Update `.cursor/rules/invest-skill.mdc`, `.github/copilot-instructions.md`, and `GEMINI.md` with a reference to the new skill.
6. Update `README.md` and the platform READMEs (`README-claude-code.md`, `README-cursor.md`, `README-gemini.md`) plus the zh-TW variants where applicable.
7. Add a `CHANGELOG.md` entry.
8. Run `npm test` (all tests must pass) and `npm run validate`.

## Version Consistency Rule

These must always carry the **same** version:
- `plugins/us-stock-analysis/.claude-plugin/plugin.json` → `.version`
- `.claude-plugin/marketplace.json` → `.metadata.version` **and** the matching entry in `.plugins[].version`

The test suite fails on a `plugins[]` version mismatch and warns on a `metadata.version` mismatch. CHANGELOG.md must mention the current version. Verify with:
```bash
jq '.version' plugins/us-stock-analysis/.claude-plugin/plugin.json
jq '.metadata.version, .plugins[0].version' .claude-plugin/marketplace.json
```

> Pitfall: `package.json` `version` (currently `1.4.0`) is **not** the source of truth and is *not* checked by the tests — it lags behind the manifests (which are at `1.6.0`). Treat the two `.claude-plugin` JSON files as authoritative.

## CI/CD & Release Process

Full reference: **`CI-CD-GUIDE.md`**; quick reference: **`RELEASE-QUICK-REFERENCE.md`**, **`DEPLOYMENTS.md`** (auto-updated deployment log).

Workflows in `.github/workflows/`:
- **`validate.yml`** — JSON syntax, required files, plugin structure, frontmatter, version consistency. Runs on push/PR to `main`/`develop`.
- **`test.yml`** ("Test Suite") — runs `npm test` (and related checks). Push/PR to `main`/`develop` + manual dispatch.
- **`auto-deploy.yml`** — triggered by a *successful* "Test Suite" run on `main`. Detects a version bump in `marketplace.json`, publishes a GitHub Release with packaged artifacts, and records the result in `DEPLOYMENTS.md`.
- **`release.yml`** — triggered by pushing a `v*` tag.
- **`deploy-pages.yml`** — builds (`docs/build-site.js`) and deploys the GitHub Pages docs site on push to `main`.
- **`install-test.yml`** — validates plugin installability (manifest/structure/prompt validity) for Claude Code and Gemini CLI without auth tokens.
- **`pr-check.yml`, `label-pr.yml`, `greetings.yml`** — PR hygiene/automation.

**Release flow:** bump version in both manifests → update `CHANGELOG.md` → commit/push to `main` → tests pass → `auto-deploy.yml` cuts the GitHub Release and records the deployment. Use `npm run pre-release` locally as the gate before pushing, and `npm run release:dry-run` to preview.

## Docs Map

| File | Purpose |
|------|---------|
| `README.md` | Main entry point, quick start, 21-framework catalog |
| `README-claude-code.md` / `README-cursor.md` / `README-gemini.md` | Per-platform install & usage guides |
| `README-zh-TW.md`, `COOKBOOK-zh-TW.md` | Traditional Chinese translations |
| `ADDING-NEW-SKILLS.md` | Contributor walkthrough for new skills (note stale `skills[]` step) |
| `CONTRIBUTING.md` | General contribution process |
| `CI-CD-GUIDE.md` / `CI-CD-FIXES-SUMMARY.md` | CI/CD architecture and fixes log |
| `DEPLOYMENTS.md` / `DEPLOYMENT-STATUS.md` | Auto-updated deployment history & status |
| `RELEASE-QUICK-REFERENCE.md` / `RELEASE_NOTES.md` | Release how-to & latest notes |
| `PLATFORM-COMPATIBILITY.md` | Cross-platform feature matrix (note: still cites "18 skills") |
| `COOKBOOK.md` | Worked examples and use cases |
| `FAQ.md` | Cross-platform Q&A |
| `CHANGELOG.md` | Version history |
| `SECURITY.md`, `LICENSE`, `TODO.md`, `HIGH-IMPACT-IMPROVEMENTS.md` | Policy, license, roadmap |
| `docs/` | GitHub Pages site source (`build-site.js`, `main.js`, `style.css`) |

## Conventions & Pitfalls

- **Markdown-only skills.** No application code implements analysis; do not add a runtime. The `scripts/` Node code is tooling only.
- **Keep the two forms in sync.** Edit a skill's `SKILL.md` and its `prompts/<name>.md` together; the bodies should match.
- **Frontmatter discipline.** `SKILL.md` must have YAML frontmatter with `description:`; `prompts/*.md` must have *none* (test fails on frontmatter in a prompt).
- **Signal block is mandatory** (UTF-8 box-drawing chars), except `report-generator`.
- **No bare-name `skills[]`** in `plugin.json` (auto-discovery; test enforces absence).
- **Stale numbers across docs.** Several docs still say "18 skills" / "270+ tests" / "288+ tests" and `package.json` says `1.4.0`. Current reality: **21 skills, 294 tests, version 1.6.0**. When you change counts, update `README.md`, `ADDING-NEW-SKILLS.md`, `PLATFORM-COMPATIBILITY.md`, `CI-CD-GUIDE.md`, and the CHANGELOG — and prefer matching the live `npm test` output over any hard-coded number.
- **`.gitignore` ignores `*.js`** except `scripts/*.js` and `docs/*.js`, and ignores `dist/`, `build/`, `node_modules/`. Release artifacts are built into `dist/` in CI.
- **Validation vs. tests.** `npm run validate` reports warnings (e.g. token-size advisories) and can exit non-zero on issues without those being hard failures of `npm test`; both run in `npm run pre-release`.
