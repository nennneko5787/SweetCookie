# ADR-0003 — Custom content never occupies a vanilla network registry identifier

**Status:** accepted
**Date:** 2026-07-30
**Affects:** SC-120, SC-160, SC-270, and every content document

## Context

The project requires that a client running SweetCookie behave **identically whether or not its
Minecraft version matches the server's** — that is, through ViaVersion and ViaBackwards.

ViaVersion rewrites packets using generated tables of **vanilla** registry identifiers. Mod-added
entries sit above the vanilla range and are not in those tables. ViaFabric's README states plainly
that Via "probably will not work with modded registry entries or registry synchronization".

The failure mode is worse than degradation. Via's changelog history is a list of cases where an
unknown or malformed payload **disconnected the client**: item component hashing, entity and
block-entity data inside items, sound identifiers in instrument components. Entity metadata is the
worst case, because index layouts shift per version and Via re-indexes them per entity type.

There is no way to make Via handle mod-added registry entries. The only lever available is what we
put on the wire.

## Decision

Custom content **never** occupies a vanilla network registry identifier and never appears in vanilla
dynamic-registry synchronisation.

On the wire, every custom block, item and entity is a **vanilla carrier**. Its real identity and
state travel over the `sweetcookie:` plugin channel, addressed by name and interned to session-scoped
handles. Custom entity state does not use `SynchedEntityData`.

Substitution happens at packet-encode time, so the server's world model still contains real custom
content.

Exactly one transport is used, always — including when versions match. Two transports means the
rarely-exercised one rots, and the rarely-exercised one would be the Via path this decision exists to
protect.

## Consequences

**Good, and more than expected.** Via works perfectly rather than not at all. Entity metadata index
drift becomes impossible. Registry-sync kicks become impossible. Clients without the mod see a plain
vanilla world instead of breaking, which leaves a seam for supporting them later. Geyser is
unaffected, because it reads server-side objects before encoding.

And — discovered after the fact, but decisive — **client and server registries no longer need to
agree**, which is what makes ADR-0007's runtime attach/detach possible at all. This decision turned
out to be load-bearing for a requirement that did not exist when it was made.

**Bad, and accepted.** Packet-encode interception via mixins at several sites, per Minecraft version,
which are the least stable targets in the project. A sparse chunk overlay costs bandwidth. A sideband
protocol needs its own versioning, its own validation and its own security review. The carrier set
must be restricted to blocks, items and entity types present in every supported protocol version.

## Alternatives considered

**Register custom content normally and accept that Via breaks.** Rejected: the requirement is
explicit, and the failure is disconnection rather than degradation.

**Detect Via and use a different code path.** Rejected: the untested path is the one that breaks, and
here it is the one that matters.

**Express everything as vanilla content with data components, PolyMc-style.** This is close to what
carriers do, but doing it in the world model rather than at encode time would mean the server no
longer holds real custom content, which breaks Geyser and complicates every behaviour hook.

## Reversal cost

**High.** The protocol is versioned, so it can evolve, but abandoning carriers entirely would mean
rewriting the client-side content pipeline and would forfeit ADR-0007.
