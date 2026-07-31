# SC-150 — Blocks and permutation resolution

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

Bedrock blocks: 32 components, 7 trigger components, 15 event responses, named states with at most
16 values each, engine-provided traits, and an ordered permutation list resolved by Molang.

Storage and slot binding are SC-120 §6 and are **not** repeated here. This document covers the
translation of behaviour and appearance.

---

## 1. Decisions already taken

**A Bedrock block occupies one pool slot with a single opaque index property** (SC-120 §6.1). It does
**not** get one Java `Property<?>` per Bedrock state. That choice trades readable debug output for
runtime attach/detach, and the trade was made deliberately.

**Permutations are pre-resolved per state index at bind time**, not evaluated per block access.
A block permutation condition may only reference block state and property queries plus pure maths
(no entity context, no world context), so every state index has a fixed, computable component set.
Evaluating Molang inside `getShape` would be indefensible.

**Later permutations override earlier ones**, per component key, which is Bedrock's rule.

**Behaviour is read through a live reference** (constitution rule 7): `BedrockBlock` overrides every
behavioural method to consult the current IR, and `BlockBehaviour.Properties` is built from
functions closing over that reference, so a reload changes behaviour without re-registration.

## 2. States, traits and the index

### 2.1 `description.states`

Two spellings, both normalising to an ordered value list:

```jsonc
{ "sc:kind":  ["short", "tall"] }                 // explicit
{ "sc:level": { "values": { "min": 0, "max": 3 } } }  // range, integers only
```

Values may be booleans, integers or strings. They are held as **strings** whatever they were
written as, because a state's values are only ever compared for equality and one type removes a
three-way branch from every site that touches one; the original JSON type is kept alongside,
because the Molang binding needs it (§3).

**At most 16 values.** A state declaring more is truncated to 16 with `SCE-1035` rather than
refused: truncating keeps the block usable and keeps the index stable for the values that fit,
where refusing loses the block entirely.

The **first declared value is the default** — for a freshly placed block, and for any lookup naming
a value the state does not permit. Refusing the latter would mean one typo in one permutation cost
the whole block, and Bedrock falls back too.

### 2.2 `description.traits`

Engine-provided state groups: `minecraft:placement_direction` (`cardinal_direction`,
`facing_direction`, `y_rotation_offset`) and `minecraft:placement_position` (`block_face`,
`vertical_half`). A trait is a state the engine fills in rather than the pack.

Traits expand into ordinary states before the index is built, and are **appended after** the pack's
own declared states. Appending rather than interleaving is what stops enabling a trait from shifting
the digits of the states already encoded in placed blocks.

Their value orders are fixed by the engine and are as load-bearing as a declared state's:

| State | Values, in order |
|---|---|
| `minecraft:cardinal_direction` | `south`, `west`, `north`, `east` |
| `minecraft:facing_direction`, `minecraft:block_face` | `down`, `up`, `south`, `west`, `north`, `east` |
| `minecraft:vertical_half` | `bottom`, `top` |
| `minecraft:y_rotation_offset` | `0`, `90`, `180`, `270` |

### 2.3 The index

State values are digits of a **mixed-radix number, least significant first in declaration order**.
A block with `sc:lit` (2 values) then `sc:level` (4 values) encodes as
`lit + 2 × level`, giving 8 indices.

**Declaration order is normative and is part of the on-disk format.** The index appears in chunk
storage and in the block ledger, so reordering a pack's `description.states` re-maps every block
already placed in every world. That is why SC-120 makes a schema change a *detected* event with a
re-mapping step rather than something that happens quietly.

`TODO(SC-150)`: 2.4, behaviour when the product exceeds the largest size class. Needs SC-120 §6's
pool to exist first.

## 3. Permutation resolution

The normative algorithm. Sketch:

```
for each state index i in 0 .. N-1:
    components := copy(block.components)
    for each permutation p in declaration order:
        if evaluate(p.condition, stateOf(i)) is truthy:
            components := merge(components, p.components)   // per key, p wins
    resolved[i] := components
```

**Truthiness is Molang's: any non-zero value, including negatives and fractions.** A condition
written `q.block_state('level') - 1` means "level is not 1", and packs write it that way.

**Merging is per top-level component key**, later permutation wins. A permutation setting
`minecraft:collision_box` replaces the base one entirely rather than patching into it — the two are
alternative shapes, not a shape and a delta.

`TODO(SC-150)`: whether Bedrock ever merges *deeper* than the top level for any component. Nothing
observed so far does, and this is the shape that matches every published pack read to date, but it
is inference from content rather than from the engine.

### 3.1 What a condition may read

**Block state and pure maths. Nothing else** — no entity, no world, no time. That restriction is
what makes the whole permutation set pre-resolvable per index at bind time; widening it would
quietly turn a bind-time table into a Molang evaluation inside `getShape`.

`query.block_state('ns:name')` and `query.block_property('ns:name')` are the same query. The state
name arrives as an argument, which in a float-typed language means an interned identity (SC-130
§2.1); the binding resolves it back. A state answers as a **number** when it is integral or boolean
and as its own interned identity when it is a string, so that `> 2` and `== 'tall'` both mean what
they look like.

A query outside that set reads 0, matching Bedrock. It is also visible *before* it runs: the
compiler records every referenced name, so a condition reaching for entity state is reportable at
load rather than silently always-false.

### 3.2 A condition that will not compile

The permutation is **dropped**, with `SCE-1035` naming the position. It is not defaulted to
always-matching or never-matching, because both are wrong and each silently changes what the block
looks like in half its states.

## 4. The 32 components

| Component | Java counterpart | Difficulty |
|---|---|---|
| `collision_box`, `selection_box` | `VoxelShape` | Bedrock is a single AABB from origin+size, capped at 1.875 blocks and required to start inside the block; Java is a union. Widening is easy, and the new BP `shapes/` folder may change this. |
| `destructible_by_mining` | hardness + tool | Bedrock uses **seconds**, with per-item overrides; Java uses hardness plus tool tiers plus mining speed. Not a linear conversion. |
| `destructible_by_explosion` | blast resistance | direct |
| `geometry` + `material_instances` | baked model | §5 |
| `light_emission`, `light_dampening` | `lightEmission`, `lightBlock` | direct |
| `friction`, `map_color`, `flammable`, `replaceable`, `movable` | direct | |
| `placement_filter` | `canSurvive` | filter-based (SC-140) |
| `transformation` | model transform | no direct counterpart; applied at bake |
| `redstone_conductivity`, `redstone_consumer` | redstone behaviour | partial |
| `queued_ticking`, `random_ticking` | `tick`, `randomTick` | direct |
| `liquid_detection`, `precipitation_interactions`, `breathability`, `chest_obstruction`, `connection_rule`, `support`, `item_visual`, `destruction_particles`, `unit_cube`, `crafting_table`, `display_name`, `loot`, `custom_components` | — | to be assessed |

`TODO(SC-150)`: complete the table with a status per component and open the coverage entries.

## 5. Geometry and materials

Bedrock block geometry is an **entity-style bone/cube model** with per-face material instances and
Molang `bone_visibility`. Java block models are a different `elements[]`/`faces` format with
`cullface`, and have no bone hierarchy.

Two paths:

- **Path A — transpile to a Java block model** when the geometry is axis-aligned boxes with static
  bone visibility. Gets vanilla chunk meshing, ambient occlusion, culling and lighting for free.
  This is the common case and should be the default.
- **Path B — render dynamically** when the geometry needs Molang-conditional bone visibility,
  non-axis-aligned rotation, or a poly mesh. Costs a block-entity renderer.

`TODO(SC-150)`: the precise predicate that decides A versus B, and the fallback when A is attempted
and fails.

`render_method` → `RenderType`: `opaque` → solid, `alpha_test` → cutout, `alpha_test_single_sided` →
cutout with culling, `blend` → translucent, `double_sided` → solid without culling. Imperfect;
`face_dimming`, `ambient_occlusion` and `tint_method` need their own treatment.

## 6. Triggers and event responses

7 trigger components (`on_fall_on`, `on_interact`, `on_placed`, `on_player_destroyed`,
`on_player_placing`, `on_step_off`, `on_step_on`) dispatching 15 event responses (`add_mob_effect`,
`damage`, `decrement_stack`, `die`, `play_effect`, `play_sound`, `remove_mob_effect`, `run_command`,
`set_block`, `set_block_at_pos`, `set_block_state`, `spawn_loot`, `swing`, `teleport`,
`transform_item`).

`run_command` executes a **Bedrock** command string. SC-190 covers command translation; unsupported
commands degrade with a diagnostic rather than executing something approximate, because executing an
approximation of a command a pack author wrote is worse than not executing it.

## 7. Testing contract

Per-component conformance cases; permutation resolution goldens covering override order, multiple
matching permutations and none matching; index encode/decode round trips including traits; a Path A
versus Path B decision test; and render goldens for both paths.
