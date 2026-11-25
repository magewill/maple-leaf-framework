# Token Budget 💰 — a Spec Kit extension for token-efficient SDD

> Reduce LLM token consumption from Spec-Driven Development artifacts
> while keeping every requirement, decision, and contract intact.

`token-budget` is a [Spec Kit](https://github.com/github/spec-kit) extension
that targets the part of the SDD workflow most other token-saving tools miss:
**the artifact stack itself**.

By the time you reach `/speckit.implement`, the agent has been re-fed
`constitution.md` + `spec.md` + `plan.md` + `research.md` + `data-model.md`
+ `contracts/*` + `tasks.md` on every turn. That's typically 20–50k tokens
of input context, much of it template scaffolding, examples, restated
cross-references, and prose padding. Token Budget attacks that surface
directly, with three further leverage points borrowed from existing
ecosystem tools.

## What it does

| Layer                                | Inspired by                      | What token-budget provides              |
|--------------------------------------|----------------------------------|------------------------------------------|
| **Artifact compaction**              | (novel for SDD)                  | `/speckit.token-budget.compact` · `/speckit.token-budget.restore` |
| **Per-phase reading scope**          | Anthropic cost guide, subagent isolation | `/speckit.token-budget.scope`           |
| **Output prose suppression**         | [CavemanClaude](https://github.com/JuliusBrussee/caveman), [claude-token-efficient](https://github.com/drona23/claude-token-efficient) | `/speckit.token-budget.concise`         |
| **CLI output compression**           | [RTK](https://github.com/rtk-ai/rtk), [caveman-code](https://github.com/JuliusBrussee/caveman-code) | `scripts/bash/slim_output.sh` (defers to `rtk` if installed, or `caveman` if opted in) |
| **Visibility**                       | (novel for SDD)                  | `/speckit.token-budget.usage`           |

The four commands compose. Compact once after each major phase, scope
before each subsequent phase, leave concise mode on, and use
`slim_output.sh` (or real `rtk`) for any noisy shell hop the agent makes
during `/speckit.implement`.

## Install

```bash
# From a release zip (recommended)
specify extension add token-budget \
  --from https://github.com/tinesoft/spec-kit-token-budget/archive/refs/tags/v0.1.0.zip

# From a local directory while developing
specify extension add --dev /path/to/spec-kit-token-budget
```

Then copy the config template and tweak it:

```bash
cp .specify/extensions/token-budget/token-budget-config.template.yml \
   .specify/extensions/token-budget/token-budget-config.yml
```

Restart your AI agent so the new commands are picked up.

## Quick start

```bash
# Inside an SDD project, after running /speckit.specify and /speckit.plan:
/speckit.token-budget.usage          # see the baseline
/speckit.token-budget.compact        # compress every artifact in this feature
/speckit.token-budget.concise on     # silence agent prose during SDD steps
/speckit.token-budget.scope tasks    # before /speckit.tasks, write a focused manifest
/speckit.tasks                        # agent reads the manifest first
```

Hooks are set up so the compact and scope commands can fire
automatically after `specify`/`plan`/`tasks` and before the next phase,
if you opt in via `auto_execute_hooks: true`.

## Commands

### `/speckit.token-budget.compact [target] [--level=...] [--dry-run]`

Rewrites SDD artifacts in place to remove template scaffolding,
examples, prose padding, and redundant cross-references. **Lossless
guardrails** mean no requirement (FR-, NFR-, US-, AC-, T-prefixed IDs),
no decision, no contract, and no fenced schema is ever altered. The
original is preserved as `<artifact>.full.md`. On the first compact run,
a guard directive is also injected into the project's agent memory file
(AGENTS.md / CLAUDE.md / etc.) instructing the agent not to read
`.full.md` files — loading them would cancel the savings. The directive
is removed automatically when the last backup is deleted by `/restore`.

Three levels:
- `light` — strip template instructions and trailing examples only.
- `medium` (default) — also collapse multi-paragraph prose into bullet
  lists where conversion is information-preserving.
- `aggressive` — also drop "Rationale", "Alternatives Considered",
  "Background" — the decisions they justify are captured elsewhere in
  the artifact, and these sections are still recoverable from
  `.full.md`.

**Global memory constitution:** when `constitution` is the target,
`/compact` also processes `.specify/memory/constitution.md` (the
project-wide file loaded on every `/speckit.*` command) in addition to
the feature-scoped `constitution.md`. Compaction of
`.specify/memory/constitution.md` is **always light level only**,
regardless of `--level` — no sections are ever dropped, no prose is
rewritten. The backup `constitution.full.md` is created under
`.specify/memory/` on the first run.

Aliases: `compress`, `distill` (both translate to the same command).

Example (real numbers from a small SDD project):

```
spec.md           4,820 → 2,310 tokens   (-52.1%)   [backup: spec.full.md]
plan.md           6,140 → 3,580 tokens   (-41.7%)   [backup: plan.full.md]
research.md       3,200 → 1,180 tokens   (-63.1%)   [backup: research.full.md]
─────────────────────────────────────────────────
total            14,160 → 7,070 tokens   (-50.1%)
```

### `/speckit.token-budget.restore [target] [--dry-run]`

Undoes a previous `/compact` run. Copies `<artifact>.full.md` back over the
compacted file and deletes the backup. The restored file is byte-for-byte
identical to the original — no cleanup of the compaction marker is needed
because the marker only lived in the compacted version.

`target` follows the same surface as `/compact`:
- omit → restore every artifact in the active feature that has a `.full.md`
  backup, plus `.specify/memory/constitution.md` if a backup exists there.
- `constitution` → restore both `specs/<feature>/constitution.md` and
  `.specify/memory/constitution.md`, whichever have backups.
- `spec` | `plan` | `tasks` | `research` | … → restore only that artifact.
- a relative or absolute path → restore that specific file.
- `--dry-run` → report what would be restored, write nothing.

Example output:

```
spec.md           2,310 → 4,820 tokens   (+108.7%)   [backup deleted]
plan.md           3,580 → 6,140 tokens    (+71.5%)   [backup deleted]
──────────────────────────────────────────────────────────────────────
total             5,890 → 10,960 tokens   (+86.1%)
```

Running `/restore` on an artifact with no backup is safe — it skips with a
warning. To re-compact at a different level after restoring, run
`/compact --level=<level>`. When the last backup in the project is deleted,
`/restore` automatically removes the backup guard from the agent memory file.

### `/speckit.token-budget.scope [phase] [--include=...] [--exclude=...]`

Pre-flight context budget. Given the next phase (`plan`, `tasks`,
`implement`, `analyze`, `checklist`), writes a reading manifest under
`specs/<feature>/.token-budget/scope-<phase>.md` that names exactly
which files (and which sections of those files) the agent should load
— and which to skim or skip outright.

When `auto_execute_hooks: true`, this fires automatically before the
matching phase command.

Aliases: `brief`, `focus`.

### `/speckit.token-budget.concise on|off|status`

Toggles a project-local directive that lives between markers in the
project's agent memory file. Resolution order:

1. **Auto-detect the active spec-kit agent** from which command directory
   has files (`.claude/commands/`, `.gemini/commands/`,
   `.cursor/commands/`, `.windsurf/workflows/`, etc.).
2. **Prefer `AGENTS.md`** when present at the project root — it's the open
   cross-agent standard and is read by Claude Code, Cursor, Copilot, Gemini
   CLI, Windsurf, Aider, Zed, Warp, RooCode, and Codex. One write reaches
   every agent.
3. Otherwise, fall back to the agent-specific file (`CLAUDE.md`,
   `GEMINI.md`, `.cursor/rules/token-budget.mdc`,
   `.windsurf/rules/token-budget.md`, `.github/copilot-instructions.md`,
   `.clinerules`, `CONVENTIONS.md`, etc.).
4. If nothing exists, create `AGENTS.md` and write there.

When on, the agent suppresses narrative padding during `/speckit.*`
commands and outputs only the artifact diff plus "Done." Reversible by
`concise off` or by deleting the marker block by hand. The directive
includes an explicit override: if you ask "explain", "walk me through",
or "why", concise mode steps aside for that single reply.

Aliases: `terse`, `quiet`.

### `/speckit.token-budget.usage [--all|--feature=<name>]`

Read-only dashboard. Per-artifact token sizes, savings vs the
`.full.md` backup, projected context budgets for each upcoming phase,
and only the recommendations that are actually warranted by the
numbers. Also shows a **Global memory** block for
`.specify/memory/constitution.md` (loaded on every command) so its
recurring token cost is always visible.

Aliases: `stats`, `report`.

## The shell helper: `slim_output.sh`

A pure-bash, dependency-free CLI output compressor for use inside
command prompts and hooks. Auto-detects the right rule from argv:

```bash
./scripts/bash/slim_output.sh -- git status         # → 3-line summary
./scripts/bash/slim_output.sh -- git log -n 100     # → first 30 oneline + count of elided
./scripts/bash/slim_output.sh -- pytest -q          # → drop PASSED, keep FAILED + summary
./scripts/bash/slim_output.sh -- some-noisy-tool    # → head 30 + tail 30 + elided count
```

If the [`rtk` binary](https://github.com/rtk-ai/rtk) is on `$PATH`, the
script delegates to it for better compression. Set
`TOKEN_BUDGET_PREFER_RTK=0` to force the bash implementation.

If [`caveman`](https://github.com/JuliusBrussee/caveman-code) is on `$PATH`
and `TOKEN_BUDGET_PREFER_CAVEMAN=1`, it is used as an AI-enhanced Tier 2
(between RTK and the built-in bash rules). Disabled by default — it makes a
live LLM call and incurs API cost, but produces richer compression for complex
outputs when RTK is not installed.

Real-world data point measured during this extension's own development:
`git status` 440 bytes → 49 bytes (-89%).

## Why these specific commands

The SDD artifact pipeline has a structural property that makes plain
output compression incomplete: **earlier artifacts get re-read on every
later turn**. A 5,000-token `plan.md` charged once at generation time
charges again at `/speckit.tasks`, again at `/speckit.analyze`, again
on every turn of `/speckit.implement`. Compacting the artifact at the
source compounds across every downstream turn — the same way RTK's CLI
compression compounds because tool output sits in context and gets
re-read on every subsequent turn (~12k tokens saved per command across
a 10-turn session per RTK's own math).

The four commands target the four leverage points in order of impact:

1. **`compact`** — biggest win. Every artifact every later phase reads
   becomes ~40–50% smaller. This compounds across every turn, every
   phase.
2. **`scope`** — second biggest. Skipping a 4k-token research doc when
   the agent doesn't need it is a flat 4k-token saving every turn it
   would otherwise have been resent.
3. **`slim_output.sh`** — moderate. Only fires when the agent shells
   out, but on `git`/`pytest`/log-heavy workflows the savings (~80–90%
   per call, compounded across turns) are substantial.
4. **`concise`** — smallest, but cheapest to enable. Output prose is
   only ~5–10% of session tokens; the bigger win is faster turns.

## What token-budget does *not* do

- **It is not a generic prompt-compression tool.** It only operates on
  spec-kit artifacts and inside `/speckit.*` workflows.
- **It never paraphrases or drops content from the memory constitution.**
  `.specify/memory/constitution.md` is always compacted at light level
  only — only whitespace, decorative separators, and template filler are
  removed. No principle, constraint, or architectural decision is ever
  altered.
- **It does not paraphrase or summarize.** Compaction is removal of
  template scaffolding and prose padding only. Lossless on every
  information-bearing line.
- **It does not replace `rtk` or Caveman.** It reuses their ideas at
  the spec-kit layer. Run them too if you want their effects elsewhere
  in your workflow — `slim_output.sh` will defer to `rtk` if installed.

## Configuration

See [`token-budget-config.template.yml`](token-budget-config.template.yml)
for every option, with comments. The big knobs are:

- `compact.level` — `light`, `medium`, or `aggressive`
- `compact.preserve_sections` and `compact.preserve_id_patterns` — your
  guardrails
- `compact.guard_memory_file` — inject/remove the backup guard directive
  in the agent memory file (default `true`; set `false` to opt out)
- `scope.max_total_tokens` — soft cap for the per-phase manifest
- `scope.phase_inputs` — override which artifacts each phase needs
- `concise.memory_files` — agent memory file priority order
- `concise.prefer_agent` — tiebreaker when multiple agents are detected
- `slim.rules` — per-pattern compression behavior
- `slim.prefer_caveman` — document the `TOKEN_BUDGET_PREFER_CAVEMAN=1` env var to enable caveman-code as Tier 2 in `slim_output.sh`

Environment overrides work too: any
`SPECKIT_TOKEN_BUDGET_<DOTTED_KEY>=<value>` will override the config
file.

## Compatibility

- **spec-kit** `>= 0.1.0`
- **Agents** — every agent spec-kit's `INTEGRATION_REGISTRY` supports.
  Slash commands (`compact`, `scope`, `usage`) and hooks are translated
  to the right format and directory automatically by spec-kit's
  `CommandRegistrar`:

  | Agent | Commands directory | Memory file (concise) |
  |---|---|---|
  | Claude Code | `.claude/commands/` | `AGENTS.md` or `CLAUDE.md` |
  | Cursor | `.cursor/commands/` or `.cursor/skills/` | `AGENTS.md` or `.cursor/rules/token-budget.mdc` |
  | GitHub Copilot | `.github/prompts/` | `AGENTS.md` or `.github/copilot-instructions.md` |
  | Gemini CLI | `.gemini/commands/` (TOML) | `AGENTS.md` or `GEMINI.md` |
  | Windsurf | `.windsurf/workflows/` | `AGENTS.md` or `.windsurf/rules/token-budget.md` |
  | Cline | (per Cline conventions) | `AGENTS.md` or `.clinerules` |
  | Roo Code | (per Roo conventions) | `AGENTS.md` or `.roo/rules/token-budget.md` |
  | Kilo Code | (per Kilo conventions) | `AGENTS.md` or `.kilocode/rules.md` |
  | Aider | (per Aider conventions) | `AGENTS.md` or `CONVENTIONS.md` |
  | Codex CLI | `.agents/skills/speckit-*/SKILL.md` | `AGENTS.md` |
  | Tabnine | `.tabnine/agent/commands/` (TOML) | `AGENTS.md` or `.tabnine/AGENTS.md` |
  | Pi Coding Agent | `.pi/prompts/` | `AGENTS.md` or `.pi/AGENTS.md` |

  When `AGENTS.md` exists at the project root, `concise` writes the
  directive there by default — it's the open cross-agent standard and a
  single write reaches every agent. To force a specific file, set
  `concise.prefer_agent` in `token-budget-config.yml` or pass
  `--file=<path>` to the command.

- **Operating systems** — macOS, Linux, WSL, native Windows. Both bash
  and PowerShell variants of every helper script are bundled.
- **Optional** — `tiktoken` (Python) for exact token counts; chars/4
  fallback otherwise. `rtk` binary on `$PATH` for stronger CLI
  compression in `slim_output.sh`; built-in rules otherwise. `caveman`
  binary + `TOKEN_BUDGET_PREFER_CAVEMAN=1` enables AI-enhanced Tier 2 in
  `slim_output.sh` when RTK is not installed.

## Inspirations

This extension stands on shoulders. Read these projects directly if you
want their effects outside the SDD workflow:

- **[RTK (Rust Token Killer)](https://github.com/rtk-ai/rtk)** — the
  model for `slim_output.sh`. Use it directly for system-wide CLI
  compression.
- **[CavemanClaude](https://github.com/JuliusBrussee/caveman)** — the
  model for `concise`. Goes much further (compresses memory files,
  ships `caveman-shrink` MCP proxy, has its own subagents).
- **[caveman-code](https://github.com/JuliusBrussee/caveman-code)** — the
  model for the Tier 2 fallback in `slim_output.sh`. Its `caveman -p` mode
  provides AI-enhanced CLI output compression; its four-layer compression
  philosophy (caveman mode, tool budgets, read dedup, RTK) inspired the
  priority chain in `slim_output.sh`.
- **[claude-token-efficient](https://github.com/drona23/claude-token-efficient)** —
  inspiration for the directive style of `concise`.
- **Anthropic's [Manage costs effectively](https://code.claude.com/docs/en/costs)** —
  the official guide that motivates `scope` and the per-phase reading
  manifests.

## License

Copyright (c) 2026-present Tine Kondo. Licensed under the [MIT](LICENSE) License.
