# ADR-0007 — No Bedrock feature gets a Java registry entry

**Status:** accepted
**Date:** 2026-07-31
**Affects:** SC-120, SC-150, SC-160, SC-170

## Context

On Bedrock, an add-on is dropped into a world and toggled. That is the experience users are
comparing against, and a compatibility layer that is clumsier than the thing it is compatible with
does not get used.

Java freezes its registries before a world is selected. The obvious design — one `Block`, `Item` and
`EntityType` per Bedrock definition — makes every pack change require a restart, and makes packs
global rather than per-world. That was the original plan, with the restart accepted as unavoidable.

It stopped being unavoidable once ADR-0003 landed. Because custom content never occupies a vanilla
network registry identifier, **client and server registries no longer need to agree**, and the
registry stops being a synchronisation mechanism. It is then only a storage mechanism, and storage
requirements differ sharply per content kind:

- **Items** are stored as an identifier plus components. Since 1.20.5, stack size, durability, food,
  tool behaviour, rarity, name, model and equippability are all **data components** — per stack, not
  per `Item`. Almost nothing an item needs is baked into its registry entry any more.
- **Entities** are stored as a type identifier plus NBT. Pathfinding, movement, AI, hitbox,
  collision and attributes are all per instance; only `MobCategory`, tracking parameters and tag
  membership are baked.
- **Blocks** are stored in chunk palettes as a registry name plus property values. This genuinely
  cannot be reduced to data.

## Decision

**No Bedrock feature gets a Java registry entry named after it.**

| Content | Registrations |
|---|---|
| Items | one carrier `Item`; identity in `minecraft:custom_data` |
| Entities | a fixed set, one per (class family × `MobCategory`) — about sixteen |
| Blocks | a **pre-reserved pool** of anonymous slots, each a `Block` with a single opaque index property, bound to Bedrock blocks at runtime |
| Everything else | none |

Slot assignment is persisted in a per-world ledger, so it is stable across restarts without being
derived. Packs are installed per instance and activated **per world**, and activation takes effect
at the next tick.

The single documented exception is pool exhaustion: adding blocks beyond the reserved pool needs a
config change and a restart, reported with exact numbers.

## Consequences

**Good.** Packs attach, detach, reorder and update at runtime, per world — the Bedrock experience.
Schema drift becomes recoverable, because the ledger records the previous state schema and existing
blocks can be remapped rather than scrambled. Detaching a pack cannot destroy content, since the
carrier and the slot are still there. And the client needs no ledger at all, because it stores no
chunks, so it rebinds freely and never restarts.

**Bad, and accepted.** Reserved slots consume block-state palette space whether used or not — the
default pool roughly triples the global palette. Block identity in chunk storage is opaque, so
debug output and `/data` are unreadable without the ledger. A dedicated carrier item means vanilla
mechanics that key on item identity do not apply, which is mostly desirable but must be handled
deliberately. Custom entities cannot be members of vanilla entity tags, which is a permanent
interoperability limitation with other Java mods and data packs.

## Alternatives considered

**Unfreeze the registry with mixins and add real entries at runtime.** No pool limit, no waste,
readable identifiers. Rejected: it requires hand-maintaining every cache Minecraft derives from the
block registry — the block-state registry, tags, recipes, creative tabs — on two loaders, across
every version, forever. It is exactly the kind of thing that breaks on every Minecraft update, and
the failure mode is world corruption.

**Both: pool by default, unfreeze on exhaustion.** Rejected: it keeps the dangerous path permanently
present while making it rare, which is the worst combination — rarely exercised and catastrophic
when wrong.

**Accept a restart for blocks only.** The smallest amount of work, and it was the position before
this ADR. Rejected because block-adding packs are the common case, so "only blocks" would mean "in
practice, usually".

## Reversal cost

**High.** The ledger format and the pool layout are persisted in saved worlds. Changing the pool
shape needs a migration; abandoning the approach needs a full content migration.
