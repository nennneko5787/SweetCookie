# Lepus specification

This directory is **normative**. Where the code and this directory disagree, this directory is
right and the code is a bug — or the specification is amended first, deliberately, in its own
commit.

Nothing outside `spec/` is normative. `docs/` is generated or explanatory. `README.md` is marketing.
Javadoc is a convenience.

## Why a specification at all

Lepus implements someone else's format. Mojang's add-on documentation is incomplete,
occasionally wrong, versioned per-file with `format_version` values spanning `1.8.0` to `1.26.30`
*inside a single pack*, and changes without notice. The interesting question about this project is
never "does it work" but **"which of the ~2,500 Bedrock feature identifiers work, how faithfully,
and how do we know?"**

Prose cannot answer that. A ledger can. That is what `coverage/` is, and it is the load-bearing
artifact here — the normative documents exist to give its entries meaning.

## Layout

| Path | What it is | Normative? |
|---|---|---|
| `constitution.md` | The 12 rules that override everything else | **yes** |
| `process.md` | How a change flows: upstream diff → feature dir → coverage → PR | **yes** |
| `ids.md` | The identifier scheme, prefix table, immutability rules | **yes** |
| `glossary.md` | Bedrock ↔ Java vocabulary. Read this before your first PR. | no |
| `normative/SC-*.md` | The substantive specifications | **yes** |
| `normative/ja/*.md` | Japanese translations | **no** — informative only |
| `schemas/` | JSON Schema. The machine-checkable half of several SC- docs. | **yes** |
| `coverage/` | The feature ledger. One entry per Bedrock feature ID. | **yes** |
| `conformance/` | Executable corpus: tiny add-ons plus expected outcomes | **yes** |
| `adr/` | Architecture decision records, append-only | **yes**, historically |
| `features/` | Transient work units (spec → plan → tasks). Japanese allowed. | no |
| `upstream/` | Pinned upstream metadata hashes and the fetch policy | **yes** |

## The normative documents

| ID | Title | State |
|---|---|---|
| [SC-000](normative/SC-000-conventions.md) | Conventions | complete |
| [SC-100](normative/SC-100-packaging.md) | Packaging and manifests | complete |
| [SC-110](normative/SC-110-ir.md) | Intermediate representation | complete |
| [SC-120](normative/SC-120-registration.md) | Registration, storage and identifier persistence | complete |
| [SC-130](normative/SC-130-molang.md) | Molang binding contract | outline |
| [SC-140](normative/SC-140-filters.md) | Filters | outline |
| [SC-150](normative/SC-150-blocks.md) | Blocks and permutation resolution | outline |
| [SC-160](normative/SC-160-entities.md) | Entities: components, groups, events, goals | outline |
| [SC-170](normative/SC-170-items.md) | Items | outline |
| [SC-180](normative/SC-180-render.md) | Resource pack and rendering | outline |
| [SC-190](normative/SC-190-loot-recipes-trading.md) | Loot, recipes, trading | outline |
| [SC-200](normative/SC-200-scripting.md) | Script API | outline |
| [SC-210](normative/SC-210-geyser-mirror.md) | Geyser mirror contract | outline |
| [SC-220](normative/SC-220-versioning.md) | Version abstraction rules | complete |
| [SC-230](normative/SC-230-platform-services.md) | Platform services | complete |
| [SC-240](normative/SC-240-diagnostics.md) | Diagnostics | outline |
| [SC-250](normative/SC-250-performance.md) | Performance budgets | outline |
| [SC-260](normative/SC-260-security.md) | Security | outline |
| [SC-270](normative/SC-270-wire-protocol.md) | Version-independent wire protocol | complete |
| [SC-280](normative/SC-280-config-ui.md) | Configuration and user interface | outline |

"outline" means the section headings and the decisions already taken are recorded, but the
document is not yet dense enough to implement from. Filling one in is a normal unit of work; see
`process.md`.

## Reading order

New to the project: `glossary.md` → `constitution.md` → `SC-000` → `SC-110`.

About to implement a feature: `SC-110` (the IR you will produce or consume) → the SC- doc for your
domain → `SC-270` if your feature is visible over the network → `SC-120` if it is stored on disk →
its `coverage/` entry.

`SC-120` and `SC-270` are the two documents that constrain all the others: together they are why no
Bedrock feature gets a Java registry entry and why nothing custom appears on the wire. Read both
before proposing anything that registers or transmits content.

## Checking it

```
./gradlew specAll
```

runs every specification check: schema validation, upstream drift, implementation-link integrity,
conformance-link integrity, report regeneration and the language lint. CI runs the same task. A
green `specAll` means the ledger is not lying; it says nothing about whether the mod is any good.
