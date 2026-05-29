# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) and other AI assistants when working with code in this repository.

## What This Is

InvestSkill is a **multi-platform AI "skills" / prompt-engineering project**, not traditional software. There is no application runtime — the "skills" are structured Markdown frameworks that guide AI assistants through US-stock investment analysis workflows (fundamental, technical, valuation, financial-report analysis, etc.).

The same skill ships to five surfaces from one source of truth:

- **Claude Code** — native plugin (`us-stock-analysis`) via the marketplace, used as slash commands
- **Cursor** — auto-loaded rules (`.cursor/rules/invest-skill.mdc`) referencing `@prompts/*.md`
- **Gemini CLI** — `GEMINI.md` + `prompts/*.md`
- **GitHub Copilot** — `.github/copilot-instructions.md`
- **Universal** — copy/paste any `prompts/*.md` into any AI (ChatGPT, Claude.ai, etc.)

## Commands

All commands are Node scripts (Node >= 18 required). Run from the repo root.

```bash
npm test                      # Unit/structure tests (currently 294 tests) — scripts/test-skills.js
npm run validate              # Validate prompts/ content & format — scripts/validate-prompts.js
npm run verify                # Verify local setup is correct — scripts/setup-verify.js
npm run pre-release-check     # Pre-release validation only — scripts/pre-release-check.js
npm run pre-release           # Full gate: pre-release-check + validate + test
npm run release               # Interactive release — scripts/release-interactive.js
npm run release:dry-run       # Preview a release without changes — scripts/release-dry-run.js
npm run integration-tests     # Cross-platform artifact integration tests
npm run integration-tests:verbose
npm run publish:cursor        # Package Cursor rules artifact
npm run publish:gemini        # Package Gemini prompts artifact
npm run publish:release-notes # Generate per-platform release notes
npm run record:deploy         # Append a deployment record to DEPLOYMENTS.md
npm run notify:release        # Release notification helper
```

Validate JSON manifests manually:
```bash
jq empty plugins/us-stock-analysis/.claude-plugin/plugin.json
jq empty .claude-plugin/marketplace.json
```

## Repository Structure

```
.claude-plugin/marketplace.json              # Claude marketplace listing (lists the plugin)
plugins/us-stock-analysis/
  ├── .claude-plugin/plugin.json             # Claude Code plugin manifest
  ├── README.md
  └── skills/<name>/SKILL.md                 # 21 skills, Claude Code form (has YAML frontmatter)
prompts/<name>.md                            # 21 universal prompts (frontmatter stripped, AI-agnostic)
scripts/                                     # All build/test/validate/release tooling (Node)
docs/                                        # GitHub Pages site (build-site.js, main.js, style.css)
.cursor/rules/invest-skill.mdc               # Cursor auto-load rules
.github/copilot-instructions.md              # GitHub Copilot auto-load
GEMINI.md                                    # Gemini CLI auto-load
.github/workflows/                           # CI/CD (test, validate, auto-deploy, release, ...)
```

Key docs: `README.md` (+ `README-claude-code.md`, `README-cursor.md`, `README-gemini.md`, `README-zh-TW.md`), `ADDING-NEW-SKILLS.md`, `CI-CD-GUIDE.md`, `PLATFORM-COMPATIBILITY.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `DEPLOYMENTS.md`, `COOKBOOK.md`, `FAQ.md`.

## Skill Distribution Model

Each skill exists in two forms that must stay in sync:

1. **`plugins/us-stock-analysis/skills/<name>/SKILL.md`** — Claude Code form. Begins with YAML frontmatter:
   ```
   ---
   description: One-line description
   ---
   ```
2. **`prompts/<name>.md`** — Universal form. Identical body, **frontmatter removed**, no platform-specific syntax (no slash commands). This is what Cursor, Gemini CLI, Copilot, and copy/paste users consume.

There are **21 skills** and **21 matching prompts** (1:1 — including `research-bundle`).

### Signal Block Requirement

Every `SKILL.md` and every `prompts/*.md` must end with a standardized Investment Signal Block drawn with UTF-8 box-drawing characters. Tests enforce its presence and format. Keep it as the last section, and verify UTF-8 encoding.

## How Skills Are Registered (important)

**Skills are auto-discovered** from `plugins/us-stock-analysis/skills/` — each subdirectory containing a `SKILL.md` becomes a skill. `plugin.json` does **NOT** contain a `skills` array. The test suite explicitly fails if `plugin.json` has a bare-name `skills[]` (it must be omitted, or use `./path` strings). So adding a skill does not require editing `plugin.json` skill lists — just create the directory + `SKILL.md`.

## Adding a New Skill

Full walkthrough in `ADDING-NEW-SKILLS.md`. Essentials:

1. Create `plugins/us-stock-analysis/skills/<name>/SKILL.md` with frontmatter + the standard sections (Overview, Framework, Inputs, Output Format, Signal Block, Example, Notes).
2. Create `prompts/<name>.md` — same body, **no frontmatter** (e.g. `tail -n +4 .../SKILL.md > prompts/<name>.md`), and no platform-specific syntax.
3. Skill is auto-discovered — no `plugin.json` skills-list edit needed.
4. Bump the version in **both** `plugins/us-stock-analysis/.claude-plugin/plugin.json` (`.version`) and `.claude-plugin/marketplace.json` (`.metadata.version` and each `plugins[].version`).
5. Update platform configs: `.cursor/rules/invest-skill.mdc`, `.github/copilot-instructions.md`, `GEMINI.md`.
6. Update `README.md` and platform README files; add a `CHANGELOG.md` entry.
7. Run `npm test` (must stay green) and `npm run validate`.

> Note: `ADDING-NEW-SKILLS.md` references "18 skills" and a `plugin.json` `skills[]` registration step in places — both are stale. Trust the live state (21 skills, auto-discovery) and the test suite.

## Version Consistency Rule

These must all carry the **same** version at all times:
- `plugins/us-stock-analysis/.claude-plugin/plugin.json` → `.version`
- `.claude-plugin/marketplace.json` → `.metadata.version` and `.plugins[].version`

Verify:
```bash
jq '.version' plugins/us-stock-analysis/.claude-plugin/plugin.json
jq '.metadata.version' .claude-plugin/marketplace.json
```

> Gotcha: `package.json` `version` is **not** the source of truth and is currently stale (1.4.0 while the plugin is 1.6.0). CI version detection reads the marketplace/plugin manifests, not `package.json`.

## CI/CD Overview

GitHub Actions in `.github/workflows/` (details in `CI-CD-GUIDE.md`):

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `validate.yml` | push/PR to `main`/`develop` | JSON syntax, required files, frontmatter, version consistency |
| `test.yml` ("Test Suite") | push/PR to `main`/`develop`, manual | Runs `npm test` (skill structure, prompt sync, signal blocks) |
| `pr-check.yml` | PR opened/synchronize/reopened | Validates changed files |
| `install-test.yml` | push to `main`/`develop` touching `plugins/**`, `prompts/**`, `GEMINI.md` | Install/usability checks |
| `auto-deploy.yml` | after **Test Suite** completes on `main` | Detects marketplace version change → validate, build artifacts, publish to all platforms, record deploy |
| `release.yml` | push of `v*` tag | Creates GitHub release with artifacts |
| `deploy-pages.yml` | push to `main`, manual | Builds & deploys the `docs/` GitHub Pages site |
| `label-pr.yml`, `greetings.yml` | PR/issue events | Labeling and greetings automation |

**Release flow:** bump version in both manifests + update `CHANGELOG.md` → commit/push to `main` → Test Suite passes → `auto-deploy.yml` detects the version bump and publishes (Claude Code marketplace release artifacts, Cursor rules package, Gemini prompts package, per-platform release notes, `DEPLOYMENTS.md` update). Cursor/Gemini registry publishing is gated on optional secrets (`CURSOR_REGISTRY_TOKEN`, `GEMINI_REGISTRY_TOKEN`/`GEMINI_REGISTRY_URL`); without them it falls back to GitHub release artifacts.

## Platform Compatibility

Full matrix in `PLATFORM-COMPATIBILITY.md`. Summary:

- **Claude Code**: native plugin, slash commands (`/us-stock-analysis:<skill> AAPL`), marketplace install (`/plugin marketplace add yennanliu/InvestSkill` → `/plugin install us-stock-analysis`).
- **Cursor**: auto-loads `.cursor/rules/invest-skill.mdc`; use `@prompts/<skill>.md ...` or natural language.
- **Gemini CLI**: auto-loads `GEMINI.md`; use `@prompts/<skill>.md ...`.
- **GitHub Copilot**: auto-loads `.github/copilot-instructions.md`; natural language (no `@prompts/` refs).
- **Universal**: paste any `prompts/*.md` into any AI tool.

Slash commands are Claude-Code-only; all other platforms use file references or natural language. `prompts/*.md` must therefore never contain slash-command or other platform-specific syntax.

## Conventions & Gotchas for AI Assistants

- **Source of truth for skill set** = the `skills/` directory, not `plugin.json`. Keep `SKILL.md` and the matching `prompts/*.md` body in sync; only the frontmatter differs.
- **Never** add a bare-name `skills[]` array to `plugin.json` — tests will fail.
- Keep the Investment Signal Block as the final section of every skill/prompt, in UTF-8 box characters.
- Version lives in the two manifests (`plugin.json` + `marketplace.json`), kept identical. `package.json` version is incidental.
- After any change to skills/prompts/manifests, run `npm test` and `npm run validate` before committing; run `npm run pre-release` before a version bump.
- Documentation counts (e.g. "18 skills" in some older docs) can lag; the authoritative count is the number of `skills/<name>/SKILL.md` files (currently 21).

## Current State

- **Version**: 1.6.0 (both manifests)
- **Skills**: 21 (auto-discovered from `skills/`)
- **Prompts**: 21 universal files in `prompts/` (1:1 with skills)
- **Tests**: 294 passing (`npm test`)
- **Node**: >= 18.0.0
