# Glossary

Informative. Read it once before your first change; the two games use overlapping words for
non-overlapping things and the resulting confusion is expensive.

## The trap words

| Word | In Bedrock | In Java | In this repo |
|---|---|---|---|
| **component** | a named behaviour attached to an entity/block/item in JSON, e.g. `minecraft:breathable`. There are 120 entity, 32 block and 44 item components. | since 1.20.5, a typed value on an `ItemStack`, e.g. `minecraft:max_damage` | always means the **Bedrock** sense. The Java sense is written **data component**, never bare "component". |
| **resource pack** | the client half of an add-on: models, animations, render controllers, particles, textures, materials, JSON UI | client assets: models, blockstates, textures, sounds | qualified every time: **RP** (Bedrock) or **Java resource pack** |
| **behavior pack** | the server half: entities, blocks, items, loot, recipes, scripts | no equivalent | **BP** |
| **state** | `description.states` on a block: a named list of at most 16 values | `BlockState`: one member of the product of a block's `Property<?>` set | **Bedrock block state** vs **Java `BlockState`**. Never bare "state" in a spec sentence. |
| **property** | *two* unrelated things: (a) an entity property — a typed, named value declared in `description.properties`; (b) informally, one of the 49 flag-like entity components | `Property<?>`, a block state axis | **entity property** vs **Java `Property`** |
| **animation** | a keyframe track set over named bones, with Molang-valued keyframes | usually a texture `.mcmeta` flipbook, or `AnimationDefinition` | **Bedrock animation** vs **flipbook** |
| **event** | a declarative mutation of an entity's component groups | a Java callback or a loader event bus message | **Bedrock event** vs **loader event** |
| **entity** | a data-driven definition assembled from components | a Java class extending `Entity` | qualified |
| **pack format** | not a thing; Bedrock uses `format_version` per file and `min_engine_version` per pack | `pack_format` / `min_format`+`max_format` in `pack.mcmeta` | **`format_version`** (Bedrock, per file) vs **pack format** (Java, per pack) |

## Bedrock terms

**Add-on** — a `.mcaddon` containing one or more packs; colloquially any BP+RP pair.

**Attachable** — an RP definition giving an item a 3D model, animations and render controllers when
held or worn. Java has no equivalent.

**Animation controller** — a finite state machine whose transitions are Molang conditions and whose
states blend animations. Exists in both BP (server, limited) and RP (client) form.

**Component group** — a named bundle of components that a Bedrock event can add to or remove from a
live entity, changing its behaviour at runtime. The mechanism with no Java analogue at all.

**Filter** — Bedrock's declarative predicate system: `{"test": ..., "subject": ..., "operator": ...,
"domain": ..., "value": ...}` combined with `all_of` / `any_of` / `none_of`. **106 tests exist.**
It is *not* Molang and does not accept expressions.

**Geometry** — a Bedrock model: a bone hierarchy containing cubes, with pivots, rotations, inflation
and optionally locators and arbitrary polygon meshes. Shipped as `.geo.json`. Two mutually
incompatible file shapes exist; see SC-180.

**Locator** — a named point (optionally with rotation) in a geometry's bone hierarchy, used to
attach particles, sounds, held items and riders.

**Molang** — Bedrock's expression language. Float-typed, evaluated per frame on the client and in a
few server contexts. `query.*` (315 of them) reads engine state, `variable.*` is per-entity mutable
storage, `temp.*` is expression-scoped, `math.*` has 61 functions. See SC-130.

**Permutation** — an entry in a block's ordered `permutations[]` list: a Molang condition over the
block's Bedrock states plus a set of components to apply. Later entries override earlier ones.

**Render controller** — an RP definition that chooses, per frame and via Molang, which geometry,
textures and materials an entity renders with, and which bones are visible.

**Runtime identifier** — a field letting a custom entity masquerade as a vanilla one so it inherits
hardcoded client behaviour. No Java analogue.

**Script API** — Mojang's JavaScript API (`@minecraft/server` and friends), executed by a QuickJS
runtime inside Bedrock. 439 exported classes as of `@minecraft/server` 2.8.0.

**Snowstorm** — the informal name of Bedrock's particle format: emitter and particle components with
Molang-valued fields and curve inputs.

**Subpack** — a variant directory inside a pack, selected by the client's memory tier, whose files
override same-path files in the pack root.

**Trait** — an engine-provided automatic block state, e.g. `minecraft:placement_direction`.

## Lepus terms

**Carrier** — the vanilla block state, item or entity type that custom content masquerades as on the
network. See SC-270.

**Coverage entry** — one record in `spec/coverage/*.yaml` describing the implementation status of a
single Bedrock feature ID.

**Derived identifier** — the Java `ResourceLocation` computed from a Bedrock identifier by the pure
function in SC-120. Never allocated, never negotiated.

**Ghost / placeholder content** — the block, item or entity registered in place of content whose
pack is no longer loaded, preserving state and NBT so nothing is lost. Constitution rule 5.

**IR** — the intermediate representation: the Minecraft-free, `format_version`-normalised data model
that every add-on file parses into. SC-110. The boundary between `core/` and everything else.

**Ledger** — the on-disk record of every content identifier ever registered, with its block-state
schema, used for placeholder registration and drift detection.

**Node** — one Minecraft version in the Stonecutter build tree (`1.21.11`, `26.2`). A **branch** is
one loader (`common`, `fabric`, `neoforge`).

**Sideband** — Lepus's own plugin-channel transport, carrying the real identity and state of
carrier-disguised content, addressed by name. SC-270.

**Tier 1 / Tier 2** — content that must be registered before the registry freezes and therefore
needs a restart to change (Tier 1: existence of blocks/items/entity types, and a block's state
list), versus everything else, which hot-reloads (Tier 2). Constitution rule 7.

## Abbreviations

**BP** behavior pack · **RP** resource pack · **AC** animation controller · **RC** render controller
· **IR** intermediate representation · **MDG** ModDevGradle · **CMD** custom model data
