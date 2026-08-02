# SC-230 — Platform services

**Status:** complete · **Since:** 0.1.0 · **Supersedes:** —

How loader-independent code reaches Fabric- and NeoForge-specific behaviour. Companion to SC-220,
which covers the *version* axis; this covers the *loader* axis. They are separate axes and mixing
them is a defect.

---

## 1. Mechanism

A hand-rolled `ServiceLoader`. No Architectury runtime dependency.

```java
public final class Services {
    public static <T> T load(Class<T> type) { … }   // exactly one provider, or fail
}
```

*Why not Architectury API:* it forces users to install a second mod, and it couples our release
cadence to a third party's — the project would be blocked whenever Architectury lags a Minecraft
drop. *Why not `@ExpectPlatform`:* it requires the Architectury Gradle plugin, the same coupling
without the runtime one. The cost of doing it by hand is one interface, two implementations and one
`META-INF/services` file per hook, which is boring and never blocks a release. This is what the
mainstream multi-loader template does in 2026.

## 2. Rules

1. **Service interfaces live in `src/main/java`**, which every node compiles. Implementations live
   in `src/fabric/java` and `src/neoforge/java`, which only the matching nodes compile (SC-220 §3).
   There are no per-loader subprojects.
2. **Exactly one provider per interface per platform.** Zero providers is a fatal startup error
   naming the interface (`SCE-6001`); two is also fatal (`SCE-6002`). Both fail loudly at init — a
   missing platform hook discovered at world load is a much worse failure.
3. **Services are resolved once, eagerly, at mod init**, into `static final` fields. No lazy
   resolution on a hot path, and no service lookup during rendering or ticking.
4. **Service interfaces are version-free.** A method whose signature is version-conditional cannot be
   called from version-free code. Version divergence inside an implementation is SC-220's problem.
5. **No loader types in a service signature.** No `net.fabricmc.*`, no `net.neoforged.*`. If an
   interface needs a loader concept, the concept is wrong and needs an abstraction of its own.
6. **Services are stateless** unless the interface says otherwise. State lives in `common/`.
7. **A service interface is not a dumping ground.** One coherent responsibility each; when a method
   does not fit any existing interface, add an interface rather than widening one.

## 3. The service set

The complete list. Adding one is a normal change; it needs an entry here and an implementation on
both loaders in the same commit.

| Interface | Responsibility |
|---|---|
| `PlatformInfo` | loader name and version, physical side, dev environment, game and config directories |
| `RegistryBootstrap` | register the carrier item, the fixed entity types and the block slot pool at the correct init phase (SC-120) |
| `PackFinderInstaller` | install the virtual data pack and virtual resource pack as a `RepositorySource` |
| `NetworkChannel` | register the `lepus:` plugin channels, send and receive, per-connection |
| `PacketEncodeHooks` | the encode-time carrier substitution points (SC-270); backed by mixins |
| `LifecycleHooks` | server start/stop, world load/unload, `/reload`, tick phases, client connect/disconnect |
| `CommandRegistrar` | register `/lepus` |
| `CreativeTabHooks` | build and refresh Lepus creative tabs |
| `ConfigScreenProvider` | expose the settings and add-on management screens through ModMenu (Fabric) and `IConfigScreenFactory` (NeoForge); both soft dependencies *(client)* — SC-280 |
| `EntityRendererRegistrar` | bind the pool entity types to their renderer *(client)* |
| `ClientTickHooks` | frame and client tick callbacks *(client)* |
| `ResourceReloadHooks` | participate in client resource reload *(client)* |
| `ConfigDirectory` | resolve config and addon directories |

Client-only interfaces are resolved only on the physical client; requesting one on a dedicated
server is a programming error and throws.

## 4. Registration order

`RegistryBootstrap` runs at each loader's earliest registration phase and **must** complete before
world load. Order within it is fixed and normative, because slot allocation depends on it (SC-120):

1. carrier item
2. entity types, in ascending name order
3. block pool, ascending by size class then index

Registration order **MUST** be deterministic and identical between loaders. It is not observable on
the wire (SC-270), but it *is* observable in the block-state palette and therefore in save size and
in debug output, and non-determinism there makes bug reports unreproducible.

## 5. Networking

`NetworkChannel` abstracts the loaders' different payload registration.

- Channels are registered in `RegistryBootstrap`.
- Payloads are defined in `common/` as records with explicit reader/writer functions. **No loader
  codec types in `common/`.**
- Every inbound payload is size-capped and validated before use; an add-on-driven protocol carries
  attacker-controlled data (SC-260).
- Handlers are dispatched onto the correct thread by the implementation, never by the caller.

## 6. Mixins

Mixins are loader-module code (SC-220 §4) and are the backing for `PacketEncodeHooks` and
`LifecycleHooks` where no loader API suffices. A mixin **MUST** delegate to `common/` immediately;
logic inside a mixin is logic maintained twice.

`PacketEncodeHooks` failing to install is fatal, not degraded: without it, custom block states leak
onto the wire and every ViaVersion guarantee in SC-270 is void.

## 7. Testing contract

- A `common/` unit test asserts every interface in §3 has a provider on each loader, by scanning the
  built jars' `META-INF/services`.
- `core/testkit` provides a headless test implementation of the whole service set, so that logic in
  `common/` is testable without a running game.
- A conformance case asserts registration order is identical on both loaders by diffing the
  resulting block-state palette.

## 8. Diagnostics allocated here

`SCE-6001` no provider for a platform service · `SCE-6002` multiple providers ·
`SCE-6003` client-only service requested on a dedicated server ·
`SCE-6004` a mixin required by a service failed to apply.
