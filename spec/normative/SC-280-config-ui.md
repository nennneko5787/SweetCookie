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
| **Add-on management** | list installed packs, enable/disable per world, reorder, see per-pack diagnostics, import a file, open the add-ons folder | world creation, world settings, `/lepus packs`, and the settings screen |
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

`config/lepus.json`, the shape sketched in SC-120 §10.

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

Drag-and-drop import of a `.mcaddon` / `.mcpack` **file** onto the screen comes free with §5.2: it is
vanilla `PackSelectionScreen.onFilesDrop`, and it copies the file into the add-on directory.

### 5.1 Three things Java Edition's pack screen makes a user guess

This screen is measured against the one it sits next to, and Java Edition's resource-pack screen
loses on all three counts. Each is a requirement, not a preference:

| | Java Edition | required here |
|---|---|---|
| **which end of the order wins** | stated nowhere; a user who assumes wrong silently gets the other pack's content and has nothing to read that would tell them | the rule is written **in the section heading**, repeated in every confirmation message, and carried in `active.json` as a `_comment` for whoever edits it by hand |
| **what a pack contains** | a name and an icon | counts of what the pack provides, per pack |
| **why a pack is doing nothing** | silent | a severity badge on the row and its diagnostics quoted in full underneath |

### 5.2 Selection is Minecraft's own pack screen

**`PackSelectionScreen` is constructed directly, with the add-ons in it.** Not a copy of it, not
something that resembles it: available and selected columns, arrow buttons on hover, the search box,
the pack folder button, drag-and-drop — all of it is the screen the game ships, because it *is* that
screen.

This is normative, and it settles the interaction question completely: **nothing about selecting an
add-on can drift from selecting a resource pack, because there is no second implementation to
drift.** A user already knows how to use it, it gains every improvement Mojang makes to it, and the
work is a `PackRepository` rather than a list widget.

Two earlier revisions of this section specified a bespoke list — first keyboard-driven, then
drag-driven. Both were wrong for the same reason: they re-derived behaviour that shipped with the
game, and a pack list that had to be operated some other way is the surprising one.

#### 5.2.1 Two tabs, behaviour and resource

A **tab bar at the top switches between behaviour packs and resource packs.** Bedrock separates the
two everywhere it lists them, and an `.mcaddon` normally unpacks into one of each — a single list
shows every add-on as two adjacent rows with nothing to tell them apart, which is neither what
Bedrock does nor what Java Edition does.

- **each tab is a whole screen**, not a panel. Vanilla's `Tab` hosts widgets inside one screen and
  cannot host a `Screen`, which `PackSelectionScreen` is. Selecting the other tab opens the other
  screen, drawing the same bar in the same place with the other tab lit;
- **a pack that declares both module types appears in both tabs**, and enabling it in either enables
  the whole pack. There is one pack and one activation entry, not two halves; that is the truth
  about the file, so it is what the screen shows;
- **committing one tab must not disturb the other kind.** The tab knows only its own packs, so its
  selection is spliced into the activation in place — this kind's entries are rewritten, everything
  else keeps its position. Handing the tab's selection straight to the diff would read every pack of
  the other kind as deselected and disable all of them.

Making room for the bar is the **one** place the mod reaches into a vanilla screen's layout, and it
is done by measuring what `init()` produced rather than by assuming coordinates. A hard-coded offset
overlaps silently the moment the header changes, and an overlapping list is still clickable — the
failure would be a tab bar that cannot be pressed, not an exception.

Otherwise, what the mod supplies is only the data and the parts vanilla leaves blank:

- **the title carries the precedence rule.** It is the one string this screen takes from us, and
  Java Edition's own pack screen never says which end of the selected column wins (§5.1). Vanilla
  displays the highest priority at the **top**, so the title says so;
- **the description says what the pack provides**, and, in red, what is wrong with it. §5.1's second
  and third points, in the two lines vanilla already draws under a pack's name;
- **`PackCompatibility` is not repurposed** to mean "this pack has errors". Its values render as
  "made for a newer/older version of Minecraft", which is a false statement about a Bedrock pack
  that failed to parse.

#### 5.2.2 Committing is a diff

The screen hands back a whole selection; every management operation is a command (§7.1). The
translation **must be a diff**, not a replay:

- closing the screen unchanged sends nothing and writes nothing;
- moving one pack sends one command.

Replaying the list would put two lines of chat in front of a user per installed pack, and rewrite
the activation file once per step for changes nobody made.

The plan's steps are **disables, then enables, then moves**, and each move's position is counted
against the list as it will be by then. Disabling first shortens the list every later position is
measured against; enabling before moving means a newly enabled pack is present when its position is
set. Getting the order wrong lands packs one place out.

Because `getSelectedIds()` is lowest-priority-first — the same direction as the activation file —
no conversion happens anywhere. **This direction is verified against the jar, not assumed**: the
screen's model reverses on the way in and again on commit, and reading it backwards would silently
invert every user's overrides.

### 5.3 A client that is not running the world says so

Activation is world state, and a client connected to a remote server has not been told it. Such a
client **must not render the installed list as though everything were disabled** — that is a
confident wrong answer, and committing it would enable packs against a list nobody had seen.

So such a client does not get the selection screen at all. It gets a read-only view listing what is
installed on its own disk and saying the server decides which of them this world uses, until
SC-270 §9's handshake gives it the real answer.

## 6. Client-side pack acceptance

When a server offers packs the client lacks (SC-270 §9), the consent prompt lives here. Its policy
is an open security decision (SC-260 §5) and this screen is where the remembered decisions are
reviewed and revoked.

## 7. Testing contract

Screen construction is testable headlessly: build every screen with a synthetic pack set and assert
no exception and that the widget tree matches a golden description. Behaviour — that toggling a pack
actually enables it — is a T2 case against the underlying commands, since the screen is a thin
layer over them.

That layering is deliberate: **every management operation is a `/lepus` command first**, and
the screen calls it. Dedicated servers get the full feature set with no client UI, and the screen
cannot drift from the command's semantics.

### 7.1 The screen calls the command by sending it

A screen action carries the command string that performs it, and the screen runs the action by
sending that string as a command. Not a new packet, and not a shared method call.

This is what makes §7's layering real rather than aspirational — there is no second path to enabling
a pack — and it settles four things at once: no new wire format to version, the same permission check
as typing the command (so a non-operator's screen refuses exactly where their keyboard would),
nothing required of the server but Lepus, and **no interaction with ViaVersion at all**, since
a chat command is vanilla traffic and SC-270's invariant is untouched.

It also gives the text backend something exact to print. `/lepus packs` on a headless server
prints each row's commands under it, so an operator is told what to type rather than that reordering
exists.

The screen **rebuilds its description on a timer, not after sending**. The command is answered on
another thread; redrawing immediately shows the state the action was about to change.

### 7.2 What is testable, and where it lives

The selection screen itself is not ours to test — it is the game's, and it is exercised every time
anyone opens the resource-pack list. What **is** ours is the diff from a selection to a set of
commands (§5.2.1), and that lives in `core/` with no Minecraft in it: plain JUnit, in seconds, and
every case asserts that applying the plan actually reaches the requested order rather than only that
the steps look right.

The remaining description layer — the pool view, the ledger, the read-only list of §5.3 — is
likewise `core/`, with only the call that turns a laid-out line into pixels per version (§3.1). 26.2
replaced the screen rendering model outright, so anything that needed a `Screen` would have to be
written twice and run on a client.

### 7.3 The selection path is version-free but for the tab bar

Checked against both merged jars, signature by signature: `PackSelectionScreen`, `PackRepository`,
`RepositorySource`, `Pack`, `PackLocationInfo`, `Pack.Metadata`, `PackSelectionConfig`, `PackSource`,
`PackResources`, `IoSupplier`, `PackType`, `MetadataSectionType`, and `AbstractWidget`'s position
setters — **identical on 1.21.11 and 26.2, constructors included.** None of it is behind §3.1's
divergence, because the render rewrite went through `Screen`'s drawing and this path never draws.

**Building the tab bar is the exception**, and it is the whole of the exception:

| | 1.21.11 | 26.2 |
|---|---|---|
| two-argument builder | `TabNavigationBar.builder(TabManager, int)` | moved to `MenuTabBar.builder` |
| sizing | `setWidth(int)` then `arrangeElements()` | `arrangeElements(int)` |
| the bar | `AbstractContainerEventHandler` — not a widget, cannot be positioned | `AbstractContainerWidget` |

So the bar sits at the top on both, because on 1.21.11 that is the only place it can sit, and the
screen moves its own content down instead. Two things that would have diverged do not, because
vanilla absorbed them: `GridLayoutTab` is a concrete public `Tab` on both, so 26.2 adding
`Tab.getLayout()` costs nothing, and `TabButton` becoming abstract there would have mattered only if
tabs were added one at a time.

One loader divergence exists and is not a version one: **NeoForge adds a fifth component to
`Pack.Metadata` and deprecates the canonical constructor**; Fabric has only the four-argument form.
The four-argument form is therefore used on both and its warning suppressed at the single call site.
Deprecation warnings are enabled on both loaders' buildscripts, because this is what turned that up.
