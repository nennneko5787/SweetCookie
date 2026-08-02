# SC-190 — Loot, recipes, trading, spawn rules and functions

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

The data domains. Cheap relative to their value, because they route through the virtual data pack
and vanilla does most of the work.

---

## 1. Decisions already taken

**Everything here is served through a virtual data pack**, not a bespoke system:
`LepusDataPackSource implements RepositorySource` synthesises vanilla-format JSON from the IR
on demand, installed via `PackFinderInstaller` (SC-230).

This buys, for free: vanilla `/reload`, vanilla client sync of recipes and tags, correct interaction
with other data packs and mods, and hot reload with no extra machinery. Writing our own loot roller
would be more code and worse.

## 2. Loot tables

**Bedrock's loot format is a fork of Java's pre-1.13 format.** Not a subset, not a superset — a fork
that diverged in 2017. Java has since moved to namespaced, registry-driven loot with number
providers. Direct conversion is not possible; the translation is a real transform with real gaps.

Divergences to specify:

| | Bedrock | Java (modern) |
|---|---|---|
| Function names | `set_data`, `enchant_random_gear`, `looting_enchant`, `set_actor_id`, unnamespaced | `minecraft:`-namespaced, different set |
| `rolls` | integer or `{min, max}` | number provider |
| Conditions | `random_chance_with_looting`, unnamespaced | namespaced, predicate-based |
| Item identity | id plus an aux/damage value | id plus data components |
| Entity loot | referenced by path from `minecraft:loot` | referenced by `LootTable` id |

`TODO(SC-190)`: the complete function and condition mapping table, with a status per entry. Bedrock
functions with no Java equivalent are emulated by a custom `LootItemFunction`; that is acceptable
because the loot registry is data-driven.

## 3. Recipes

Seven Bedrock types: `minecraft:recipe_shaped`, `recipe_shapeless`, `recipe_furnace`,
`recipe_brewing_mix`, `recipe_brewing_container`, `recipe_smithing_transform`, `recipe_smithing_trim`.

Notes: Bedrock routes recipes to stations via a `tags` array (`["crafting_table"]`); items carry
aux/data values; there is no recipe unlocking or advancement integration; and creative-menu grouping
comes from `item_catalog/` rather than a recipe book.

Brewing has no data-driven Java equivalent and needs a Lepus-side implementation.

`TODO(SC-190)`: per-type mapping and the ingredient-matching rule for custom items, which are all
one carrier `Item` distinguished by a data component (SC-120 §4) — vanilla ingredient matching is by
item identity, so custom ingredients need a custom `Ingredient` predicate.

That last point is the one genuine subtlety in this document and it should be settled before
implementation starts.

## 4. Trading

`trading/*.json` with tiers and trades. Java made villager trades data-driven at 26.1
(`data/<ns>/villager_trade/`), which helps on 26.2 but not on 1.21.11, so the translation targets
Lepus's own trade evaluation with the data pack used only where it fits.

`TODO(SC-190)`: whether to use 26.1's data-driven trades on the node that has them, given SC-220 §5
forbids version-conditional *behaviour*. Likely answer: no — implement it once, ourselves, so both
nodes behave identically.

## 5. Spawn rules

`spawn_rules/` uses its own condition components rather than the SC-140 filter grammar directly,
though several embed it:

`minecraft:spawns_on_block_filter`, `spawns_above_block_filter`, `brightness_filter`,
`biome_filter`, `height_filter`, `difficulty_filter`, `world_age_filter`, `delay_filter`,
`mob_event_filter`, `player_in_village_filter`, `weight`, `herd`, `density_limit`, `permute_type`,
`spawns_lava`, `disallow_spawns_in_bubble`.

Mapped onto Java's `SpawnPlacements` plus a Lepus spawner pass. Bedrock's spawn density and
herd semantics differ from Java's mob caps; the divergence goes in `fidelity` notes rather than
being approximated.

## 6. Functions and commands

`functions/*.mcfunction` plus `tick.json`.

**Bedrock commands are not Java commands.** Selectors differ (`@e[type=, family=, hasitem={},
haspermission={}, scores={}]`), `execute` has a different grammar, and Bedrock has commands Java
lacks entirely (`/scriptevent`, `/camera`, `/inputpermission`, `/hud`, `/dialogue`, `/damage`,
`/structure`, `/ride`, `/aimassist`, `/fog`, `/music`).

Decision: **translate what maps cleanly, refuse the rest with a diagnostic.** Executing an
approximation of a command a pack author wrote is worse than not executing it, because the failure
becomes silent and the behaviour becomes subtly wrong.

`bedrock-samples` ships `metadata/command_modules/mojang-commands.json` — a machine-readable command
grammar. Use it as the source of truth rather than hand-writing a parser.

`TODO(SC-190)`: the per-command mapping table, generated from that file into a coverage shard.

## 7. Structures

`.mcstructure` is little-endian NBT with a palette-plus-index layout and `block_position_data` for
block entities — a completely different format from Java's big-endian `.nbt` with a `blocks[]` list.
Needs its own reader.

`TODO(SC-110)` (cross-referenced): whether structures enter the IR at all or stay opaque assets
resolved at placement time. Leaning opaque.

## 8. Features, feature rules, biomes, dialogue

Deferred beyond 0.x. Bedrock has 26 feature types and a `feature_rules/` scatter system with no
Java equivalent; Java has a noise router and density functions with no Bedrock equivalent. Terrain
will not match and pretending otherwise would be worse than saying so.

Packs using them load; the files produce one `unsupported` diagnostic each.

## 9. Testing contract

Loot: seeded roll goldens per function and condition. Recipes: crafting-grid conformance cases per
type, including custom ingredients. Trading: tier-generation goldens. Spawn rules: a controlled
world with fixed conditions asserting what spawns. Functions: per-command execution cases and a
refusal case for each unsupported command.
