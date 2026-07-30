# SC-140 — Filters

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

Bedrock's declarative predicate system. **106 tests.** Separate from Molang — filters accept no
expressions, only structured comparisons.

---

## 1. Grammar

```
Filter     := FilterTest | FilterGroup | [Filter, …]        // a bare array is implicitly all_of
FilterTest := { "test": <name>, "subject": <subject>, "operator": <op>,
                "domain": <string>, "value": <any> }
FilterGroup:= { "all_of": [Filter…] } | { "any_of": […] } | { "none_of": […] }
```

| Field | Values |
|---|---|
| `subject` | `self` (default), `target`, `parent`, `player`, `other`, `block`, `damager`, `none` |
| `operator` | `==` `!=` `<` `<=` `>` `>=` `equals` `not`; default `==` |
| `domain` | test-specific; e.g. the state name for `is_block` |

## 2. Decisions already taken

**Filters compile to a tree of typed predicates at ingest**, like Molang (SC-110 §7), not
interpreted from JSON per evaluation. Same reasons: errors surface at load, and evaluation is on the
AI hot path.

**An unknown test evaluates to `false` and emits one diagnostic per filter site, once.** Not per
evaluation — a filter on a goal runs every tick and would flood the log. Choosing `false` rather than
`true` means an unimplemented test makes a behaviour not happen, which is safer than making it
happen unconditionally.

**Short-circuit order is declaration order** within `all_of` / `any_of`. Bedrock does not document
an evaluation order, but packs are written assuming cheap tests come first, so honouring declaration
order is both the least surprising choice and the fastest.

## 3. The 106 tests

Grouped for implementation, each with a coverage entry:

| Group | Examples |
|---|---|
| Identity and family | `is_family`, `has_tag`, `is_variant`, `is_mark_variant`, `is_skin_id`, `is_color` |
| Health and damage | `actor_health`, `has_damage`, `is_missing_health`, `taking_fire_damage`, `was_last_hurt_by` |
| Equipment and inventory | `has_equipment`, `has_equipment_tag`, `all_slots_empty`, `any_slot_empty`, `has_ranged_weapon`, `has_silk_touch`, `has_item_with_component` |
| State | `is_baby`, `is_sneaking`, `is_sprinting`, `is_sleeping`, `is_sitting`, `is_moving`, `is_navigating`, `is_riding`, `is_leashed`, `is_tamed`, `is_panicking` |
| Environment | `is_biome`, `has_biome_tag`, `is_underwater`, `in_lava`, `in_clouds`, `is_snow_covered`, `weather`, `light_level`, `is_brightness`, `is_temperature_type` |
| Time | `clock_time`, `hourly_clock_time`, `is_daytime`, `moon_phase`, `moon_intensity` |
| Spatial | `distance_to_nearest_player`, `target_distance`, `owner_distance`, `home_distance`, `is_altitude`, `is_underground`, `in_block`, `is_block` |
| Properties | `bool_property`, `int_property`, `float_property`, `enum_property`, `has_property` |
| Game rules and difficulty | `is_game_rule`, `is_difficulty`, `random_chance` |
| Relationship | `is_owner`, `is_target`, `trusts`, `is_leashed_to`, `in_caravan`, `is_in_same_vehicle` |

`TODO(SC-140)`: the full table with per-test `domain` and `value` semantics, generated from
`bedrock-samples` `metadata/doc_modules/entities.json`.

## 4. Where filters appear

Entity `events[].filters`, `component_groups` via events, AI goal `*_filters` / `target_filters` /
`entity_types[].filters`, `minecraft:environment_sensor`, `minecraft:damage_sensor`,
`minecraft:interact.interactions[].on_interact.filters`, loot-table conditions, trade tables, block
`placement_filter`, item `use_modifiers`, features and feature rules.

Spawn rules use their **own** condition components (`minecraft:brightness_filter`,
`minecraft:biome_filter`, `minecraft:height_filter`, …), some of which embed this grammar. SC-190
covers them; they are not the same system despite the name.

## 5. Subject resolution

Each evaluation supplies a `FilterContext` binding the subjects that exist in that context. A test
naming a subject the context does not bind evaluates to `false` with a one-time diagnostic — for
example `damager` outside a damage event.

`TODO(SC-140)`: the context/subject availability matrix.

## 6. Performance

Filters run on the AI hot path — every goal's `canUse` every tick, for every entity. Sections to
write: a static cost model per test, reordering within `all_of` when it is provably safe, caching of
results that cannot change within a tick, and a budget.

`TODO(SC-140)`: set the budget once SC-160's goal scheduling is settled.

## 7. Testing contract

Each of the 106 tests gets a conformance case placing an entity in a known state and asserting the
filter's outcome, plus grouping tests for `all_of` / `any_of` / `none_of` nesting, implicit-array
behaviour, operator defaults, and unknown-test degradation.
