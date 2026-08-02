# Lepus

[English](README.md) | [日本語](README.ja.md)

**Run Minecraft Bedrock Edition Add-Ons on Minecraft Java Edition.**

Lepus loads `.mcaddon` / `.mcpack` files — behavior packs *and* resource packs — and executes
them inside Java Edition: custom blocks, items, entities with their component/AI-goal definitions,
Bedrock skeletal models and animations, Molang, Snowstorm particles, loot tables, recipes and
spawn rules.

| | |
|---|---|
| Loaders | Fabric, NeoForge |
| Minecraft | 26.2, 1.21.11 *(more to follow)* |
| Side | **Required on both client and server** |
| Status | **Pre-alpha.** Blocks and items from a real add-on load, bind and render; entities, Molang queries and particles do not. |

## Implementation status

A box is checked when the capability works end to end in the running game. The fraction after it
counts entries in [`spec/coverage/`](spec/coverage/) that have an implementation behind them
(`implemented` + `partial`) against the identifiers tracked in that area — the full per-identifier
table is [`docs/compatibility/summary.md`](docs/compatibility/summary.md), and **1363 identifiers
are tracked in total**. The two are not the same measurement: a capability can be usable while most
of its long tail of identifiers is still untouched.

**Loading a pack**

- [x] `.mcaddon`, `.mcpack` and plain-folder containers — 14/19
- [x] Manifest `format_version` 1, 2 and 3, module and dependency declarations, `min_engine_version`
- [x] Subpack overlays, `texts/*.lang`
- [x] Enable, disable and reorder per world at runtime, with the assignment persisted per world
- [x] Unknown components, goals, queries and format versions log a diagnostic and become no-ops
      rather than crashing the world (SC-240)

**Blocks** — 5/54 components, 0/8 states and traits

- [x] Bound to anonymous pool slots, with generated blockstates and models per state
- [x] `minecraft:collision_box`, `minecraft:selection_box`
- [x] `minecraft:geometry` transpiled from `.geo.json`, `minecraft:unit_cube`
- [x] `minecraft:material_instances` texture resolution, through `terrain_texture.json`
- [x] Creative menu, grouped by pack and ordered by `menu_category`
- [ ] Permutations beyond the parsed form, block traits, and the remaining 49 components

**Items** — 7/46 components

- [x] One carrier item plus a data component, never a registry entry of its own
- [x] `max_stack_size`, `durability`, `wearable`, `enchantable`, `glint` / `foil`, `display_name`
- [x] `item_display_transforms` from the add-on's geometry — 1/17
- [ ] `allow_off_hand`, food, cooldown, digger, and the remaining 39 components

**Rendering**

- [ ] Attachables — first-person and worn 3D models, animated. In progress, 2/10
- [ ] Bedrock skeletal animation files — 0/22
- [ ] Render controllers 0/13, client entity definitions 0/15
- [ ] Snowstorm particles — 0/33

**Molang** — 1/396

- [x] Lexer, compiler and math binding in `core/molang`
- [ ] No `math.*` or `query.*` identifier is claimed in the ledger yet

**Entities** — 0/373

- [ ] Components 0/118, AI goals 0/173, events 0/23, entity properties 0/59
- [ ] Filters — 0/108

**World and gameplay data** — 0/152

- [ ] Loot tables 0/30, recipes 0/9, trading 0/9, spawn rules 0/16, functions and commands 0/88

**Interoperability and scripting**

- [ ] Geyser 0/7, ViaVersion / ViaBackwards 0/8
- [ ] Script API — 0/23

## Why

Bedrock's add-on system is genuinely more expressive than Java's data packs in several areas:
data-driven entities with a 120-component / 171-AI-goal vocabulary, a real skeletal animation
system with a state machine on top, Molang, and the Snowstorm particle engine.

And on a [Geyser](https://geysermc.org/) server, Bedrock players are already connecting — so
running the add-on they were built for is the natural thing to do. **Geyser deliberately does not
support behavior packs**, because executing them requires changes on the Java server, which a proxy
cannot make. Lepus is the Java server side that Geyser is missing.

## Design highlights

- **Format parsing has no Minecraft dependency.** `core/` compiles without `net.minecraft.*` on the
  classpath, so ~40k lines of add-on parsing are unit-testable in seconds and shared verbatim across
  every Minecraft version.
- **Identifiers are derived, never allocated.** `wizardry:magic_wand` always becomes
  `lepus:wizardry.magic_wand`. No allocation table, no ID negotiation, no split-brain between
  client and server.
- **Packs attach and detach at runtime, per world — like they do on Bedrock.** No Bedrock feature
  ever gets a Java registry entry of its own: items live entirely in a data component, entities in
  NBT, and blocks bind to a pre-reserved pool of anonymous slots whose assignment is persisted per
  world. Enable, disable, reorder or update an add-on mid-game and it takes effect at the next tick.
  The only thing that needs a restart is enlarging the block pool, and it tells you the exact number.
- **Detaching a pack does not destroy your world.** Blocks keep their slot and their state, item
  stacks keep their NBT, entities go inert — all of it clearly marked as "needs add-on X" and
  restored losslessly when the pack comes back. Changing a block's state list remaps existing blocks
  instead of scrambling them.
- **Custom content never occupies a vanilla network registry ID.** On the wire it travels as a
  vanilla carrier plus a name-based sideband, which makes it work identically through
  ViaVersion / ViaBackwards as it does natively.

See [`spec/`](spec/) for the normative specification and
[`docs/compatibility/`](docs/compatibility/) for the generated feature-coverage table.

## Compatibility

Lepus implements someone else's specification, so "what works" is tracked explicitly rather
than claimed. Every Bedrock feature ID has an entry in [`spec/coverage/`](spec/coverage/) with a
status, the implementing class, a fidelity note and a link to its conformance test — and CI fails if
any of those links are dishonest.

## Interoperability

- **Geyser** — Lepus registers translated content through the `geyser-api` custom item / block
  / entity events and serves the add-on's own resource pack half to Bedrock clients unmodified.
- **ViaVersion / ViaBackwards** — supported as a first-class case, not an afterthought. A client
  running Lepus behaves identically whether or not its Minecraft version matches the server's.

## Licensing and attribution

**MIT** — see [LICENSE](LICENSE).

Lepus ships no Mojang content. Bedrock schema metadata is fetched at build time and used only
to generate code; it is never redistributed. Third-party attributions and the read-only-reference
policy for GPL sources are in [NOTICE](NOTICE); the reasoning is
[ADR-0006](spec/adr/0006-licensing-and-attribution.md).

Minecraft is a trademark of Mojang Synergies AB. This project is not affiliated with Mojang or
Microsoft.
