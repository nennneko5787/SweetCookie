# SC-150 — Blocks and permutation resolution

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

Bedrock blocks: 32 components, 7 trigger components, 15 event responses, named states with at most
16 values each, engine-provided traits, and an ordered permutation list resolved by Molang.

Storage and slot binding are SC-120 §6 and are **not** repeated here. This document covers the
translation of behaviour and appearance.

---

## 1. Decisions already taken

**A Bedrock block occupies one pool slot with a single opaque index property** (SC-120 §6.1). It does
**not** get one Java `Property<?>` per Bedrock state. That choice trades readable debug output for
runtime attach/detach, and the trade was made deliberately.

**Permutations are pre-resolved per state index at bind time**, not evaluated per block access.
A block permutation condition may only reference block state and property queries plus pure maths
(no entity context, no world context), so every state index has a fixed, computable component set.
Evaluating Molang inside `getShape` would be indefensible.

**Later permutations override earlier ones**, per component key, which is Bedrock's rule.

**Behaviour is read through a live reference** (constitution rule 7): `BedrockBlock` overrides every
behavioural method to consult the current IR, and `BlockBehaviour.Properties` is built from
functions closing over that reference, so a reload changes behaviour without re-registration.

## 2. States, traits and the index

### 2.1 `description.states`

Two spellings, both normalising to an ordered value list:

```jsonc
{ "sc:kind":  ["short", "tall"] }                 // explicit
{ "sc:level": { "values": { "min": 0, "max": 3 } } }  // range, integers only
```

Values may be booleans, integers or strings. They are held as **strings** whatever they were
written as, because a state's values are only ever compared for equality and one type removes a
three-way branch from every site that touches one; the original JSON type is kept alongside,
because the Molang binding needs it (§3).

**At most 16 values.** A state declaring more is truncated to 16 with `SCE-1035` rather than
refused: truncating keeps the block usable and keeps the index stable for the values that fit,
where refusing loses the block entirely.

The **first declared value is the default** — for a freshly placed block, and for any lookup naming
a value the state does not permit. Refusing the latter would mean one typo in one permutation cost
the whole block, and Bedrock falls back too.

### 2.2 `description.traits`

Engine-provided state groups: `minecraft:placement_direction` (`cardinal_direction`,
`facing_direction`, `y_rotation_offset`) and `minecraft:placement_position` (`block_face`,
`vertical_half`). A trait is a state the engine fills in rather than the pack.

Traits expand into ordinary states before the index is built, and are **appended after** the pack's
own declared states. Appending rather than interleaving is what stops enabling a trait from shifting
the digits of the states already encoded in placed blocks.

Their value orders are fixed by the engine and are as load-bearing as a declared state's:

| State | Values, in order |
|---|---|
| `minecraft:cardinal_direction` | `south`, `west`, `north`, `east` |
| `minecraft:facing_direction`, `minecraft:block_face` | `down`, `up`, `south`, `west`, `north`, `east` |
| `minecraft:vertical_half` | `bottom`, `top` |
| `minecraft:y_rotation_offset` | `0`, `90`, `180`, `270` |

### 2.3 The index

State values are digits of a **mixed-radix number, least significant first in declaration order**.
A block with `sc:lit` (2 values) then `sc:level` (4 values) encodes as
`lit + 2 × level`, giving 8 indices.

**Declaration order is normative and is part of the on-disk format.** The index appears in chunk
storage and in the block ledger, so reordering a pack's `description.states` re-maps every block
already placed in every world. That is why SC-120 makes a schema change a *detected* event with a
re-mapping step rather than something that happens quietly.

`TODO(SC-150)`: 2.4, behaviour when the product exceeds the largest size class. Needs SC-120 §6's
pool to exist first.

## 3. Permutation resolution

The normative algorithm. Sketch:

```
for each state index i in 0 .. N-1:
    components := copy(block.components)
    for each permutation p in declaration order:
        if evaluate(p.condition, stateOf(i)) is truthy:
            components := merge(components, p.components)   // per key, p wins
    resolved[i] := components
```

**Truthiness is Molang's: any non-zero value, including negatives and fractions.** A condition
written `q.block_state('level') - 1` means "level is not 1", and packs write it that way.

**Merging is per top-level component key**, later permutation wins. A permutation setting
`minecraft:collision_box` replaces the base one entirely rather than patching into it — the two are
alternative shapes, not a shape and a delta.

`TODO(SC-150)`: whether Bedrock ever merges *deeper* than the top level for any component. Nothing
observed so far does, and this is the shape that matches every published pack read to date, but it
is inference from content rather than from the engine.

### 3.1 What a condition may read

**Block state and pure maths. Nothing else** — no entity, no world, no time. That restriction is
what makes the whole permutation set pre-resolvable per index at bind time; widening it would
quietly turn a bind-time table into a Molang evaluation inside `getShape`.

`query.block_state('ns:name')` and `query.block_property('ns:name')` are the same query. The state
name arrives as an argument, which in a float-typed language means an interned identity (SC-130
§2.1); the binding resolves it back. A state answers as a **number** when it is integral or boolean
and as its own interned identity when it is a string, so that `> 2` and `== 'tall'` both mean what
they look like.

A query outside that set reads 0, matching Bedrock. It is also visible *before* it runs: the
compiler records every referenced name, so a condition reaching for entity state is reportable at
load rather than silently always-false.

### 3.2 A condition that will not compile

The permutation is **dropped**, with `SCE-1035` naming the position. It is not defaulted to
always-matching or never-matching, because both are wrong and each silently changes what the block
looks like in half its states.

## 4. The 32 components

| Component | Java counterpart | Difficulty |
|---|---|---|
| `collision_box`, `selection_box` | `VoxelShape` | Bedrock is a single AABB from origin+size, capped at 1.875 blocks and required to start inside the block; Java is a union. Widening is easy, and the new BP `shapes/` folder may change this. |
| `destructible_by_mining` | hardness + tool | Bedrock uses **seconds**, with per-item overrides; Java uses hardness plus tool tiers plus mining speed. Not a linear conversion. |
| `destructible_by_explosion` | blast resistance | direct |
| `geometry` + `material_instances` | baked model | §5 |
| `light_emission`, `light_dampening` | `lightEmission`, `lightBlock` | direct |
| `friction`, `map_color`, `flammable`, `replaceable`, `movable` | direct | |
| `placement_filter` | `canSurvive` | filter-based (SC-140) |
| `transformation` | model transform | no direct counterpart; applied at bake |
| `redstone_conductivity`, `redstone_consumer` | redstone behaviour | partial |
| `queued_ticking`, `random_ticking` | `tick`, `randomTick` | direct |
| `liquid_detection`, `precipitation_interactions`, `breathability`, `chest_obstruction`, `connection_rule`, `support`, `item_visual`, `destruction_particles`, `unit_cube`, `crafting_table`, `display_name`, `loot`, `custom_components` | — | to be assessed |

`TODO(SC-150)`: complete the table with a status per component and open the coverage entries.

### 4.1 The two boxes

`collision_box` and `selection_box` are read **independently**, never once and shared. Minecraft
falls `getCollisionShape` back to `getShape` by default, so one shared answer would make
`"selection_box": false` delete a block's collision as well — and `"collision_box": false` with the
outline left alone is what a plant or a decorative block is.

Both are converted at bind time, per state index, from Bedrock's centre-relative `origin` plus
`size` to a `VoxelShape`. Nothing rebuilds a shape inside a collision query, for §1's reason.

A pool block is registered with **`Properties.dynamicShape()`**, and this is required rather than
advisory. Minecraft's per-state cache holds a collision shape, the context-free
`getCollisionShape(level, pos)` answers out of it, and it is built by `BlockState.initCache` —
which vanilla runs from `Blocks`' class initialiser, before any world exists and therefore before
anything is bound. Whether a given loader registers early enough for our states to be cached is not
a question this project should have to answer.

Bedrock's stated limits — the origin must start inside the block, and the box may not exceed 1.875
blocks — are applied by **truncation**. Neither has been observed on a real Bedrock client, and
what Bedrock does with a larger box is unverified; truncating keeps the block, and refusing it over
a number would be the outcome constitution rule 5 exists to prevent.

**Light occlusion is not yet tied to the boxes.** A block with a smaller collision box still shadows
as a full cube. That agrees with how it is drawn — every bound block is a unit cube until §5's
geometry lands — and the two should move together.

## 5. Geometry and materials

Bedrock block geometry is an **entity-style bone/cube model** with per-face material instances and
Molang `bone_visibility`. Java block models are a different `elements[]`/`faces` format with
`cullface`, and have no bone hierarchy.

Two paths:

- **Path A — transpile to a Java block model** when the geometry is axis-aligned boxes with static
  bone visibility. Gets vanilla chunk meshing, ambient occlusion, culling and lighting for free.
  This is the common case and should be the default.
- **Path B — render dynamically** when the geometry needs Molang-conditional bone visibility,
  non-axis-aligned rotation, or a poly mesh. Costs a block-entity renderer.

### 5.1 The predicate

Path A is taken when **every** statement below holds of the whole model. Any one of them failing
sends the model to Path B; there is no partial transpile, because half a model drawn twice by two
different renderers is worse than a whole model drawn once by the slower one.

1. No bone declares `binding`. It is Molang bone reparenting evaluated per frame, and a Java block
   model is baked once.
2. No cube carries a `poly_mesh` or `texture_meshes`. Neither is a box.
3. **Every bone rotation is zero, or a quarter turn about one axis.** A quarter turn takes an
   axis-aligned box to another axis-aligned box — it permutes the axes and flips some signs — so it
   is baked into the cube's corners and its face names. A rotation that is not a multiple of 90°
   makes a box that is not axis-aligned, which is the one thing a Java `elements[]` entry cannot be.

   **One axis at a time.** Two would need Bedrock's composition order, which is not written down
   anywhere this project can check, and getting it wrong gives a model that is right in outline and
   wrong in orientation — the failure that is hardest to attribute.

   **A bone inherits its parents' turns**, and a chain of them composes. Missing that is the
   difference between working and not: the ordinary way to orient a Bedrock model is `rotation` on a
   childless `root` bone with the cubes in a child, and reading each bone's own rotation alone
   transpiles it into a model that never turns.

   **A Bedrock angle turns the opposite way round to a right-handed turn about the same axis**, and
   that is observed rather than asserted. A pack orients one geometry four ways through four
   permutations — `north` 0°, `south` 180°, `west` +90°, `east` −90° — and taken right-handed, the
   blocks placed along the east-west line came out holding each other's rotation while north and
   south were right.

   Half turns are the same either way round, so a wrong sense can only ever show at 90° and 270°.
   It showing up at exactly those two is what names the sense as the culprit rather than the mirror
   (§5.3): a wrong mirror would have taken north and south with it.

   The same correction applies to every angle a pack writes, including the cube rotations of rule 4
   that Java expresses itself. Correcting only the turns that get baked would leave a cube rotated
   45° and a bone rotated 90° disagreeing about which way round the model goes.
4. **Every cube rotation is either zero, or a single-axis rotation of ±22.5° or ±45°.** That is
   exactly what a Java element's `rotation` can express. Two axes at once cannot be.

With every rotation zero, no bone transform needs baking at all: Bedrock cube origins are already
in model space, and a bone's pivot only matters to a rotation that is not happening. The hierarchy
is therefore not resolved on Path A — which is why §3.2's unresolved-parent problem does not block
this.

### 5.2 The fallback

Path A is attempted first and can fail in three ways, all of which land in the same place:

- the block names a geometry no enabled pack declares
- the predicate above rejects the model
- Path B, which would take it, does not exist yet

**Every one of them draws the block as a unit cube with its `material_instances` texture, and emits
a diagnostic naming the block and the geometry.** The block stays placeable, breakable, collidable
and visible. Refusing to draw it, or drawing nothing, would leave a player with an invisible
obstacle — which is worse than a recognisable wrong shape, and is what constitution rule 5 is about.

| Code | Cause |
|---|---|
| `SCE-2030` | the block names a geometry no enabled pack declares |
| `SCE-2031` | the geometry exists and §5.1 rejects it |
| `SCE-2032` | a material's texture resolves to no file in any enabled pack |

Separate codes because the fixes belong to different people: `SCE-2030` and `SCE-2032` are a missing
or misspelled file in the pack, `SCE-2031` is a model this project cannot yet draw. A single code
would send everyone to the same wrong page.

`SCE-2032` is emitted **once per block, not once per state** — states of one block almost always name
the same materials, and a 32-state block would otherwise report the same absent file 32 times.

A block whose texture does not resolve still draws: it gets the missing texture, which is visible
and reportable, where refusing it would leave an invisible obstacle. But visible is only half of it,
and without the diagnostic the user has a black-and-magenta cube and no line anywhere naming the
file.

This is also today's behaviour for every block, so Path A is a strict improvement over the fallback
rather than a replacement for it.

### 5.3 Conversion

**Everything is computed in Bedrock's space and converted once, at the end.** That is SC-110 §6.1's
rule and it is load-bearing rather than tidy: the face-direction table and the point rotation are
mirror images of each other by construction, and turning boxes in a half-converted space let the two
disagree about handedness — a disagreement invisible on any symmetric model.

| | Bedrock | Java |
|---|---|---|
| position, X | measured from the block's centre | `from`/`to`, measured from the corner: `x + 8` |
| position, Z | measured from the block's centre, **and the axis runs the other way** | `8 - z`, and the box's corners swap over |
| face names | `north` is at Bedrock's `−Z` | that is Java's `+Z`, so **`north` and `south` trade places** |
| face textures | — | **no face is ever flipped, and none is spun.** The conversion is a pure relabelling |
| cube rotation | `rotation` about one axis | the angle is **kept** for `x` and `y` and **negated** for `z`. Two corrections that cancel on two axes: the pack's angle turns the other way round to begin with (§5.1 rule 3), and reversing Z reverses the sense of a turn about the other two |
| a pivot | a point in the same space as the box it turns | **the box conversion, applied to a point**: `x + 8`, `8 − z`. It is not a separate rule and must not be restated as one — restating it is how the pivot kept an X mirror after the mirror had moved to Z, and a pivot on the block's own axis is fixed by either, so nothing saw it |

**A conversion between these two engines can only rotate a face, never flip one.** Both draw a UV
rectangle with positive extent un-mirrored, so a flip would render that face as its own mirror image
— which neither engine does to anybody's model. This is worth stating as a rule because the first
attempt at this conversion flipped four of the six faces and no test could see it.

It also settles the shape of the answer without a screenshot. The four side faces' U directions
circulate around the box in one consistent sense in **both** engines, so a conversion reverses all
four or none: *two of four is not a possible answer*. With the mirror on Z, the answer is none — the
conversion comes out as a pure relabelling, with no face flipped and no face spun.

**Which axis is mirrored was settled by looking, not by reasoning.** X was tried first; it cured the
inside-out look and left the model facing backwards, which showed up as a block placed along the
north-south line coming out the wrong way round. What settles it now is stronger than that first
observation: with the mirror on Z and §5.1 rule 3's angle sense corrected, a block orienting one
geometry four ways is right in **all four**, and a mirror on the other axis is a half turn away —
which no permutation could be right through.

**One symptom that was read as this one was not.** The same investigation recorded that "the model
in the hand still looked wrong after the icon looked right", and took it as evidence about the
mirror. It was not: the icon and the hand are the same file, so no mirror can be right in one and
wrong in the other. It was §5.6 — the pack's own `item_display_transforms` were being dropped, and
the icon was at Java's default angle. Kept here because the reasoning was wrong in a way that
happened to reach the right axis, and a reader re-deriving it from that symptom would not be so
lucky.
| UV | texel units, divided by the model's `texture_width`/`_height` | 0–16 over the whole texture, whatever its resolution |
| `inflate` | grows the box, leaves UV alone | no counterpart; baked into `from`/`to` |
| `mirror` | flips U | no counterpart; baked by swapping the face's two U coordinates |
| `never_render` | bone draws nothing | no counterpart; the bone's cubes are dropped |

`collision_box` and `selection_box` go through **the same conversion, mirror included** (§4.1). That
is a real argument rather than a preference: a pack author places a collision box and a model in one
coordinate space, and Bedrock reads both. Converting them differently would put every asymmetric
block's collision on the opposite side from the thing a player can see.

**How the mirror was found, and why it took so long.** It shipped wrong. Every coordinate assertion
in the corpus used an X-symmetric box and no test anywhere told `east` from `west`, so nothing
failed; the transpiler wrote Bedrock's face names straight into the Java model and the offset alone
looked like the whole conversion. It was caught by putting the same block in both editions side by
side. Two tests now stand in that gap — an asymmetric box, and a cube whose six faces carry six
different UVs — and the corpus carries an asymmetric element for the same reason.

No face receives a `cullface`. A face culled against a neighbour that turns out not to fill its side
disappears, and a face drawn that could have been culled costs one quad.

### 5.4 Materials

`render_method` → `RenderType`: `opaque` → solid, `alpha_test` → cutout, `alpha_test_single_sided` →
cutout with culling, `blend` → translucent, `double_sided` → solid without culling. Imperfect;
`face_dimming`, `ambient_occlusion` and `tint_method` need their own treatment.

A face names a **material instance**, not a texture: `material_instances` maps the instance name to
a texture key, `terrain_texture.json` maps that to a path, and the pack's VFS maps that to bytes
(§4.1's chain, already built). A face naming no instance uses `*`. Every instance a model actually
uses becomes one entry in the generated model's `textures` map.

### 5.5 Occlusion

Pool blocks are registered **`noOcclusion()`**. A model smaller than its block must not cull its
neighbours' faces, and whether it is smaller is not known at registration — the occlusion shape is
baked once, before any pack is bound, and unlike the collision shape there is no `dynamicShape`
equivalent to opt out of that.

The cost is that a bound block which *is* a full cube no longer blocks light or hides the faces
behind it. That is a visible but mild wrongness (a brighter cave) set against an invisible and
severe one (terrain disappearing behind a custom model), and it is the trade this section takes
deliberately.

### 5.6 Display transforms

The generated model inherits `minecraft:block/block`, which carries **no elements and every display
transform** — how a block is held, dropped, worn and shown in an inventory. Without a parent the
block is right in the world and wrong in every hand holding it.

Where the geometry states its own `item_display_transforms` (SC-180 §3.6), those are written into
the model's `display` block **on top of** that inheritance. Java resolves `display` one context at a
time up the parent chain, so a pack that states only `gui` keeps vanilla's answer for the other
seven; a context the pack did not state is therefore **not written at all**, and writing an identity
transform in its place would silently replace vanilla's answer with "no transform".

| | Bedrock | Java |
|---|---|---|
| `rotation` | degrees, per axis | §5.1 rule 3's angle correction and §5.3's mirror, which is exactly the correction a cube rotation gets: **kept** on `x` and `y`, **negated** on `z` |
| `translation` | sixteenths of a block | **a displacement, not a position**: it takes the mirror's sign on `z` and *not* the centre-to-corner offset on `x`. Adding 8 here moves every icon half a block sideways |
| `scale` | a multiplier per axis | unchanged — a mirror commutes with a scale along the axes |

**Not observed: the order the three angles compose in.** Java's half is read off the jar — the
transform is applied as `Quaternionf().rotationXYZ(x, y, z)`, so X then Y then Z — and Bedrock is
*assumed* to match. Conjugating by a mirror preserves whatever the order is, so a transform turning
about one axis is right either way and only one turning about two at once could show a difference.

**Not observed: what Bedrock does for the left hand.** Java mirrors the two left-hand contexts
itself, negating `translation.x`, `rotation.y` and `rotation.z` at render time whether or not the
model declared a `*_lefthand` transform of its own — the convention is "author the right hand, get
the left one free". A pack's left-hand values are therefore passed through **verbatim**, which is
right if Bedrock inherited that convention along with the rest of the system, and mirrors the item
in the off hand if it did not. Verbatim is the choice that costs nothing when the guess is right;
pre-compensating for Java's mirror would be wrong in both directions if the guess is wrong.

**Why a block model carries any of this.** The item form of a bound block draws this same file
(SC-170), so the transforms have nowhere else to live. It is also the only part of a geometry whose
absence is invisible in the world and visible in the hotbar — see SC-180 §3.6.

## 6. Triggers and event responses

7 trigger components (`on_fall_on`, `on_interact`, `on_placed`, `on_player_destroyed`,
`on_player_placing`, `on_step_off`, `on_step_on`) dispatching 15 event responses (`add_mob_effect`,
`damage`, `decrement_stack`, `die`, `play_effect`, `play_sound`, `remove_mob_effect`, `run_command`,
`set_block`, `set_block_at_pos`, `set_block_state`, `spawn_loot`, `swing`, `teleport`,
`transform_item`).

`run_command` executes a **Bedrock** command string. SC-190 covers command translation; unsupported
commands degrade with a diagnostic rather than executing something approximate, because executing an
approximation of a command a pack author wrote is worse than not executing it.

## 7. Testing contract

Per-component conformance cases; permutation resolution goldens covering override order, multiple
matching permutations and none matching; index encode/decode round trips including traits; a Path A
versus Path B decision test; and render goldens for both paths.
