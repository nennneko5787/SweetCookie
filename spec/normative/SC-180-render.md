# SC-180 — Resource pack and rendering

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

The client half. Bedrock's resource pack is a general skeletal animation and dynamic model-selection
system; Java has hardcoded `ModelPart` hierarchies and code-defined renderers. This is effectively a
second renderer, and it is the largest client-side body of work in the project.

---

## 1. Decisions already taken

**There is no render abstraction for the geometry path.** Shared code calls
`SubmitNodeCollector.submitCustomGeometry` directly, because the signature is identical on both
supported versions. This replaces an earlier design built on a mistaken premise; ADR-0010 records
what was wrong and why.

**Version divergence exists elsewhere in rendering and is abstracted when first needed**, sized to
the actual difference (§2.1). Where an abstraction is introduced, it is designed against the newest
version's constraints, since a restrictive contract can be satisfied by a permissive backend and not
the reverse (SC-220 §2.2).

**Backends, where they exist, live in per-version source directories**, never inline `//?`
(SC-220 §3).

**Everything except the backends is version-free**: geometry traversal, bone matrices, animation
sampling, animation-controller state machines, render-controller evaluation, Molang, Snowstorm
simulation. Target: under 10 % of client code is version-split.

**Bedrock coordinate conventions are preserved in the IR** and converted once, here, where the
result can be checked against a rendered image (SC-110 §6.1).

## 2. Submission

Verified against both `minecraft-merged` jars, not against release notes:

```java
// net.minecraft.client.renderer.SubmitNodeCollector — IDENTICAL on 1.21.11 and 26.2
void submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer);

interface CustomGeometryRenderer {
    void render(PoseStack.Pose, VertexConsumer);
}
```

`RenderType`, `Identifier`, `PoseStack` and `VertexConsumer` are in the same packages on both
versions. `RenderTypes.entityCutout(Identifier)` and its siblings supply the layer.

Submission **deferred** vertex writing into a callback; it did not abolish it. That is the fact the
earlier design got wrong, and it happens to suit this project: Bedrock render controllers choose
geometry, texture and material per frame through Molang, so an upload-immutable-meshes API would
have fought the format continuously.

`src/main/java/.../client/render/BedrockCubeSubmitter.java` is the worked example and compiles
unchanged on all four nodes.

### 2.1 Where rendering actually diverges

| Path | 1.21.11 vs 26.2 |
|---|---|
| `submitCustomGeometry` | **identical** — the path this project lives on |
| `submitModelPart`, `submitModel` | differing overloads |
| `submitBlockModel` | `BlockStateModel` vs `List<BlockStateModelPart>` |
| `submitItem` | `BakedQuad` moved package |
| Particles | `submitParticleGroup(ParticleGroupRenderer)` vs `submitQuadParticleGroup(QuadParticleRenderState)` |
| `submitBlock` | removed in 26.2 |
| `submitNameTag` | one extra parameter in 1.21.11 |

`MultiBufferSource` still exists in 1.21.11 and is gone in 26.2, but nothing here needs it: the
renderer rewrite landed at 1.21.9, so both supported versions already have the submission API.

`TODO(SC-180)`: particles and block models get an abstraction when they are first implemented, not
before. Their shape is not knowable yet and a seam built early would be the wrong one.

## 3. Geometry

Two structurally incompatible file families, and this is the single most important parsing fork in
the project.

| Family | Shape |
|---|---|
| **`1.8.0`** | top-level keys matching `geometry\..*`; per-model `texturewidth`/`textureheight`; `bones[]` with `cubes[]`; **box UV only** (`uv: [u, v]`) |
| **`1.12.0` / `1.16.0` / `1.21.0` / `1.26.x`** | `{"format_version": …, "minecraft:geometry": [{description, bones}]}`; **per-face UV** (`uv: {north: {uv, uv_size, material_instance}, …}`); `poly_mesh` (1.16+); `binding` (Molang bone reparenting); locators with rotation |

Both appear in the same vanilla pack — `bedrock-samples` ships 79 files at `1.8.0`, 61 at `1.12.0`,
36 at `1.21.0` and a handful at others.

**Selection is by structural sniffing, not the declared `format_version`** (SC-110 §3.1): authoring
tools have shipped mismatched declarations for years. Presence of `minecraft:geometry` means modern;
presence of `geometry.*` keys means legacy; the declared version loses.

The IR normalises both into one model with per-face UV, since box UV is expressible as per-face UV
but not the reverse.

### 3.1 What the sniffer looks at

| Shape | Family |
|---|---|
| root has a `minecraft:geometry` member | modern |
| root has any member matching `geometry\..*` | `1.8.0` |
| neither | abstain; the declared version decides |

Checked in that order. A file holding both is modern, because the modern member is the one a tool
writes deliberately.

The declared version is **not** discarded — it is recorded in `Provenance.declaredVersion` alongside
the effective one and reported as `SCE-1031`. "Your file says 1.8.0 and is not" is only actionable if
the author can see both halves.

### 3.2 One IR for both families

| Concept | `1.8.0` | modern | IR |
|---|---|---|---|
| identifier | the root member's name | `description.identifier` | `identifier`, with the `a:b` parent split off |
| texture size | `texturewidth` / `textureheight` | `description.texture_width` / `_height` | `textureWidth` / `textureHeight`, defaulting to 16 |
| culling box | `visible_bounds_*` at model level | the same keys under `description` | `visibleBounds`, absent when undeclared |
| cube UV | `uv: [u, v]` | `uv: {north: {uv, uv_size, material_instance}, …}` | always per-face |

**Box UV is not a family marker.** Modern files use it constantly, so the *shape of the value*
decides how a cube's `uv` is read, not the file's family. A cube declaring no `uv` at all gets an
empty face map rather than a guessed rectangle: a guess renders the wrong part of the texture and
looks deliberate.

Bones are kept as a **flat list**, with the hierarchy expressed by `parent` naming another bone.
Bedrock's own files are flat and unordered — a child may precede its parent, and a named parent may
not exist — so resolving a tree at parse time would mean either rejecting files Bedrock loads or
inventing a root. Resolution is a later pass with its own diagnostic.

A bone with no `name` is skipped with `SCE-1036`; nothing can reference, parent or animate it, and
losing one bone costs less than losing the model. A model with no bones is legal and common: it is
how an inheriting model says "the parent's bones, unchanged".

Locators accept both spellings — `"name": [x, y, z]` and `"name": {"offset", "rotation"}` — and are
**sorted by name** in the IR. Bedrock writes them as a JSON object; a golden that preserved the
author's typing order would churn on an edit that changed nothing.

### 3.3 Box UV expansion

The `1.8.0` family's `uv: [u, v]` expands against the cube's `size: [x, y, z]` into the unwrapped-box
arrangement, with the depth forming the margins:

```
     +----+----+
     | UP |DOWN|
+----+----+----+-----+
|WEST|NRTH|EAST|SOUTH|
+----+----+----+-----+
```

| Face | origin | size |
|---|---|---|
| `north` | `(u + z, v + z)` | `(x, y)` |
| `south` | `(u + 2z + x, v + z)` | `(x, y)` |
| `east` | `(u + z + x, v + z)` | `(z, y)` |
| `west` | `(u, v + z)` | `(z, y)` |
| `up` | `(u + z, v)` | `(x, z)` |
| `down` | `(u + z + x, v)` | `(x, z)` |

`TODO(SC-180)`: **this arrangement is asserted, not verified.** It is what every available reference
describes, and no reference is an artifact. Nothing in a parse-level test can distinguish it from the
variant with `east` and `west` exchanged — a real possibility, since Bedrock's Z axis is mirrored
relative to Java's — because telling them apart requires looking at a rendered image. Until a T3 case
does that, `geometry/box_uv` carries the caveat, and the constants live in one class so the
correction is three lines rather than an audit.

`mirror` flips U per cube and is parsed but not applied to the expansion. `inflate` grows a box
without changing its UV, and is likewise recorded and not applied — both are render-stage concerns.

### 3.4 Coordinate conventions

**The IR preserves Bedrock's axes, pivots and handedness unchanged** (SC-110 §6.1). UVs stay in
texel units and are not divided by the texture size: the divisor is per model, packs get it wrong,
and folding a possibly-wrong divisor into the data makes the mistake unrecoverable.

Conversion to Java's conventions happens once, in the renderer, where it can be checked against an
image.

### 3.5 Not yet parsed

`poly_mesh`, `texture_meshes` and `binding` are recognised and preserved in the unknown bag rather
than modelled. `binding` is Molang, and SC-110 §7 forbids storing Molang as text that something might
evaluate — a raw string that looks evaluable is exactly how a parse error surfaces mid-frame with no
provenance. It is kept only so the round trip is lossless and so a diagnostic can name it, and it
becomes a `MolangExpr` when SC-130 lands.

`inheritance` — the `geometry.a:geometry.b` syntax — is split into `identifier` and `parent` at parse
time, but nothing resolves the parent yet.

## 4. Animation

Bedrock animations are a general skeletal keyframe system: `bones.<name>.rotation/position/scale`
with **Molang-valued** keyframes, `lerp_mode` (`linear`, `catmullrom`), `pre`/`post` keyframe pairs,
`anim_time_update`, `blend_weight`, `override_previous_animation`, `loop` and `loop_delay`,
`start_delay`, plus `particle_effects`, `sound_effects` and `timeline` maps that fire at times.

Java has nothing comparable for entities. Sections to write: the sampler, blending order, the
effect timeline, and how `anim_time_update` interacts with a variable frame rate.

## 5. Animation controllers

A finite state machine: states with `animations[]` (each with a Molang blend weight),
`transitions[]` guarded by Molang conditions, `blend_transition`, `on_entry` and `on_exit` scripts,
and `particle_effects` / `sound_effects` per state.

`TODO(SC-180)`: transition evaluation order, whether multiple transitions in one frame are
permitted, and the guard against transition loops.

## 6. Client entity definitions and render controllers

`minecraft:client_entity` binds aliases: `materials`, `textures`, `geometry`, `animations`,
`animation_controllers`, `scripts` (`pre_animation`, `initialize`, `animate`, `scale`, `variables`),
`render_controllers`, `particle_effects`, `sound_effects`, `locators`, `spawn_egg`,
`enable_attachables`, `hide_armor`, `held_item_ignores_lighting`.

Render controllers then choose, **per frame and via Molang**, the geometry, textures and materials
to use, which bones are visible (`part_visibility`), colour and overlay tints, `uv_anim`, and
lighting flags — with `arrays` plus Molang index expressions providing variant selection.

**Per-frame ordering is normative** and must be specified precisely: `pre_animation` → animation
controllers → animation sampling → `animate` → render controller evaluation → submission. Getting
the order wrong produces one-frame-late visuals that are extremely hard to diagnose.

`TODO(SC-180)`: write that ordering with the exact Molang scope visible at each step.

## 7. Particles (Snowstorm)

Emitter components (`emitter_rate_*`, `emitter_lifetime_*`, `emitter_shape_*`,
`emitter_initialization`, `emitter_local_space`) and particle components (`particle_initial_speed`,
`initial_spin`, `motion_dynamic`/`parametric`/`collision`, `lifetime_expression`/`events`,
`appearance_billboard` with flipbook UV, `appearance_tinting`, `appearance_lighting`, `kill_plane`,
`expire_if_in_blocks`, `expire_if_not_in_blocks`), plus `curves` (`linear`, `bezier`,
`bezier_chain`, `catmull_rom`) and `events`.

Nearly every field is Molang. Java's `ParticleType`/`ParticleProvider` cannot express this, so
SweetCookie ships its own particle engine, simulated in version-free code.

Particle **submission** is one of the paths that genuinely diverges (§2.1):
`submitParticleGroup(ParticleGroupRenderer)` on 1.21.11 versus
`submitQuadParticleGroup(QuadParticleRenderState)` on 26.2. That abstraction is written when the
particle engine is, not before.

## 8. Materials, textures, sounds

- `materials/*.material` — a JSON-ish format with `"child:parent"` inheritance, vertex and fragment
  shader names, and render states. Custom shaders are `wontfix`; the mapping is from material
  *states* to Java render types.
- `terrain_texture.json`, `item_texture.json`, `flipbook_textures.json`, `texture_list.json` — atlas
  definitions translated into Java atlas sources and `.mcmeta` animations, served from the virtual
  resource pack so vanilla does the stitching.
- `sounds.json` and `sound_definitions.json` — translated into Java sound definitions. `.fsb` (FMOD
  bank) audio is `wontfix`: no usable JVM decoder is known.
- `fogs/`, client biomes, `font/`, `texts/` — to be assessed.

### 8.1 How the virtual resource pack reaches the client

The pack is **generated, not shipped**: which Bedrock block holds which pool slot is decided per
world, and a raised `sweetcookie.blockPool` produces more slots than any build-time file set covers.
Files in the jar are a build-time answer to a runtime question and are a stopgap only.

It is **required and fixed-position**, never a pack a user selects. It is where the models for their
enabled add-ons come from; switching it off would make every bound block invisible with no
indication why.

Getting it into the repository is the one place the two loaders genuinely differ:

| Loader | Hook |
|---|---|
| NeoForge | `AddPackFindersEvent`, filtered to `PackType.CLIENT_RESOURCES`. Supported, ~40 lines. |
| Fabric | **a mixin.** There is no API for it. |

That Fabric line is a finding, not an assumption, and is recorded so it is not re-derived. Fabric
API's `ResourceManagerHelper.registerBuiltinResourcePack` serves a pack **out of the mod jar** and
cannot serve a generated one; `registerReloadListener` is for listeners, not packs; and Fabric
Loader's own `ModResourcePack` is likewise built from the jar. Every published mod doing this uses a
mixin.

The mixin target has one trap worth stating before anyone writes it. `PackRepository` carries no
`PackType`, so a mixin on its constructor cannot tell the client's repository from the server's and
would add the pack to both. Adding to both is *nearly* harmless — the pack answers with no
namespaces for `SERVER_DATA` — but it would list an empty data pack, so the mixin should target the
client's construction of the repository rather than the class itself.

`TODO(SC-180)`: land the Fabric mixin, and with it the mixin configuration this project does not yet
have.

### 8.2 Texture bytes

Not served yet, and the reason is a real constraint rather than an oversight: SC-100 §12 closes an
add-on's archive after parsing so that an author can replace the file they are editing, which is
exactly what a resource pack reading textures on demand would undo. Holding every texture resident
instead is a memory decision that needs its own answer, not a default.

Until then a bound block draws with the missing texture — visible, in the right place, with the
wrong surface. That is the intermediate state to keep: it proves the chain from `.mcaddon` through
IR, binding, ledger and pack to a chunk, and it cannot be mistaken for finished.

## 9. JSON UI

`wontfix` for 0.x. It is a complete data-driven UI engine with hundreds of undocumented
engine-provided bindings, and Mojang is itself migrating away from it toward Ore UI. Packs using it
load, and their UI files are ignored with one diagnostic.

`@minecraft/server-ui`'s `ActionFormData` / `ModalFormData` / `MessageFormData` are a much smaller,
tractable surface and **are** in scope — they are covered by SC-200, not here.

## 10. Testing contract

Geometry parse goldens across both families and every observed `format_version`; a rendered-image
golden per feature (bones, pivots, inflate, mirror, per-face UV, poly mesh, locators); animation
sampling goldens at fixed times; animation-controller transition traces; render-controller selection
traces; particle simulation traces at fixed seeds; and the Molang frame budget from SC-250.
