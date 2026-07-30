# SC-210 — Geyser mirror contract

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

How Bedrock clients connecting through Geyser see SweetCookie content. This is the case the project
exists to serve as much as the Java one: on a Geyser server, the players the add-on was written for
are already connecting.

---

## 1. What Geyser does and does not do

**Geyser does not support behavior packs, and says so.** Its own documentation: executing them
"would require modifications on the Java server side, which isn't possible when Geyser is used on a
proxy." That is precisely the gap SweetCookie fills — SweetCookie owns behaviour on the Java server;
Geyser owns the Bedrock client's presentation.

Geyser does provide, as of 2.11.0:

| API | Event |
|---|---|
| Custom items | `GeyserDefineCustomItemsEvent` |
| Custom blocks | `GeyserDefineCustomBlocksEvent` |
| Custom entities | `GeyserDefineEntitiesEvent` *(experimental)* |
| Entity properties | `GeyserDefineEntityPropertiesEvent` |
| Resource packs | `GeyserDefineResourcePacksEvent`, `SessionLoadResourcePacksEvent` |
| Spawn interception | `ServerSpawnEntityEvent` |

## 2. Decisions already taken

**`geyser-api` is a `compileOnly` soft dependency.** SweetCookie works without Geyser and must never
fail to load because Geyser is absent or a different version.

**The whole bridge sits behind one interface**, `GeyserBridge`, with a no-op implementation.
Resolution is reflective and tolerant: a `NoSuchMethodError` or `ClassNotFoundException` degrades to
no-op with a diagnostic, never a crash. Geyser's custom-entity API is `@ApiStatus.Experimental` and
its block components are documented as unstable, so this is not defensive programming for its own
sake.

**The add-on's own resource pack half is served to Bedrock clients unmodified.** This is the single
highest-value, lowest-cost part of the integration: a `.mcaddon`'s RP is *already* exactly what a
Bedrock client wants. `ResourcePack.create(PackCodec.path(…))` and it is done — no conversion, no
translation, no fidelity loss. Bedrock clients get the original geometry, animations, render
controllers and particles the author shipped.

**Geyser is unaffected by SC-270.** Carrier substitution happens at Java packet-encode time; Geyser
reads server-side objects before that, so it sees real custom content. No special case in either
direction.

## 3. The mirror

| SweetCookie content | Bedrock representation |
|---|---|
| Custom item | `NonVanillaCustomItemDefinition` — our carrier is `sweetcookie:item`, which has no vanilla base, so the non-vanilla builder is the correct one |
| Custom block | `CustomBlockData` with components and permutations built from the same IR that drives the Java side |
| Custom entity | `CustomEntityDefinition` plus `GeyserDefineEntityPropertiesEvent`; the RP already defines its geometry and animations |
| Entity properties | mirrored as Geyser entity properties, subject to its caps |
| Resource pack | the add-on's RP, served verbatim |

Because both sides derive from one IR, the Java and Bedrock representations cannot drift — which is
the main argument for doing the mirror at all rather than shipping a separate Geyser extension.

## 4. Constraints to design around

- **Entity properties: at most 32 per entity type, 16 enum values, 32-character names.** Bedrock
  entity definitions routinely exceed this. Sections to write: the selection rule for which
  properties are mirrored, and the diagnostic when some are dropped.
- **Custom blocks are unstable** by Geyser's own admission; Mojang churns Bedrock block components
  between releases.
- **Custom entities are experimental**, with no JSON mapping path yet.
- **Proxy deployments.** Geyser on a separate proxy cannot see our server-side objects at all. Then
  the RP is still servable (via `GeyserPackSync` or an operator-managed pack), but the item, block
  and entity mirrors are unavailable. `TODO(SC-210)`: whether to ship a Geyser *extension* for that
  topology, or document it as unsupported.

## 5. Ordering

Registration into Geyser happens after SweetCookie's own binding (SC-120) and before Geyser
completes initialisation. Pack activation changes at runtime (SC-120 §8) must propagate: Geyser's
registration events are lifecycle-scoped and may not re-fire, so `TODO(SC-210)` is whether Bedrock
clients need a reconnect after a pack change, and how to tell them so.

## 6. Degradation

| Situation | Behaviour |
|---|---|
| Geyser absent | no-op bridge; Java side unaffected |
| Geyser present, older API | mirror what resolves, diagnostic per unavailable event |
| Geyser on a proxy | RP only; §4 |
| A mirror registration fails | that content is unavailable to Bedrock clients only; Java is unaffected |

Nothing about Geyser may ever affect Java-side correctness. A Bedrock-client bug must not become a
Java-client bug.

## 7. Prior art worth reading

`GeyserMC/Hydraulic` (MIT) generates Bedrock packs from Java mod content — the exact inverse of this
document, and the best available map of what maps cleanly. `GeyserMC/Rainbow` (GPL-3.0) generates
Geyser item mappings and Bedrock resource packs from live Java data-component inspection; its
**output schema** is what we must emit, though its code is GPL and must not be copied.

## 8. Testing contract

Geyser integration cannot be unit-tested meaningfully, so: a manual checklist under
`spec/conformance/manual/geyser/` covering each content type from a real Bedrock client, plus
automated tests that the bridge degrades correctly when the API is absent or throws — which is the
part most likely to break in production.

## 9. Diagnostics allocated here

`SCE-6010` Geyser API unavailable · `SCE-6011` Geyser API version mismatch · `SCE-6012` mirror
registration failed · `SCE-6013` entity properties dropped, cap exceeded · `SCE-6014` proxy
deployment detected, mirrors unavailable.
