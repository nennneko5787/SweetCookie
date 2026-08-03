# SC-120 — Registration, storage and identifier persistence

**Status:** complete · **Since:** 0.1.0 · **Supersedes:** —

How Bedrock content becomes something Minecraft can store in a chunk, an inventory and an entity
list — without a per-feature registry entry, so that packs attach and detach at runtime the way they
do on Bedrock.

**This document governs on-disk formats. Getting it wrong corrupts worlds.** Changes require an ADR.

---

## 1. The problem

Java freezes its registries before a world is selected. Bedrock add-ons are per-world data that
users expect to toggle freely. Those two facts are irreconcilable *if* each Bedrock block, item and
entity needs its own registry entry.

So none of them get one. Constitution rule 7.

This is only affordable because of SC-270: custom content never occupies a vanilla **network**
registry ID either, so client and server registries need not agree, and nothing about registration
is visible on the wire.

## 2. Two identifier spaces

| | Logical identity | Physical slot |
|---|---|---|
| Example | `lepus:wizardry.magic_block` | `lepus:block_16` state index 37 |
| Derived or allocated | **derived**, pure function of the Bedrock id | **allocated**, recorded in the ledger |
| Where it appears | commands, sideband, coverage, diagnostics, config, logs, everything a human reads | chunk palettes, and nowhere else a human sees |
| Stable across | machines, restarts, pack reordering, reinstalls | one world's ledger only |
| On the network | as an interned name (SC-270) | **never** |

Confusing them is a review-blocking defect (constitution rule 4).

### 2.1 A third relation: what Bedrock calls something that already exists

Both spaces above are about content a pack **brings**. A pack may also address content that was
already there — renaming the totem, retexturing the sword — and then no identifier is derived,
because Minecraft registered the thing years ago. The relation needed is **equivalence**, not
derivation, and it runs the other way: given Bedrock's spelling, which vanilla thing is it.

The identifiers themselves already agree; `minecraft:totem_of_undying` is that in both games. What
does not agree is the two places Bedrock uses an **internal short name** instead:

| | Bedrock | Java |
|---|---|---|
| translation | `item.totem.name`, `tile.wool.white.name` | `item.minecraft.totem_of_undying`, `block.minecraft.white_wool` |
| item picture | `textures/items/totem.png` | `textures/item/totem_of_undying.png` |

**Measured, not assumed: 525 of 1,437 names are spelled differently on the two sides**, so treating
the short name as the Java path is wrong for more than a third of the game and wrong in ways
inspection does not catch — `wool.white` is `white_wool`.

The tables are **generated** (`./gradlew generateBedrockConstants`, `spec/upstream/fetch.md`) by
joining Mojang's two language files on the English display name, and an entry survives only when
that name is unique in **both** games. Nothing is fitted by hand and nothing is assumed by pattern.

**Ambiguity yields no mapping.** Bedrock has one `banner_pattern`; Java has nine items called
"Banner Pattern". Choosing among them would be a fitted constant, so none of them are mapped and
such an item keeps its vanilla name and picture. Refusing leaves a pack's intent unapplied where
choosing would apply it to the wrong thing, and only the second is a defect. A human answer may be
recorded in `spec/upstream/vanilla-names.manual.yaml`, which the generator merges and which wins.

The implementation is `core/registry`'s `BedrockVanillaNames` and `BedrockVanillaTextures`, beside
`IdMapper` — that one derives an identifier for content a pack brings, these recognise content that
was already there. None of the three has a coverage entry, because none is a Bedrock feature.

## 3. Logical identity

```
lepus:<sanitise(bedrock namespace)>.<sanitise(bedrock path)>
```

`sanitise` = lowercase with `Locale.ROOT`, then map every character outside `[a-z0-9_.-]` to `_`.

| Bedrock | Logical |
|---|---|
| `wizardry:magic_wand` | `lepus:wizardry.magic_wand` |
| `my_pack:fire/ember_block` | `lepus:my_pack.fire_ember_block` |
| `Cool-Pack:Thing` | `lepus:cool-pack.thing` |

The hyphen **survives**: it is inside `[a-z0-9_.-]` and is legal in a Java namespace. An earlier
revision of this table mapped it to `_`, contradicting the rule one line above it. Replacing a legal
character would have been worse than cosmetic — it creates collisions that need not exist, since
`cool-pack` and `cool_pack` would then derive the same identifier.

A Bedrock identifier with no namespace defaults to `minecraft`, matching Bedrock.

### 3.1 Collisions

Two distinct Bedrock identifiers may sanitise to the same string. The one appearing **later in pack
load order** (SC-100 §5) gets `_h` plus the first eight lowercase hex digits of
`sha1(original bedrock identifier, UTF-8)` appended to its path component. `SCE-4001` records both.

Deterministic given the same set of packs, which is the property that matters. A *different* set of
packs may produce a different winner; that is why the mapping is also written to the ledger, so an
existing world keeps its assignment (§6.3).

### 3.2 One implementation

The transformation exists once, in `net.nennneko5787.lepus.runtime.registry.IdMapper`.
Reimplementing it elsewhere is a review-blocking defect. It is a pure function with no dependency on
load order except through the collision rule, which takes load order as an explicit argument.

## 4. Items — one registration

A single `Item` is registered: `lepus:item`.

Every Bedrock item is an `ItemStack` of that item carrying:

| Data component | Holds |
|---|---|
| `minecraft:custom_data` | `{"lepus": {"id": "wizardry:magic_wand", "v": 1, …}}` |
| `minecraft:max_stack_size`, `max_damage`, `damage`, `food`, `tool`, `enchantable`, `rarity`, `item_name`, `item_model`, `equippable`, … | translated from the Bedrock item components (SC-170) |

*Why a dedicated carrier rather than a vanilla item:* a vanilla base would silently participate in
vanilla recipes and tags — a custom item based on `minecraft:paper` would craft into a book. A
dedicated item is in no tag and no recipe, so vanilla never touches it.

*Why this does not contradict SC-270:* `lepus:item` is the **server-side** identity. The
**wire** carrier is a vanilla item chosen from `vanilla_fallback`, substituted at encode time. The
two layers are independent and both are required.

Consequences:

- **Zero per-item registrations.** Items hot-plug completely.
- Stack merging works: `custom_data` participates in component equality, so two different Bedrock
  items never stack together and two identical ones do.
- Everything Bedrock's item components control — stack size, durability, food, tool behaviour,
  display name, model — is a data component, i.e. per stack, i.e. hot-reloadable (1.20.5+, so both
  supported Minecraft versions qualify).
- Behaviour Java bakes into `Item` subclasses (use, `useOn`, attack) is intercepted for stacks
  carrying `lepus` custom data and dispatched through the IR. SC-170 specifies the hooks.
- An item whose pack is unloaded keeps its stack and its NBT verbatim; it renders as a placeholder
  and states which add-on it needs (§7).

## 5. Entities — a fixed set

A fixed, version-independent set of `EntityType`s is registered at startup, one per
**(class family × spawn category)**:

| Class family | Java base | Registered as |
|---|---|---|
| `MOB` | `PathfinderMob` | `lepus:mob_<category>` for each `MobCategory` |
| `PROJECTILE` | `Projectile` | `lepus:projectile` |
| `OBJECT` | `Entity` | `lepus:object` |

`MobCategory` is baked into `EntityType` and governs natural-spawn caps, so it cannot be dynamic —
hence one type per category. That is roughly sixteen registrations in total, fixed forever,
independent of how many add-ons are installed.

The Bedrock identity lives in the entity's NBT (`lepus:{id, v}`), synced to the client over
the sideband (SC-270), never in the entity type.

### 5.1 What is per-instance, and therefore free

`EntityType` is a small descriptor. Almost everything that makes an entity behave the way it does
lives on the *instance*, so putting the Bedrock identity in NBT costs nothing:

| Concern | Where it lives | Bedrock source |
|---|---|---|
| **Pathfinding** | `PathNavigation`, created in the constructor, replaceable at any time | `minecraft:navigation.*` |
| **Movement** | `MoveControl`, `JumpControl`, `LookControl`, likewise | `minecraft:movement.*` |
| **AI** | `GoalSelector` / `targetSelector`, rebuilt whenever component groups change (SC-160) | `minecraft:behavior.*` |
| **Hitbox** | `getDimensions(Pose)` override; `EntityType`'s dimensions are only a default. Call `refreshDimensions()` on change — vanilla does exactly this for crouching and swimming | `minecraft:collision_box` |
| **Attachment points** (rider seat, name tag, vehicle) | part of the dimensions returned above | `minecraft:rideable` |
| **Collision and pushing** | `canBeCollidedWith`, `isPushable`, `canCollideWith`, `isPickable` — all instance methods | `minecraft:pushable`, `minecraft:is_collidable` |
| **Attributes** | `AttributeMap` per instance | `minecraft:health`, `minecraft:movement`, `minecraft:attack`, … |
| **Fire immunity, gravity, drag, step height** | instance methods or fields | `minecraft:fire_immune`, `minecraft:physics` |
| **Rendering** | one renderer per registered type, dispatching on the Bedrock identity | the resource pack |

Attributes need one accommodation: `DefaultAttributes` is keyed by `EntityType` and consulted when a
`LivingEntity` is constructed, and `getAttribute` returns null for an attribute the supplier omitted.
Each registered mob type therefore declares a **permissive supplier containing every attribute**, so
any Bedrock component can set any of them at any time.

### 5.2 What is genuinely baked, and what we do about it

Three things, and they are the whole reason there is more than one entity type.

**1. `MobCategory`.** It drives natural-spawn caps, spawn-density rules and despawn behaviour, and
it is read from the `EntityType` with no instance in hand. It cannot be made dynamic. Hence one
registered type per category — that *is* the ~16.

**2. `clientTrackingRange` and `updateInterval`.** Baked, and they decide when vanilla tells a client
an entity exists. The registered types use generous values; the runtime throttles per entity instead,
and custom state travels on the sideband where we control the rate anyway (SC-270).

**3. Vanilla `EntityType` tags.** A Bedrock entity cannot be a member of `#minecraft:skeletons`,
`#minecraft:undead` and so on, because tags bind to registry entries.

This third one is a **real, permanent limitation** and is documented as such. It does **not** affect
Bedrock add-ons themselves: Bedrock's own grouping mechanism is `minecraft:type_family`, an
independent string-tag system that Lepus implements directly (SC-160). What it affects is
interoperation with *Java* content — a data pack predicate or another mod testing
`#minecraft:undead` will not match a Bedrock skeleton. `SCE-2020` is emitted once per entity
definition whose components imply a vanilla tag we cannot grant, naming the tag.

`TODO(SC-120)`: a future opt-in could let a pack declare vanilla tag membership through a
Lepus-specific manifest field, implemented by injecting into the tag's contents at data-pack
build time. Not in 0.x.

### 5.3 Consequences of identity living in NBT

- An entity's **class family and spawn category are fixed at spawn time**, because they determine
  which `EntityType` was written to its NBT. If a pack update moves an entity from `monster` to
  `creature`, already-spawned individuals keep the old category until they are removed. `SCE-2021`,
  informational. Everything else about them updates live.
- **Spawn eggs** are Lepus items (§4), not vanilla `SpawnEggItem`, so they are unaffected.
- `/summon lepus:mob_monster` with no Bedrock identity in its NBT produces an inert entity and
  `SCE-3020`. `/lepus summon <bedrockId>` is the supported command.

An entity whose pack is unloaded becomes inert — invisible, immobile, not ticking, NBT preserved —
and is restored when the pack returns (§7).

## 6. Blocks — the slot pool

Blocks are the only content whose identity is written into chunk storage as a registry name plus
property values, so they are the only content that needs reserved registry entries.

### 6.1 Structure

At startup, Lepus registers **anonymous pool blocks** grouped into **size classes**. A size
class of size *N* is a `Block` whose state definition is a single `IntegerProperty` named `i` with
range `0 .. N-1`. Size classes are powers of two.

```
lepus:block_1/0000 … lepus:block_1/03ff       (N = 1,    1024 slots)
lepus:block_16/0000 … lepus:block_16/007f     (N = 16,    128 slots)
…
```

Binding a Bedrock block to a slot means: choose the smallest size class whose *N* is at least the
number of distinct Bedrock state combinations the block declares, take a free slot in that class,
and record the binding.

The Bedrock state tuple ↔ index mapping is the mixed-radix encoding of the block's declared states
in **declaration order**, with each state's values in **declaration order**, and the **first declared
state as the least significant digit**:

```
index = Σ over states s, in declaration order:  valueIndex(s) × Π (sizeOf(earlier states))
```

Traits (SC-150) are expanded into states before encoding, appended after the declared states in the
fixed order `placement_direction`, `placement_position`.

**The digit order is not arbitrary, and an earlier revision of this document had it backwards.** It
placed the first state in the *most* significant position — `Π (sizeOf(later states))` — which
contradicts the append rule in the sentence immediately after it. Appending a state only leaves
existing encodings alone when the appended state is the *most* significant digit, i.e. when earlier
states are the less significant ones:

| | states `A`(2) | after appending trait `T`(4) |
|---|---|---|
| least significant first | `a` | `a + 2·t` — old index `a` still decodes to the same `A`, with `T` at its first value |
| most significant first | `a` | `4·a + t` — **every placed block shifts** |

Both spellings are self-consistent; only one preserves the property the append rule exists to
provide, and getting it wrong scrambles every placed block the first time a pack enables a trait.
The correction is free today because no ledger has been written by a released build. SC-150 §2.3
states the same rule and the two must never diverge again.

### 6.2 Default pool

| Size class *N* | Slots | Block states |
|---|---|---|
| 1 | 1024 | 1 024 |
| 2 | 256 | 512 |
| 4 | 256 | 1 024 |
| 8 | 128 | 1 024 |
| 16 | 128 | 2 048 |
| 32 | 64 | 2 048 |
| 64 | 64 | 4 096 |
| 128 | 32 | 4 096 |
| 256 | 32 | 8 192 |
| 512 | 16 | 8 192 |
| 1024 | 8 | 8 192 |
| 4096 | 4 | 16 384 |
| **total** | **2 012 blocks** | **56 832 block states** |

For scale, vanilla is roughly 1 100 blocks and 27 000 block states. Reserved slots cost palette
space whether used or not; this default is the deliberate trade and it is configurable.

**The pool is exactly what the config says.** It is not sized from the saved worlds, and it does not
grow by itself.

An earlier revision of this section made the effective pool the element-wise maximum of the config
and every world's ledger. That was withdrawn, for three reasons:

- **The cost is instance-wide and the demand is per world.** One heavily-modded save would enlarge
  the palette for every other world in the instance — permanently for that session, in `BlockState`
  allocations and in the width of the global block-state identifier space. A player with forty
  vanilla saves and one add-on world would pay for the add-on world forty times.
- **It does not actually close the hole.** Registration finishes during mod init, so a world
  *copied in later* — a downloaded world, a restored backup — still would not fit, and the growth
  was recomputed from the worlds present at boot rather than persisted, so deleting a world silently
  shrank the pool again.
- **It hid the decision.** Growth was reported in a log line during startup, which is not a surface
  anybody reads.

A world whose ledger references a slot outside the registered pool therefore loads, keeps those
bindings (§6.3 rule 1 — a slot is never reused or recomputed), and reports `SCE-4013` naming the
size class, the count needed and the config line. Raising `blockPool` and restarting restores those
blocks exactly. Nothing is lost in the meantime; the blocks simply do not resolve.

### 6.3 Allocation and the ledger

Slot assignment is **allocated, persisted and never recomputed**. It lives in the world save:

```
<world>/data/lepus/ledger.json
```

```jsonc
{
  "formatVersion": 1,
  "pool": { "1": 1024, "16": 128, "64": 64 },        // sizes this world requires
  "entries": [
    {
      "logicalId": "lepus:wizardry.magic_block",
      "bedrockId": "wizardry:magic_block",
      "kind": "block",
      "slot": { "sizeClass": 16, "index": 55 },       // -> lepus:block_16/0037
      "stateSchema": [
        { "name": "wizardry:charge", "type": "int",  "values": [0, 1, 2, 3] },
        { "name": "wizardry:lit",    "type": "bool", "values": [false, true] }
      ],
      "stateSchemaHash": "sha256:…",                  // canonical JSON of stateSchema, SC-000 §6
      "previousSchemas": [],
      "packUuid": "…", "packVersion": "1.2.0",
      "firstSeen": "2026-07-31T04:11:09Z",
      "lastSeen":  "2026-07-31T04:11:09Z"
    }
  ]
}
```

Rules:

1. A `logicalId` present in the ledger **MUST** keep its slot, forever, even while its pack is
   absent. Slots are never reused, never compacted, never auto-freed.
2. New content takes the lowest free index in the smallest adequate size class, iterating content in
   ascending `logicalId` order so allocation is deterministic.
3. Content needing a size class that is full is **not bound**. It emits `SCE-4010` naming the class,
   how many more slots are needed and the exact config change, and its blocks are unavailable until
   restart. Everything else in the pack still loads.
4. Entities and items have **no** ledger entries — they need no slots. The ledger records blocks
   only, plus the placeholder records in §7.
5. The ledger is written atomically (temp file, fsync, rename) after every change, and a backup of
   the previous version is kept as `ledger.json.bak`.

### 6.4 Schema drift

If a bound block's `stateSchemaHash` changes — the pack added, removed or reordered a Bedrock state:

- the new schema is recorded, the old one is appended to `previousSchemas[]`, and `SCE-4011` is
  emitted with a readable diff;
- if the new state count still fits the bound size class, the slot is **kept** and existing blocks
  are **remapped**: each stored index is decoded with the old schema and re-encoded with the new
  one, matching states by name and values by value. States that disappeared are dropped; states that
  appeared take their first declared value;
- if it no longer fits, a new slot in a larger class is allocated, the old slot is retained as a
  placeholder that remaps on read, and `SCE-4012` is emitted.

Remapping happens lazily, per chunk, on load — never as a world-wide sweep. A chunk records the
ledger revision it was last remapped against.

*Why this matters:* under a naive design, an add-on author adding one block state to a block silently
scrambles every placed copy in every world. The recorded schema is what makes that recoverable, and
it must exist from the first release or it can never be added retroactively.

## 7. Detaching a pack

Disabling or removing a pack **MUST NOT** destroy anything (constitution rule 5).

| Content | Behaviour while its pack is absent |
|---|---|
| Block | The slot stays bound. The pool block renders as a clearly-marked placeholder, is not breakable by normal means, keeps its state index, drops nothing, and has no behaviour. Restoring the pack restores it exactly. |
| Item | The stack keeps its `custom_data` verbatim. It renders as a placeholder and its tooltip names the required add-on. It cannot be used, crafted with, or consumed. |
| Entity | Becomes invisible, immobile, non-ticking, invulnerable and non-collidable. NBT is preserved untouched. |

Placeholders are **never pruned automatically**. `/lepus prune <logicalId>` removes a ledger
entry and frees its slot, and requires a typed confirmation naming the content.

Because items and entities carry their identity in data rather than a registry entry, they need no
placeholder registration at all — the carrier is already there. Only blocks need the ledger to hold
their slot.

## 8. Per-world activation

Packs are installed **per instance** and activated **per world**, matching Bedrock's model as
closely as Java allows.

```
<gamedir>/lepus/addons/            installed packs, shared by every world
<world>/data/lepus/active.json     which packs this world uses, and in what order
<world>/data/lepus/ledger.json     §6.3
```

`active.json` records an ordered list of `{packUuid, version}`. Order is authoritative for SC-100 §5
override precedence.

Activation and deactivation take effect **immediately**, without a restart:

| Step | What happens |
|---|---|
| 1 | The new active set is resolved and parsed into a fresh `AddonIr` (SC-110). Parse errors abort the change and leave the previous set live. |
| 2 | Blocks are bound to slots (§6.3). Content that cannot be bound is reported and skipped. |
| 3 | Every live reference is swapped atomically at a tick boundary: block behaviour, item behaviour, entity components and goals, virtual data pack, virtual resource pack. |
| 4 | Loot tables, recipes and tags reload through vanilla's own reload path. |
| 5 | The sideband announces the change; clients rebuild their local bindings and re-render loaded chunks (SC-270 §8). |
| 6 | Newly-orphaned content becomes a placeholder (§7); newly-restored content is un-placeholdered. |

Commands: `/lepus pack list | enable <id> | disable <id> | order <id> <n> | reload | prune`.
A world-creation screen and an in-game screen offer the same operations; a dedicated server exposes
only the commands.

### 8.1 What still needs a restart

Exactly one thing, and it **MUST** be reported with numbers rather than as a generic failure:

> Pool class 64 is full (64/64 slots used). `nature_pack` needs 3 more.
> Set `lepus.blockPool.64 = 96` in `config/lepus.json` and restart. `SCE-4010`

Nothing else. Adding, removing, reordering, updating or downgrading packs, and every kind of content
other than blocks, is live.

## 9. Clients

The client runs the same pool and the same carrier item and entity types, but **binds slots
independently**: its bindings are session-scoped, rebuilt from the sideband handshake, and never
persisted, because the client stores no chunks. Client and server slot assignments therefore need
not agree, and no negotiation occurs.

A client missing a pack the server has is offered a download (SC-270 §9) and rebinds live. It does
**not** need to restart, because it has no ledger to honour — this is the one place the client has
an easier job than the server.

## 10. Config

```jsonc
{
  "blockPool": { "1": 1024, "2": 256, "4": 256, "8": 128, "16": 128, "32": 64,
                 "64": 64, "128": 32, "256": 32, "512": 16, "1024": 8, "4096": 4 },
  "subpackMemoryTierCeiling": null // SC-100 §7; null = highest the pack offers
}
```

`blockPool` is the whole answer: there is no `blockPoolAutoGrow`, because there is no automatic
growth to switch off (§6.2). A missing config file is written with these defaults, so the line
`SCE-4013` and `SCE-4010` ask an operator to change is always present and always visible. A
**malformed** config falls back to the defaults and is **not** overwritten — somebody who broke
their JSON wants it back, not replaced.

## 11. Diagnostics allocated here

`SCE-2020` vanilla entity tag cannot be granted · `SCE-2021` spawned entity retains its old spawn
category · `SCE-3020` pool entity summoned without a Bedrock identity · `SCE-4001` identifier
collision · `SCE-4010` pool class exhausted · `SCE-4011` schema drift, remapped · `SCE-4012` schema
drift, reallocated · `SCE-4013` a loaded world's ledger references slots outside the registered pool
· `SCE-4014` ledger unreadable, backup used · `SCE-4015` chunk remapped against a newer ledger
revision.

## 12. Testing contract

1. **Round-trip** — bind a block, place it, save, reload, assert the state survives.
2. **Detach/attach** — place custom blocks, items and entities; disable the pack; assert placeholder
   behaviour and that NBT is byte-identical; re-enable; assert full restoration.
3. **Schema drift** — place blocks, change the pack's state list in every direction (add, remove,
   reorder, widen a value list), assert the remap is correct for each.
4. **Determinism** — same packs, clean world, two runs: identical ledgers.
5. **Exhaustion** — fill a size class; assert the diagnostic names the right class and count, that
   the rest of the pack loads, and that nothing is corrupted.
6. **Pool growth** — a world whose ledger exceeds the config loads correctly with auto-grow on and
   fails cleanly with it off.
