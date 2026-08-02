# SC-270 — Version-independent wire protocol

**Status:** complete · **Since:** 0.1.0 · **Supersedes:** —

How custom content reaches a client. The short version: it does not travel as custom content.

**This document governs a network protocol. Changes require an ADR and a protocol version bump.**

---

## 1. The invariant

> **Lepus content MUST NOT occupy a vanilla network registry identifier, and MUST NOT appear
> in vanilla dynamic-registry synchronisation.**
>
> On the wire, every custom block, item and entity is a **vanilla carrier**. Its real identity and
> state travel over the `lepus:` plugin channel, addressed **by name**.

Constitution rule 6. Everything in this document follows from it.

## 2. Why

### 2.1 ViaVersion is the forcing function

ViaVersion and ViaBackwards rewrite packets between Minecraft protocol versions using generated
tables of **vanilla** registry identifiers. Mod-added entries sit above the vanilla range, so they
either fall through unmapped or collide. ViaFabric's own README states it plainly: it "probably will
not work with modded registry entries or registry synchronization".

Worse, the failure is not graceful. Via's changelog history is a list of cases where an unknown or
malformed component payload **kicked the client** rather than degrading — item component hashing,
entity/block-entity data in items, sound ids inside instrument components. A mod that puts custom
data on the wire and hopes Via copes is a mod that disconnects people.

The requirement is that **a client running Lepus behaves identically whether or not its
Minecraft version matches the server's**. There is exactly one way to get that: give Via nothing to
get wrong.

### 2.2 What else falls out

Choosing the invariant for Via's sake pays for itself several times over:

- **Entity metadata index drift disappears.** Metadata layouts shift between versions and Via
  re-indexes them per entity type. Custom entity state never uses `SynchedEntityData`, so there is
  nothing to re-index.
- **Registry-sync kicks become impossible.** Nothing custom is in the sync.
- **Client and server registries need not agree**, which is what makes SC-120's whole
  no-registry-entry design — and therefore runtime pack attach/detach — possible at all.
- **Clients without the mod degrade instead of breaking.** They see a plain vanilla world. Not
  supported in 0.x, but the seam exists.
- **Geyser is unaffected**, because it reads server-side objects before encoding (§10).

## 3. Layers

Three distinct identifier layers. Confusing them is the standard failure mode.

| Layer | Example | Scope |
|---|---|---|
| **Logical identity** | `lepus:wizardry.magic_block` | global, derived (SC-120 §3) |
| **Storage slot** | `lepus:block_16/0037` + index 55 | one world's ledger (SC-120 §6) |
| **Session handle** | `int 41` | one connection, negotiated at handshake (§5) |
| **Wire carrier** | `minecraft:stone` | one packet |

The sideband speaks in **session handles**, which resolve to **logical identities**. Storage slots
never leave the server. Carriers never leave the packet.

## 4. Carriers

Every IR definition gets a `vanilla_fallback` computed once at translation time, from
`material_instances` colours, `minecraft:map_color`, `runtime_identifier`, the item's category, and
the entity's class family. It is chosen to be visually and physically plausible so that a client
which never applies the overlay still sees something sane.

| Content | Carrier | Chosen for |
|---|---|---|
| Block | a vanilla `BlockState` | similar colour, opacity, collision, and light behaviour |
| Item | a vanilla `Item` + `minecraft:custom_data` | similar category and stack size |
| Entity | a vanilla `EntityType` | similar size and class (mob / projectile / object) |

Carriers **MUST** exist in every supported protocol version. The permitted set is a fixed list of
blocks, items and entity types present from 1.21 through 26.2, recorded in
`spec/schemas/ir/carrier-set.json` and asserted by a conformance case. Picking a carrier that a
downgrade target lacks recreates exactly the problem this document exists to avoid.

### 4.1 Item carriers and `custom_data`

Items are the one case where a carrier can transport meaningful data by itself.
`minecraft:custom_data` is an opaque NBT compound that has existed since 1.20.5 and that Via
converts to and from pre-component root NBT reliably. It is the **only** component Lepus may
place custom data in.

```jsonc
"minecraft:custom_data": { "lepus": { "h": 41, "v": 1 } }
```

`h` is the session handle, `v` the payload version. The full stack state arrives over the sideband;
the handle is embedded in the stack so that a stack moving through vanilla inventory mechanics —
containers, hoppers, drops, shulker boxes — carries its identity without the server having to track
positions.

Custom data **MUST NOT** be placed in any other component, and Lepus **MUST NOT** define its
own component type. A mod-namespaced component has no downgrade path and is a kick waiting to happen.

## 5. Handshake

On login, before any world data:

```
S → C   lepus:hello        protocolVersion, serverMcVersion, features[]
C → S   lepus:hello_ack    protocolVersion, clientMcVersion, packs[{uuid, version, sha256}]
S → C   lepus:session      handles[], missingPacks[], activeSet
```

- **Protocol version is Lepus's own** and is independent of Minecraft's. A mismatch outside
  the supported range disconnects with a readable message naming both versions (`SCE-5001`).
- `lepus:session` interns every logical identity the active pack set defines into a dense
  `int` handle space, in ascending logical-identity order so it is deterministic and diffable.
  Handles are **session-scoped**: not persisted, not stable across reconnects, meaningless elsewhere.
- Handle 0 is reserved for "no custom content".
- Interning is what keeps the sideband small: a block overlay carries `int` handles, not strings.

A client that does not answer `lepus:hello` within the configured window is treated as
vanilla and, in 0.x, disconnected with an explanatory message (`SCE-5002`). The seam for supporting
it properly is §11.

## 6. Blocks

Chunk sections and block updates are rewritten at encode time: every custom `BlockState` becomes its
carrier. The client then applies a **sparse overlay**.

```
S → C   lepus:chunk_overlay
        chunkX, chunkZ, ledgerRevision,
        sections[ { sectionY, palette[handle…], bitsPerEntry, data[…] } ]
```

- Only sections containing at least one custom block appear. A chunk with none sends no overlay.
- The section encoding mirrors vanilla's palette-plus-packed-array format so the client's existing
  bit-unpacking is reusable and the size is comparable to a vanilla section.
- `lepus:block_update` carries `(pos, handle, stateIndex)` and is sent alongside the vanilla
  update for the carrier, in the same tick, ordered after it.
- The client stores the overlay beside its `ClientLevel` and binds each handle to its own local pool
  slot (SC-120 §9), then re-meshes the affected sections.

**Ordering is normative.** The carrier packet **MUST** precede its overlay, and the client **MUST**
tolerate a carrier with no overlay (rendering the carrier) for at most one tick before treating it
as genuinely vanilla. Otherwise a dropped overlay leaves a permanently wrong block.

`ledgerRevision` lets the client detect that the server rebound slots — after a pack change — and
request a resend rather than render stale content.

## 7. Items

The carrier stack already contains its handle (§4.1). The sideband supplies everything else:

```
S → C   lepus:item_state   handle, componentsBlob
```

sent once per handle per session, not per stack. Per-stack mutable state (damage, custom name,
container contents) rides in vanilla components on the carrier where a vanilla equivalent exists,
and in `custom_data` where it does not.

*Why not send the whole item state per stack:* an inventory of 36 custom stacks would multiply the
payload by 36 for data that is identical across them.

## 8. Entities

Custom entity state **MUST NOT** use `SynchedEntityData` (constitution rule 6). It travels here:

```
S → C   lepus:entity_bind    entityId, handle
S → C   lepus:entity_state   entityId, propertiesDelta, molangVarsDelta, animStateDelta
```

- `entity_bind` is sent immediately after the vanilla spawn packet for the carrier type.
- `entity_state` is delta-encoded against the last acknowledged state, on the entity's own update
  interval rather than vanilla's.
- Entity properties (SC-160), Molang `variable.*` visible to the client, and animation-controller
  state are the three payloads. Nothing else about a custom entity is client-visible.

Position, velocity, rotation, passengers and effects continue to use **vanilla** packets on the
carrier entity, because Via handles those correctly and reimplementing them would be both wasteful
and worse.

## 9. Pack distribution

The handshake tells the server which packs the client lacks. The server offers them:

- **Preferred:** an operator-configured HTTP(S) base URL; the client fetches by
  `{uuid}/{version}.mcpack` and verifies the sha256 from the handshake.
- **Fallback:** a chunked transfer over `lepus:pack_data`, rate-limited, off by default on
  dedicated servers because it is expensive.

The client writes the pack into `<gamedir>/lepus/addons/`, binds it live (SC-120 §9) and
continues. **It does not restart** — a client keeps no ledger, so it has nothing to honour.

Integrity is mandatory: a pack whose sha256 does not match the handshake is discarded with
`SCE-5010`. A pack is never executed as a consequence of joining a server without matching the hash
the server declared.

## 10. Geyser

Geyser translates **server-side objects**, before this document's encode-time substitution applies to
Java clients. It therefore sees the real custom block, item and entity, and maps them through
`geyser-api` (SC-210). No interaction with carriers, no interaction with the sideband, no special
case in either direction.

This is a consequence of doing the substitution at packet-encode time rather than in the world
model, and it is one of the reasons for that choice.

## 11. Degradation

| Situation | Behaviour |
|---|---|
| Client has Lepus, same MC version | full fidelity |
| Client has Lepus, different MC version, via Via | **full fidelity — identical to the above** |
| Client has Lepus, missing packs | fetched live (§9), then full fidelity |
| Client has Lepus, refuses the packs | carriers only, clearly marked, playable |
| Client has no Lepus | 0.x: disconnected with an explanation. The carrier layer already makes the alternative possible; it is a policy decision, not a technical gap. |

## 12. One transport, not two

An obvious optimisation is to skip the sideband when client and server run the same Minecraft
version and use native registry identifiers instead. **This is prohibited in 0.x.**

Two transports means one of them is exercised rarely, and the rarely-exercised one is the one that
breaks — which is precisely the Via path this document exists to protect. The sideband is a superset;
it is used always. Native-identifier optimisation may be revisited once the sideband has a
conformance suite that proves equivalence, and it needs an ADR.

## 13. Testing contract

The normative definition of "works through Via" is an **equivalence test**, not a smoke test. The
same scripted scenario — place custom blocks, put custom items in a container, spawn custom
entities, mutate their properties — runs in three configurations:

| | Server | Client |
|---|---|---|
| (a) | 26.2 | 26.2 |
| (b) | 26.2 | 1.21.11 through ViaBackwards |
| (c) | 1.21.11 | 26.2 through ViaVersion |

Each client records a **state trace**: the logical identity and Bedrock state of every block it
believes exists, of every item stack, and of every entity's properties and Molang variables.

**All three traces MUST be byte-identical.** Any divergence is a defect in this document's
implementation, not an acceptable version difference.

Additional cases: overlay lost in transit (recovers within one tick), pack change mid-session
(clients rebind, no restart), handle exhaustion, carrier absent from the downgrade target (caught at
build time by the carrier-set assertion), and a hostile server offering a pack whose hash does not
match.

## 14. Diagnostics allocated here

`SCE-5001` protocol version mismatch · `SCE-5002` client did not complete the handshake ·
`SCE-5003` overlay arrived without its carrier · `SCE-5004` unknown session handle ·
`SCE-5005` ledger revision mismatch, resend requested · `SCE-5010` pack hash mismatch ·
`SCE-5011` pack transfer failed · `SCE-5012` sideband payload exceeded the size limit.
