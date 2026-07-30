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

Sections to write:

- 2.1 `description.states`: `{"ns:name": [v1, …]}` and `{"values": {"min": 0, "max": 15}}`; the
  16-value cap; permitted value types (bool, string, int).
- 2.2 `description.traits`: `minecraft:placement_direction` (`cardinal_direction`,
  `facing_direction`, `y_rotation_offset`) and `minecraft:placement_position` (`block_face`,
  `vertical_half`). Expanded into states before encoding, appended in a fixed order (SC-120 §6.1).
- 2.3 The mixed-radix index encoding, and why declaration order is normative.
- 2.4 Behaviour when the product exceeds the largest size class.

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

`TODO(SC-150)`: the exact truthiness rule for a non-boolean Molang result, and whether merging is
per top-level component key or deeper. Bedrock's behaviour needs observation, not inference.

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
