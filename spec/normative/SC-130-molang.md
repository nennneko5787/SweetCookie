# SC-130 — Molang binding contract

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

Molang is Bedrock's expression language: float-typed, evaluated per render frame on the client and
in a few server contexts. 315 `query.*` and 61 `math.*` functions. It is on the hot path of every
frame of every custom entity, so its design decides whether the client is playable.

---

## 1. Decisions already taken

**Base implementation: `team.unnamed:mocha` (MIT).** Java, on Maven Central, with both an
interpreter and a compiler to JVM bytecode. The alternatives considered were `hollow-cube/molang`,
`Draylar/arcane` and `bedrockk/MoLang`; mocha wins on licence, availability and the bytecode path,
which matters because the alternative is tree-walking an AST per bone per frame. ADR-0008.

**Expressions are parsed at ingest, never stored as text** (SC-110 §7). Parse errors surface at load
with provenance instead of mid-frame with none; constants fold once; and the set of queries a pack
references is known statically, which is what allows pre-binding.

**Arithmetic is `float`** (SC-000 §7). Folding in `double` and narrowing later changes which branch a
pack takes.

**A failed evaluation yields 0 and does not throw** (constitution rule 1). This matches Bedrock.

## 2. Grammar

Sections to write:

- 2.1 Lexical: case-insensitivity except in string literals; the `q.`/`v.`/`t.`/`c.` short forms.
- 2.2 Operators: `! && || < <= > >= == != * / + -`, ternary `a ? b : c`, **binary-if** `a ? b`
  (yields 0 when false), null-coalescing `??`, member access `->`, statement separator `;`,
  `return`.
- 2.3 Complex expressions: multi-statement bodies must end in `return`; assignment to `v.` and `t.`.
- 2.4 Newer constructs: `loop(count, {…})`, `for_each(v.x, array.y, {…})`, `break`, `continue`.
- 2.5 Arrays and structs; `->` dereference chains such as
  `q.get_nearby_entities(...)->q.health`.
- 2.6 What mocha does **not** express, and how each gap is closed.

`TODO(SC-130)`: enumerate the mocha gaps concretely. This is a prerequisite for the section being
complete and needs an experiment, not a guess.

## 3. Scopes

| Scope | Lifetime | Writable |
|---|---|---|
| `query.` / `q.` | read-only engine state | no |
| `variable.` / `v.` | **per entity**, persists across frames; shared between animations, render controllers and particles | yes |
| `temp.` / `t.` | one expression evaluation | yes |
| `context.` / `c.` | supplied by the caller | no |
| `math.` | pure functions | — |
| `geometry.` / `material.` / `texture.` | alias lookup, render controllers only | no |
| `array.` | declared arrays, indexed by any float expression (floor, wrap) | no |

`variable.*` living per entity and being shared across three subsystems is the single most important
semantic fact here: it is how packs communicate between a render controller and a particle emitter,
and getting the sharing wrong breaks packs in ways that look like rendering bugs.

## 4. Evaluation contexts

A capability matrix: which of the 315 queries are legal in which context, and what each context
supplies. Contexts: render controllers, client-entity `scripts` (`pre_animation`, `initialize`,
`animate`, `scale`, `variables`), animations (keyframes, `anim_time_update`, `blend_weight`,
`loop_delay`, `start_delay`, `pre_effect_script`), animation controllers (transitions,
`blend_transition`, `on_entry`, `on_exit`), particles (nearly every field, plus curve inputs),
attachables, block `permutations[].condition`, block `geometry.bone_visibility`, item and entity
display fields, fog, client biomes, and `MolangVariableMap` from the Script API.

`TODO(SC-130)`: build the matrix from `bedrock-samples` `metadata/molang_modules/` rather than by
hand, and generate it into `docs/`.

### 4.1 Client versus server

Almost all Molang is **client-side and per frame**. The server evaluates it only for block
permutation conditions, which use a restricted query set (block state and property queries plus pure
maths, no entity context).

The client renders at an unbounded frame rate; Bedrock's semantics are per frame, so ours must be
too. Evaluating per tick instead is a fidelity divergence and must be recorded as one, not adopted
silently as an optimisation.

## 5. Binding the queries

315 queries, each backed by Java-side state. Sections to write: the `MolangQuery` SPI, int-interned
query identifiers, arity and return-type metadata generated from upstream, the per-context binding
table, and the policy for queries with no Java equivalent (`q.is_in_village`, `q.is_using_vr`,
`q.armor_texture_slot`, …) — each gets a coverage entry with status `wontfix` or `unsupported` and a
documented constant.

## 6. Performance

Budget, normative: **under 1 ms per frame with 50 visible custom entities**, measured by a regression
test from the milestone the render stack lands.

Techniques, in the order they matter:

1. constant folding at parse time;
2. source interning so identical expressions across a pack compile once;
3. compilation to bytecode for anything evaluated per frame;
4. integer query identifiers rather than string lookup;
5. per-frame memoisation of queries that cannot change within a frame;
6. a bitset of client-relevant boolean queries so an entity's whole flag set syncs as one word.

`TODO(SC-130)`: mocha's metaspace behaviour under a few thousand compiled expressions is unmeasured.
If class-loading cost is significant, the fallback is compiling only expressions observed to be hot.

## 7. Testing contract

- Every one of the 315 queries has a unit test or an explicit `unsupported`/`wontfix` coverage entry.
- Expression golden tests: a corpus of expressions with expected `float` results, including the
  awkward ones — binary-if, `??` chaining, `->` on a null subject, division by zero, `loop` with a
  non-integral count.
- A performance regression test asserting the §6 budget.
- Differential tests against Bedrock output where a value can be observed in game.
