# SC-160 — Entities

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

The largest translation problem in the project. Bedrock entities are a data-driven entity–component
system: **120 components**, **171 `minecraft:behavior.*` AI goals**, 49 flag-like property
components, component groups mutated at runtime by events, and typed entity properties. Java has a
hardcoded `EntityType` registry and compiled `Goal` classes.

Registration is SC-120 §5 and is not repeated here.

---

## 1. Decisions already taken

**One generic entity class per family** (`MOB`, `PROJECTILE`, `OBJECT`), holding a live component
map. There is no class per Bedrock entity.

**Component groups rebuild the `GoalSelector`.** Java's goal selector is mutable, so adding or
removing a group means recomputing the goal set and re-populating both selectors. Rebuild is
scheduled to a tick boundary, never mid-iteration.

**Entity properties and Molang `variable.*` do not use `SynchedEntityData`** (constitution rule 6);
they sync over the sideband (SC-270 §8). This removes the entire class of Via metadata-index
failures.

**Bedrock physics and navigation are not reproduced in 0.x.** Gravity, drag, step height, friction
application order and node evaluation all differ, and `minecraft:uses_legacy_friction` exists
precisely because Mojang changed them. Bedrock mobs will move slightly wrong. This is documented as a
`fidelity` note on the movement and navigation components rather than approximated badly.

## 2. Definition structure

```
minecraft:entity
  description       identifier, is_spawnable, is_summonable, is_experimental,
                    runtime_identifier, spawn_category, properties{}, scripts{}, animations{}
  components        the base set
  component_groups  named sets added and removed at runtime
  events            declarative mutations of the group set
```

Sections to write: identifier and family inference, `spawn_category` → `MobCategory` mapping (which
selects the registered type, SC-120 §5), and `runtime_identifier` — a field letting an entity
masquerade as a vanilla one to inherit hardcoded client behaviour, with no Java analogue; likely
`wontfix` with a per-value allowlist for the few cases worth special-casing.

## 3. The component system

`TODO(SC-160)`: the full table of 120 components with Java counterpart and status. Grouped:

| Group | Notes |
|---|---|
| Movement (`minecraft:movement.*`) | basic, generic, fly, glide, hover, jump, skip, sway, amphibious → `MoveControl` implementations |
| Navigation (`minecraft:navigation.*`) | walk, fly, float, climb, generic, hover, swim, each with flags (`can_path_over_water`, `avoid_sun`, `can_break_doors`, `blocks_to_avoid`) → `PathNavigation` + `NodeEvaluator` |
| Physical | `collision_box`, `physics`, `pushable`, `knockback_resistance`, `custom_hit_test`, `scale`, `is_collidable` |
| Vital | `health`, `breathable`, `damage_sensor`, `environment_sensor`, `fire_immune`, `hurt_on_condition` |
| Social | `breedable`, `tameable`, `rideable`, `leashable`, `angry`, `anger_level`, `trusting`, `type_family` |
| Inventory | `inventory`, `equipment`, `equippable`, `loot`, `economy_trade_table` |
| Lifecycle | `ageable`, `timer`, `despawn`, `transformation`, `spawn_entity` |
| Flags | the 49 property-like components: `can_climb`, `is_baby`, `variant`, `mark_variant`, `push_through`, … |

### 3.1 Component storage on the hot path

A `Map<String, Object>` consulted per tick per entity is the obvious implementation and the wrong
one. `TODO(SC-160)`: specify an interned-key array layout with a presence bitset, sized from the
union of components any loaded pack uses.

## 4. Component groups and events

Bedrock events apply `add` / `remove` of component groups, with `sequence[]`, weighted
`randomize[]`, `filters` (SC-140), `set_property`, `trigger`, `queue_command`, `emit_vibration`, and
a `%50`-style percentage syntax on `add`.

Built-in events: `minecraft:entity_born`, `entity_spawned`, `entity_transformed`, `on_prime`.
Triggers: `on_death`, `on_hurt`, `on_hurt_by_player`, `on_ignite`, `on_friendly_anger`,
`on_start_landing`, `on_start_takeoff`, `on_target_acquired`, `on_target_escape`, `on_wake_with_owner`.

`TODO(SC-160)`: event application order, re-entrancy (an event triggering another), and the
recursion bound. Bedrock's behaviour here is undocumented and needs observation.

## 5. Entity properties

Typed, declared in `description.properties`: `bool`, `int` with range, `float`, `enum`. Queryable as
`q.property('ns:x')`, settable by `set_property` in events, exposed to the Script API. The closest
Bedrock analogue to Java's synched data, but declarative.

Stored in Lepus's own per-entity state, persisted in entity NBT, synced over the sideband.
Geyser mirrors them through `GeyserDefineEntityPropertiesEvent` — noting Geyser's caps of 32
properties per type, 16 enum values, 32-character names (SC-210).

## 6. The 171 AI goals

A registry of goal factories keyed by component name, producing Java `Goal` instances.

Sections to write: priority semantics (Bedrock and Java agree that lower numbers run first), mutex
flag inference, the rebuild protocol, and the shared parameters (`priority`, `speed_multiplier`,
`target_filters`, `must_see`, `must_see_forget_duration`, `cooldown`).

`TODO(SC-160)`: the full 171-row table. It is generated into `docs/compatibility/entity-goals.md`
from the coverage shard, not hand-maintained here.

**Scope control applies here more than anywhere.** Implementing goals in alphabetical order is how
this project dies. The acceptance add-ons decide the order.

## 7. Spawning

`spawn_rules/` uses its own condition components rather than the filter grammar directly:
`minecraft:spawns_on_block_filter`, `brightness_filter`, `weight`, `herd`, `biome_filter`,
`density_limit`, `height_filter`, `spawns_above_block_filter`, `permute_type`, `delay_filter`,
`world_age_filter`, `difficulty_filter`, `mob_event_filter`, `player_in_village_filter`,
`spawns_lava`, `disallow_spawns_in_bubble`. Covered in SC-190.

## 8. Testing contract

Per-component and per-goal conformance cases in a controlled arena; component-group transition
goldens; event-ordering tests; property persistence across save/load and across the sideband; and a
performance test with 200 custom entities asserting the tick budget from SC-250.
