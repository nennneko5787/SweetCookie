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

**There is no `ConfigScreenProvider` service.** An earlier revision of this section specified one.
It is not needed, because **both loaders pull**: ModMenu calls the `modmenu` entry point and NeoForge
reads the `IConfigScreenFactory` extension point, so nothing ever asks for a screen through an
interface. And the screen class has **one name across both version directories** (SC-220 §3), so
shared code that did want to construct one simply can. SC-230 §2 rule 7 says to add an interface
only when a method does not fit an existing one; this one fitted nothing because there was no call.

ModMenu is `modCompileOnly` and its version is per Minecraft version, because it tracks them one
major line each. The versions were read out of each jar's own `fabric.mod.json` rather than inferred:

| ModMenu | declares |
|---|---|
| `17.0.0` | `minecraft >=1.21.11 <26` |
| `20.0.0-beta.2` | `minecraft >=26.2-` |

`ModMenuApi.getModConfigScreenFactory` and `IConfigScreenFactory.createScreen` are identical across
both versions of each, so only the dependency coordinate diverges, not the integration code.

The NeoForge extension point is registered **only on a physical client**: it returns a `Screen`, and
touching that class on a dedicated server would pull client rendering into a process that has none.

The screens themselves are built in `common/` from version-free widget descriptions; only
construction and registration are per loader. Whether to depend on **Cloth Config** is
`TODO(SC-280)` — it is the Fabric convention and would save real work, but it is a third-party
dependency on the client and would need its own NeoForge story.

### 3.1 The version axis is the expensive one, not the loader axis

Measured against both merged jars rather than assumed:

| | 1.21.11 | 26.2 |
|---|---|---|
| screen drawing | `Screen.render(GuiGraphics, int, int, float)` | **`Screen.extractRenderState(GuiGraphicsExtractor, int, int, float)`** |
| text | `GuiGraphics.drawCenteredString(...)` and friends | **absent from `GuiGraphics` entirely** |

This is the same submission-based rewrite as the block and entity render path (ADR-0010), applied to
the UI. It is **not** a rename: the two versions do not share a rendering model for screens, so a
screen written against either cannot compile against the other.

That is what makes §3's "version-free widget descriptions" load-bearing rather than tidy. The
description layer — what rows exist, what each says, which are toggles — is version-free and testable
headlessly (§7); a small per-version backend turns descriptions into pixels and lives in a
per-version source directory (SC-220 §3), because the divergence is far past the five-line budget for
a `//?` comment.

**The description layer comes first, and is useful before any backend exists**: rendered as text it
is exactly what §1's development loop needs — enable, watch it fail, read why.

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

### 5.1 Three things Java Edition's pack screen makes a user guess

This screen is measured against the one it sits next to, and Java Edition's resource-pack screen
loses on all three counts. Each is a requirement, not a preference:

| | Java Edition | required here |
|---|---|---|
| **which end of the order wins** | stated nowhere; a user who assumes wrong silently gets the other pack's content and has nothing to read that would tell them | the rule is written **in the section heading**, repeated in every confirmation message, and carried in `active.json` as a `_comment` for whoever edits it by hand |
| **what a pack contains** | a name and an icon | counts of what the pack provides, per pack |
| **why a pack is doing nothing** | silent | a severity badge on the row and its diagnostics quoted in full underneath |

### 5.2 Reordering is keys, not dragging

Java Edition reorders by dragging a pack between two lists. **This is a keyboard interaction here**:
arrow keys move a selection, one labelled key per action acts on it, and the available keys are drawn
**under the selected row** rather than in a legend.

A drag needs a mouse, needs the pack and its destination visible at once, and reports nothing about
where the pack landed. Keys work on a list longer than the screen, and every action ends in a message
naming the pack's new position and the count it is out of. Mouse support may be added; it may not
replace this.

Two consequences are normative:

- **the selection must never leave the screen** — the list scrolls to follow it, by the least it can;
- **an action that cannot apply is not offered** — the last pack has no "raise", rather than a
  "raise" that does nothing. A key that does nothing is a key a user presses twice before concluding
  the screen is broken.

### 5.3 A client that is not running the world says so

Activation is world state, and a client connected to a remote server has not been told it. Such a
client **must not render the installed list as though everything were disabled** — that is a
confident wrong answer. It shows what is installed on its own disk, says the server decides which of
them this world uses, and offers no enable/disable controls until SC-270 §9's handshake gives it the
real answer.

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

### 7.1 The screen calls the command by sending it

A screen action carries the command string that performs it, and the screen runs the action by
sending that string as a command. Not a new packet, and not a shared method call.

This is what makes §7's layering real rather than aspirational — there is no second path to enabling
a pack — and it settles four things at once: no new wire format to version, the same permission check
as typing the command (so a non-operator's screen refuses exactly where their keyboard would),
nothing required of the server but SweetCookie, and **no interaction with ViaVersion at all**, since
a chat command is vanilla traffic and SC-270's invariant is untouched.

It also gives the text backend something exact to print. `/sweetcookie packs` on a headless server
prints each row's commands under it, so an operator is told what to type rather than that reordering
exists.

The screen **rebuilds its description on a timer, not after sending**. The command is answered on
another thread; redrawing immediately shows the state the action was about to change.

### 7.2 Where the description layer lives

Everything above the pixels — what rows exist, what each says, which can be acted on, where the lines
go, what the keys do — is in `core/` and is Minecraft-free. Only the call that turns a laid-out line
into pixels is per version (§3.1).

This is what the testing contract above requires rather than merely permits: 26.2 replaced the screen
rendering model outright, so a test that needed a `Screen` would have to be written twice and run on
a client. The line list and the key handling are plain JUnit, in seconds, covering both versions at
once.
