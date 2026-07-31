# SC-000 — Conventions

**Status:** complete · **Since:** 0.1.0 · **Supersedes:** —

How to read every other document in `spec/normative/`. Normative.

---

## 1. Requirement levels

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**, **SHOULD**,
**SHOULD NOT**, **RECOMMENDED**, **MAY** and **OPTIONAL** are to be interpreted as described in
[RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) and [RFC 8174](https://www.rfc-editor.org/rfc/rfc8174),
and only when they appear in **bold uppercase**. The same words in ordinary prose carry their
ordinary English meaning and impose no requirement.

A requirement with no stated subject applies to the implementation.

## 2. Document structure

Every `SC-<nnn>-<slug>.md` begins with:

```markdown
# SC-<nnn> — <Title>

**Status:** complete | outline | obsolete · **Since:** <version> · **Supersedes:** <SC-nnn or —>
```

| Status | Meaning |
|---|---|
| `complete` | dense enough to implement from without guessing |
| `outline` | decisions taken so far are recorded; gaps are explicitly marked `TODO(SC-nnn)` |
| `obsolete` | superseded; the file remains, because code and coverage entries reference its ID |

Unresolved questions are marked `TODO(SC-nnn): <question>` inline. A `complete` document **MUST NOT**
contain a `TODO` that affects observable behaviour.

## 3. Fidelity vocabulary

These five words describe the relationship between SweetCookie's behaviour and Bedrock's. They are
the same five values a coverage entry's `status` may take, and they mean the same thing in prose.

| Term | Definition |
|---|---|
| **implemented** | Observably indistinguishable from Bedrock for all inputs the conformance corpus covers. Divergences outside that corpus may exist and are bugs. |
| **partial** | Correct for the common case. Every known divergence is enumerated in the coverage entry's `fields` and described in `fidelity`. |
| **stub** | Parsed into the IR and recognised, but has no runtime effect. Emits a diagnostic. |
| **unsupported** | Not implemented yet, deliberately. `fidelity` states what it would take. |
| **wontfix** | Will not be implemented. `fidelity` states why — usually that no Java analogue exists and emulation would cost more than the feature is worth. |

"Faithful" and "correct" are **not** defined terms and **SHOULD NOT** be used in a normative
sentence. Say which of the five applies.

### 3.1 `implemented` is written by a human and verified by the build

ADR-0011. An entry **MUST NOT** claim `implemented` unless every one of these holds, and the build
checks all five:

| Requirement | Checked by |
|---|---|
| names an `impl` class carrying a matching `@SpecImpl` | `specLinks` |
| names at least one conformance case | `specValidate` |
| every named case ran and passed | `specConformance` |
| carries **no** `fidelity` note | `specValidate` |
| every entry in `fields`, if present, is `ok` | `specValidate` |

The last two follow from the definition rather than adding to it. A `fidelity` note states an
observable difference from Bedrock, so an entry claiming there is none cannot carry one; a `fields`
map containing `missing` or `partial` is an enumerated divergence in table form, and says `partial`
whatever the `status` line says.

`partial` is **never** promoted to `implemented` by any tool. It is the terminal state for work whose
divergences are known and stated, which is most work.

## 4. Naming Bedrock things

Bedrock identifiers are quoted verbatim, in backticks, with their namespace:
`minecraft:behavior.melee_attack`, not "the melee attack goal".

`format_version` values are quoted as they appear in files: `1.8.0`, `1.21.0`, `1.26.30`. Note that
Bedrock's *creator-facing* version string remains `1.26.x` even though the game is marketed as
`26.x`; specifications use the creator-facing form, because that is what appears inside packs.

When a Bedrock term collides with a Java term, use the disambiguated form from `../glossary.md`.
"Component" always means the Bedrock sense; the Java sense is "data component".

## 5. Java-side naming

- Package root: `net.nennneko5787.sweetcookie`
- Mod id and resource namespace: `sweetcookie`
- `core/` packages: `net.nennneko5787.sweetcookie.core.<format|molang|script|testkit|api>`
- Minecraft-dependent: `net.nennneko5787.sweetcookie.<runtime|client|platform|compat>`

Class names in a specification are written fully qualified on first use in a document and by simple
name afterwards.

## 6. Canonical JSON

Several parts of this project hash or compare JSON: the ledger's state-schema hash (SC-120), the
upstream lock file (`spec/upstream/`), and conformance goldens. Wherever a specification says
"canonical JSON", it means:

1. UTF-8, no byte-order mark.
2. Object keys sorted by Unicode code point, ascending.
3. No insignificant whitespace: no space after `:` or `,`, no indentation, no trailing newline.
4. Numbers serialised as the shortest representation that round-trips to the same IEEE-754 double;
   integral values within ±2^53 emitted without a decimal point or exponent.
5. Strings escaped minimally: only `"`, `\`, and code points below U+0020, the last as `\u00XX`
   except for the standard short escapes `\b \f \n \r \t`.
6. No duplicate keys. Duplicates in input are a parse error (`SCE-1xxx`), not a last-wins.

Conformance goldens are canonical JSON so that a diff is meaningful.

## 7. Numbers

Molang is float-typed and so is much of the Bedrock format. Unless a document says otherwise:

- Bedrock numeric fields parse into **`float`** (IEEE-754 binary32) in the IR, matching the engine.
- Molang expressions **yield** `float`, and constant folding at parse time is done in `float`.
  Widening "for accuracy" changes which branch a pack takes, so nothing here does it deliberately.
- **Exception, stated:** arithmetic *inside* one Molang expression is evaluated in `double`, because
  the expression compiler is `double` end to end and replacing it would cost SC-250's frame budget.
  ADR-0012 records the decision and SC-130 §2.6 the measurement. The observable effect is confined
  to comparisons and discontinuous functions applied within about 2⁻²⁴ of a boundary.
- Positions, rotations and pivots are `float`. World coordinates on the Java side are `double`;
  conversion happens at the boundary and is specified per site.
- An integral Bedrock field that Java models as an integer is parsed as `float` and then narrowed
  with truncation toward zero, matching Bedrock, and clamped to the Java type's range with a
  diagnostic if it does not fit.

## 8. Time

- **Tick** means a Minecraft server tick, 1/20 s nominal.
- **Frame** means a client render frame, of unbounded rate.
- Bedrock evaluates resource-pack Molang **per frame**, not per tick. A specification that says
  "per frame" means it, and an implementation that evaluates per tick instead has a fidelity
  divergence that must be recorded.
- Bedrock durations in seconds are converted to ticks by `round(seconds * 20)` unless stated
  otherwise. Where Bedrock itself uses ticks, no conversion occurs.

## 9. Ordering and determinism

Anything that affects registration, identifier derivation or the on-disk ledger **MUST** be
deterministic given the same set of pack files, independent of filesystem enumeration order, hash
map iteration order and locale.

Where a specification depends on "pack load order", that order is defined in SC-100 and is itself
deterministic.

String case folding uses `Locale.ROOT`. Never the default locale.

## 10. Error handling

Constitution rule 1 governs: unknown input degrades, it does not throw. Concretely, within `core/`:

- A parser encountering an unknown key **MUST** record it in the IR's `unknown` bag and continue.
- A parser encountering a *malformed* value for a known key **MUST** emit an `SCE-1xxx` diagnostic,
  omit that field, and continue with the rest of the object.
- A parser encountering a structurally broken file **MUST** emit a diagnostic, skip that file, and
  continue with the rest of the pack.
- Only an unreadable or unsafe archive aborts a whole pack, and even then the remaining packs load.

`core/` **MUST NOT** log. It returns diagnostics as values; the runtime decides how to surface them.
This keeps `core/` testable and free of a logging dependency.

## 11. Referencing

Cross-references use the bare ID: "see SC-110 §4". Feature-level references use the suffixed form:
`SC-160#minecraft:behavior.melee_attack`. Both are checked by CI.

External references cite a URL. Mojang documentation URLs are unstable, so a citation **SHOULD**
also name the artifact and version it was read from — e.g. "`bedrock-samples` at
`metadata/json_schemas/server/item/1.26.30/`".

## 12. Units in tables

Every quantity in a specification table carries its unit. A bare number in a normative table is a
defect.
