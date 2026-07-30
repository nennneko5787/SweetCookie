# SC-180 — Resource pack and rendering

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

The client half. Bedrock's resource pack is a general skeletal animation and dynamic model-selection
system; Java has hardcoded `ModelPart` hierarchies and code-defined renderers. This is effectively a
second renderer, and it is the largest client-side body of work in the project.

---

## 1. Decisions already taken

**The render abstraction is designed from 26.2's constraints** (SC-220 §2.2). 1.21.11's
`MultiBufferSource` is permissive; 26.2's submission model is restrictive; the interface follows the
restrictive one and 1.21.11 emulates it.

**Backends live in per-version source directories**, never inline `//?` (SC-220 §3).

**Everything except the backends is version-free**: geometry traversal, bone matrices, animation
sampling, animation-controller state machines, render-controller evaluation, Molang, Snowstorm
simulation. Target: under 10 % of client code is version-split.

**Bedrock coordinate conventions are preserved in the IR** and converted once, here, where the
result can be checked against a rendered image (SC-110 §6.1).

## 2. The backend interface

```java
public interface GfxBackend {
    RenderLayerRef entityCutout(ResourceLocation texture);
    RenderLayerRef entityTranslucent(ResourceLocation texture);
    RenderLayerRef entityEmissive(ResourceLocation texture);
    RenderLayerRef custom(BedrockMaterial material);
    MeshHandle uploadMesh(BedrockMeshData data);
}

public interface GfxSink {
    void mesh(RenderLayerRef layer, Matrix4f pose, MeshHandle mesh,
              int light, int overlay, int tintARGB);
    void quads(RenderLayerRef layer, Matrix4f pose, Matrix3f normal, BedrockQuad[] quads,
               int light, int overlay, int tintARGB);
    void particleBatch(RenderLayerRef layer, ParticleBatch batch);
}

public interface RenderLayerRef {}   // RenderType on 1.21.11, RenderPipeline on 26.2
```

`TODO(SC-180)`: **this interface is provisional until the 26.2 spike lands.** 26.2's
`FeatureRenderer<SUBMIT>` / `SubmitNode` shape is known in outline but not verified against the real
API. If `GfxSink` is wrong, the entire client is a rewrite — which is why the spike precedes all
other client work.

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

Sections to write: bone hierarchy and pivots, `inflate`, `mirror`, per-cube rotation, locators,
`poly_mesh`, `binding`, `visible_bounds`, and the Bedrock → Java coordinate conversion.

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
SweetCookie ships its own particle engine, simulated in version-free code and submitted through
`GfxSink.particleBatch`.

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
