# SC-280 — Configuration and user interface

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —
**Priority:** normal — the management screen lands with the first content milestone, not after it.

Configuration file format, the in-game add-on management screen, and integration with the loaders'
mod-configuration entry points.

---

## 1. Why this is not cosmetic, and not deferrable

SC-120 §8 promises that packs attach, detach and reorder at runtime, per world, the way they do on
Bedrock. **A promise like that is only real if there is a screen.** On Bedrock the pack list is part
of world creation and world settings; a Java equivalent that exists only as chat commands has not
delivered the feature to a single-player user.

There is a second reason, and it is the one that decides the schedule: **this is a development
tool before it is a user feature.** Enable a pack, watch it fail, read the diagnostics, disable it,
edit a JSON file, reload, try again — that loop is the single most-repeated action while building
this mod, and every iteration of it runs through this screen. Building the content pipeline first
and the screen afterwards means paying for a worse loop during exactly the period when the loop
runs most often.

So the management screen is scheduled with the first milestone that loads a pack, not after the
content pipeline is complete. The *settings* screen — the thing ModMenu opens — is smaller and can
follow.

## 2. Two screens

| Screen | Purpose | Reached from |
|---|---|---|
| **Add-on management** | list installed packs, enable/disable per world, reorder, see per-pack diagnostics, import a file, open the add-ons folder | world creation, world settings, `/sweetcookie packs`, and the settings screen |
| **Settings** | block pool sizes, subpack memory ceiling, diagnostic verbosity, pack-download consent policy, performance toggles | ModMenu / NeoForge mod list |

The management screen is world-scoped and only meaningful with a world loaded or being created. The
settings screen is instance-scoped and always available.

## 3. Loader integration

A platform service (SC-230 §3), because the entry points differ:

| Loader | Mechanism |
|---|---|
| Fabric | ModMenu's `ModMenuApi` entry point, declared as `modmenu` in `fabric.mod.json`; **soft dependency** — absent ModMenu must not break anything |
| NeoForge | `IConfigScreenFactory`, registered as a mod extension point |

```java
public interface ConfigScreenProvider {          // client-only
    Screen settings(Screen parent);
    Screen addonManagement(Screen parent, @Nullable LevelStorageAccess world);
}
```

The screens themselves are built in `common/` from version-free widget descriptions; only
construction and registration are per loader. Whether to depend on **Cloth Config** is
`TODO(SC-280)` — it is the Fabric convention and would save real work, but it is a third-party
dependency on the client and would need its own NeoForge story.

## 4. Configuration file

`config/sweetcookie.json`, the shape sketched in SC-120 §10.

Requirements: comments preserved on rewrite, unknown keys preserved (the same discipline as SC-110
§5 — a user who downgrades must not lose settings), atomic writes, and a documented default for
every key.

`TODO(SC-280)`: whether server-side settings should be per world rather than per instance. Block
pool sizes clearly belong per instance, since they are decided before world load. Diagnostic
verbosity plausibly belongs per world.

## 5. What the management screen must show

Not just a list of checkboxes:

- pack name, version, author and icon, from the manifest;
- **what it provides** — counts of blocks, items, entities, recipes — because a user with a folder
  of `.mcaddon` files cannot otherwise tell them apart;
- per-pack diagnostic badges, linking to the SC-240 detail view;
- **the restart warning, with numbers**, when a change would exceed the block pool (SC-120 §8.1) —
  and nothing else may ever produce a restart warning;
- which packs the current world has enabled, distinct from which are installed;
- load order, reorderable, since it decides override precedence (SC-100 §5).

Drag-and-drop import of `.mcaddon` / `.mcpack` onto the screen is `TODO(SC-280)` but is close to how
users expect this to work.

## 6. Client-side pack acceptance

When a server offers packs the client lacks (SC-270 §9), the consent prompt lives here. Its policy
is an open security decision (SC-260 §5) and this screen is where the remembered decisions are
reviewed and revoked.

## 7. Testing contract

Screen construction is testable headlessly: build every screen with a synthetic pack set and assert
no exception and that the widget tree matches a golden description. Behaviour — that toggling a pack
actually enables it — is a T2 case against the underlying commands, since the screen is a thin
layer over them.

That layering is deliberate: **every management operation is a `/sweetcookie` command first**, and
the screen calls it. Dedicated servers get the full feature set with no client UI, and the screen
cannot drift from the command's semantics.
