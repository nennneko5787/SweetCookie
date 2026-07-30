# Identifier schemes

Three separate identifier spaces exist in this project and confusing them is the most common source
of design error. This document is normative.

| Space | Example | Who allocates it |
|---|---|---|
| **Specification IDs** | `SC-160`, `SC-160#minecraft:behavior.melee_attack` | this document |
| **Diagnostic codes** | `SCE-1042` | SC-240 |
| **Content identifiers** | `sweetcookie:wizardry.magic_wand` | derived, SC-120 |

A fourth space — Bedrock's own identifiers (`minecraft:behavior.melee_attack`, `wizardry:magic_wand`)
— is not ours to allocate. We only consume it.

---

## 1. Specification IDs

### Format

```
SC-<nnn>
SC-<nnn>#<feature-id>
```

`<nnn>` is a three-digit number. `<feature-id>` is a *Bedrock* identifier, verbatim, including its
namespace: `minecraft:behavior.melee_attack`, `minecraft:block/geometry`, `query.anim_time`.

### Number space

| Range | Domain |
|---|---|
| 000–099 | Cross-cutting: conventions, glossary-adjacent definitions |
| 100–119 | Ingestion: packaging, manifests, the intermediate representation |
| 120–129 | Registration, identifiers, persistence |
| 130–149 | Expression and predicate languages: Molang, filters |
| 150–179 | Content domains: blocks, entities, items |
| 180–189 | Resource pack and rendering |
| 190–199 | Data domains: loot, recipes, trading, spawn rules |
| 200–209 | Scripting |
| 210–219 | Interoperability: Geyser, other mods |
| 220–239 | Build and platform: version abstraction, platform services |
| 240–269 | Operational: diagnostics, performance, security |
| **270–279** | **Networking and the wire protocol** |
| 280–289 | Configuration and user interface |
| 290–999 | Unassigned |

### Feature identifiers are unique per document, not globally

**Bedrock reuses component names across domains, with different semantics.**
`minecraft:collision_box` is both an entity component and a block component;
`minecraft:loot`, `minecraft:transformation`, `minecraft:display_name`,
`minecraft:custom_components`, `minecraft:projectile` and `minecraft:tags` are similarly overloaded.

So the unique key is the **pair** `(specification document, feature identifier)`, which is exactly
what the suffixed form already writes:

```
SC-150#minecraft:collision_box     the block component
SC-160#minecraft:collision_box     the entity component
```

Two coverage entries may share a feature identifier only if their shards declare different `spec`
values. `specValidate` enforces this, and it is how the mistake was found: the original assumption
here was global uniqueness, and the check rejected the seeded ledger until it was corrected.

A bare `SC-nnn#<feature>` is therefore always unambiguous. A bare `<feature>` never is, and
**MUST NOT** appear in an annotation, a conformance case or a normative sentence.

### Rules

- **A specification ID is immutable once merged.** A document may be rewritten, split or marked
  obsolete; its number is never reused for a different subject and never renumbered.
- Splitting a document allocates new numbers for the new parts and leaves the original as a stub
  pointing at them.
- A document that becomes obsolete keeps its file, gains `Status: obsolete` and a pointer to its
  replacement. It is not deleted, because code and coverage entries reference it.
- The `#<feature-id>` suffix is used in `@SpecImpl` annotations and coverage entries. It is *not*
  used for file names; every `SC-<nnn>` maps to exactly one file
  `spec/normative/SC-<nnn>-<slug>.md`.

### Referencing from code

```java
@SpecImpl("SC-160#minecraft:behavior.melee_attack")
public final class MeleeAttackGoal extends BedrockGoal { ... }
```

```java
@ProvesSpec("SC-160#minecraft:behavior.melee_attack")
void meleeAttack_stopsWhenTargetDies() { ... }
```

For a document-level implementation with no single feature ID, the bare form `@SpecImpl("SC-110")`
is permitted.

CI (`specImplCheck`) verifies four directions:

1. every `@SpecImpl` / `@ProvesSpec` value names a specification document that exists;
2. every `#<feature-id>` suffix names a coverage entry that exists;
3. every coverage entry whose status is not `stub` names an `impl` class that exists and carries the
   matching annotation;
4. no `@SpecImpl` exists whose feature ID has no coverage entry (an orphan implementation).

---

## 2. Diagnostic codes

```
SCE-<nnnn>
```

| Range | Class |
|---|---|
| 1000–1999 | Parse: malformed archive, bad JSON, unreadable `format_version` |
| 2000–2999 | Semantic: unknown component / goal / query / filter, unresolvable reference, cap exceeded |
| 3000–3999 | Runtime: evaluation failure, script error, budget exhausted |
| 4000–4999 | Registration and persistence: schema drift, placeholder registration, ledger conflict |
| 5000–5999 | Networking: handshake failure, sideband desync, pack mismatch |
| 6000–6999 | Interoperability: Geyser bridge unavailable, API mismatch |

Codes are allocated in `spec/normative/SC-240-diagnostics.md`, are **never reused**, and are never
renumbered — users search the internet for them and the results must stay correct. A retired code
keeps its entry marked `retired` with the version in which it stopped being emitted.

---

## 3. Content identifiers

Fully specified in SC-120; summarised here because it is the rule most often needed and most often
guessed at.

```
sweetcookie:<sanitised bedrock namespace>.<sanitised bedrock path>
```

| Bedrock | Java |
|---|---|
| `wizardry:magic_wand` | `sweetcookie:wizardry.magic_wand` |
| `my_pack:fire/ember_block` | `sweetcookie:my_pack.fire_ember_block` |
| `Cool-Pack:Thing` | `sweetcookie:cool_pack.thing` |

Sanitisation: lowercase, then map every character outside `[a-z0-9_.-]` to `_`. The namespace and
path are joined with a single `.`; any `.` already present in either part is preserved, which is
harmless because the *pair* is what must round-trip, and the mapping is recorded in the ledger
rather than reversed by parsing.

Collisions — two distinct Bedrock identifiers sanitising to the same string — are resolved by
appending `_h` plus the first eight hex digits of `sha1(original)` to whichever identifier was seen
**later in pack load order**, and recording it in the ledger. This is deterministic given the same
set of packs, which is the property that matters.

The transformation exists in exactly one place in the codebase. Reimplementing it anywhere else is a
review-blocking defect.

### The namespace is always `sweetcookie`

Not the add-on's namespace. Using the add-on's namespace would let two add-ons collide in the Java
registry, would make it impossible to tell our content from another mod's, and would break the
guarantee in rule 4 of the constitution that the mapping is a pure function we control.

---

## 4. Network identifiers

The sideband channel (SC-270) interns content identifiers into a **session-scoped** integer space
negotiated at handshake. That space is unrelated to, and must never be confused with, Minecraft's
network registry IDs. It is not persisted, not stable across sessions, and carries no meaning
outside a single connection.
