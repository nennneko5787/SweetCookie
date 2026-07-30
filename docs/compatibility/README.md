# Compatibility tables

**GENERATED — do not edit.** Produced by `./gradlew specReport` from `spec/coverage/**`.
CI fails if a commit leaves these stale.

Nothing is here yet: the generator lands with the `build-logic` spec plugin. Until then, read the
ledger directly at [`spec/coverage/`](../../spec/coverage/) — it is YAML and it is meant to be
readable.

## What will be generated

| File | Contents |
|---|---|
| `summary.md` | headline table: per-domain counts and percentages by status |
| `entity-components.md` | 120 entries |
| `entity-goals.md` | 171 entries |
| `block-components.md` | 54 entries |
| `item-components.md` | 44 entries |
| `filters.md` | 106 entries |
| `molang-queries.md` | 315 entries |
| … | one page per coverage shard |

Each row carries the status, the implementing class, the fidelity note and a link to the conformance
case. A percentage with no fidelity notes behind it would be marketing; the notes are the point.

## How to read a status

| | Meaning |
|---|---|
| `implemented` | indistinguishable from Bedrock across everything the conformance corpus covers |
| `partial` | works for the common case; the fidelity note says exactly where it does not |
| `stub` | recognised and parsed, no runtime effect, emits a diagnostic |
| `unsupported` | not implemented yet, deliberately |
| `wontfix` | will not be implemented; the note says why |

Definitions are normative in [SC-000 §3](../../spec/normative/SC-000-conventions.md).
