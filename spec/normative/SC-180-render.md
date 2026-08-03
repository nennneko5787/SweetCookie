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

### 3.4.1 The angle sense applies to the render path too — on two axes of three

**A Bedrock rotation angle about X or Z turns the opposite way to a right-handed turn. About Y it
does not.** This governs every angle a pack writes: a bone's `rotation`, and an animation's
`rotation` channel.

The asymmetry is a consequence rather than a quirk. Bedrock's entity space is the engine's with Y
flipped (§3.4.2's `diag(1, −1, 1)`), and conjugating a rotation by that flip reverses the two axes
the flipped one takes part in and leaves the third alone:

```
D · Rx(t) · D = Rx(-t)      D · Ry(t) · D = Ry(t)      D · Rz(t) · D = Rz(-t)
```

The bone and animation paths originally applied no correction at all, and every rotation came out
reversed. **Nothing saw it for a long time** because a bind pose is mostly symmetric — a skirt panel
at ±17.5° looks the same mirrored. It surfaced when an animation moved something asymmetric: a
piggybacking character's legs, posed at −73° to wrap forward around the player, swung backwards.

**The fix then over-corrected**, negating all three axes on the assumption that a sign convention is
uniform. That is the more instructive half, because the X evidence was real and the Y change rode in
beside it unexamined, and stayed wrong for weeks:

| symptom | what it looked like |
|---|---|
| the same character's legs at ±24° about Y **crossed** instead of spreading | "the legs are too narrow" |
| a head driven by `query.target_y_rotation` turned the **opposite way** to the player's, while its pitch tracked | "horizontally they face different directions, vertically the same" |

Settled by measurement, in Bedrock's own coordinates: with Y negated the two legs sat at
x −3.0..4.1 and −4.3..2.7 — overlapping, each on the other's side of the centre line. Without, at
−7.7..−0.7 and 0.4..7.5, mirrored.

**A correction that is right on one axis is not evidence about the others.** One helper composes the
sense for both paths, so a declared rotation and an animated one cannot disagree.

**The Y sense is now measured directly rather than inferred.** Probe v9 (§4.1.3) put a
rotation-only animation of `[0, -85, 0]` on a forward-pointing marker on the Bedrock client: the
marker swings from forward to the **player's left**, which is the standard right-handed turn this
build uses. Until then Y's non-inversion rested on a rifle-grip frame that a sceptic could call
confounded; it no longer does.

`TODO(SC-180)`: the same question is open for `poly_mesh` and for locator rotations, neither of which
is drawn yet.

### 3.4.2 An attachable is posed in player space, in every view

**A Bedrock attachable's geometry is authored against the player's skeleton, not against the item.**
A model whose feet are at y 0 and whose head is at y 40 is describing where it sits on a person; an
animation that moves a root bone is moving it relative to that person. Nothing in an attachable is
expressed relative to a hand, and **this holds in first person too** — which is the part that is easy
to get wrong, because first person is where Java would naturally draw a held item.

It was got wrong here. First person was drawn through a `SpecialModelRenderer`, which draws at the
item's position, and the model hung two and a half blocks below the hand. The measurement that
settled it: a real pack conditions an animation on `v.main_hand && c.is_first_person` whose entire
content is `root3: rotation [0,180,0], position [0,0,-11]`. It turns the character around and moves
it 0.69 blocks forward **of the player**. A pack composing against player space is evidence that
Bedrock provides one.

**First person is the exception, and it is not player space at all.** Bedrock renders the two views
by different means: in first person the model is **fixed to the screen**. Two of its frames, one
pitched fully up and one fully down, place the character's parts at the *same screen positions* —
so nothing about the camera is undone. A person watching it describes the model as "following the
view", which is the same observation.

Rebuilding player space against the camera — undoing the pitch, taking the yaw back to the body's —
was tried, on the premise that first person should hold the model where third person holds it. **It
had to come out.** The premise was wrong: the two views are not the same space.

| | |
|---|---|
| origin | the eye, so Bedrock's y 0 is the player's own **eye height** below it — read per frame, since a crouching player's eye drops |
| axes | `diag(-1, +1, +1) / 16`, Bedrock units to camera units |
| rotation | **none**. Camera space is left as it is |

`query.target_*_rotation` answers **zero** in this view for the same reason: a bone that aims itself
at the gaze would swing inside a model that is already following it, and Bedrock's first-person
character does not turn her head. The third-person layer still supplies them, and there she does.

The axes follow from the third-person conversion and the camera's, with the player facing south so
the world axes can be named:

```
Bedrock -> world     R(180) . scale(-1,-1,1) . ON_PLAYER   =  diag( 1,  1, -1) / 16
world   -> camera    camera's -Z is south, its +X is west  =  diag(-1,  1, -1)
Bedrock -> camera                                          =  diag(-1,  1,  1) / 16
```

Reading it back is the check: Bedrock −Z is the direction the player faces and stays the camera's
−Z, so `position: [0, 0, -11]` lands 0.69 blocks in front of the eye, which is where the survey said
it lands. **Parity is odd — one axis flips — and that is correct here** rather than the mirror bug
§3.4.1 records, because Bedrock's model space is already mirrored against the world and the
world-to-camera step is a proper rotation.

**X was only ever checked as part of that composition, and it has since been checked on its own.**
It holds. A Bedrock first-person capture of an attachable that carries a rifle on the character's
right — `gun2`, at `x -4.43 .. -1.96` — shows the rifle on the screen's **right**. Bedrock `−X` is
the character's own right, which is the player's right, which is the screen's right in a view taken
from the player's eyes. Which is also what the table above computes without being told: third person
is confirmed on a Bedrock-facing frame, so first person follows from it by arithmetic rather than by
choice.

**One anchor appeared to contradict this and does not survive inspection**, recorded because it is a
mistake worth not making twice. Vanilla's `first_person.empty_hand` moves `rightarm` by `[13.5, …]`
against a rest pivot of `(-5, 22, 0)`, so its net `x` is positive — and a first-person hand is at the
screen's right, which reads as `+X → right` and therefore as a mirror here. **It reads as nothing of
the kind: `rightarm` is a child of `body`, and `body` is the one bone first person rotates**
(§4.2.1). Its final side depends on a rotation this document has not pinned down, so the arm cannot
testify about the axis until that rotation is known. An anchor whose value depends on the unknown it
is being used to settle is not an anchor.

**The seam is therefore per view, not per kind of attachable.** Third person is a render layer on the
player; first person is a hook on the hand render. An earlier reading — that hand-held things go
through the item and body-worn things through a layer — does not survive the measurement: there is no
attachable that belongs at the hand.

That `TODO` — whether Bedrock keeps the model upright as the view pitches or lets it ride the camera
— **is answered: it rides the camera.** The evidence is above; it cost a day, because the question
was asked of the code and the maths rather than of a Bedrock frame, and both compensations that were
built in the meantime had to be removed again.

### 3.4.3 What the corpus uses and this build drops

**Asked in the form "there is probably something skipped because the loaded add-on did not need
it", and there is.** The list matters more than any one entry, because each was left out on the same
reasoning and each will come back the same way.

| dropped | uses in the surveyed corpus |
|---|---|
| ~~`lerp_mode: catmullrom` and `post` keyframes — §4 samples linearly~~ **closed, §4.1.2** | **233**, counted again against the installed packs rather than trusted from this row |
| `reset` on a bone — not in `BONE_KEYS`, so it lands in the unknown bag | 2, and both on the bones of the one model whose first-person placement is wrong |

Neither is the cause of that placement, and the checks are worth recording so they are not redone:
the `catmullrom` uses are in walk and idle animations of *other* characters, not the two the
first-person question turns on; and `reset` **appears in no version of Mojang's geometry schema —
1.12, 1.16 or 1.21 — and in no vanilla model**, which makes it a Blockbench artefact that Bedrock
most likely ignores too.

**The first entry was a real fidelity gap and has been closed on its own merits** (§4.1.2). Two
hundred and thirty-three keyframes across twenty-two files sampled as straight lines where Bedrock
curves them, and none of them belonged to the model whose placement this section is about — which is
why it stayed open while the placement question was worked: it explains none of that, and it was
still wrong.

### 3.4.4 `binding` decides what a bone attaches to, and its absence decides too

Mojang's schema states the rule this document had only ever inferred from screenshots:

> `binding` — "A molang expression specifying the bone name of the parent skeletal hierarchy that
> this bone should use as the root transform. **Without this field it will look for a bone in the
> parent entity with the same name as this bone.** If both are missing, it will assume a local
> skeletal hierarchy (via the `parent` field). If that is also missing, it will attach to the owning
> entity's root transform."

So §4.2's "bones named after the wearer's are driven by the wearer" is not a guess: **name matching
is the documented fallback when there is no binding**, and the pack's own `parent` chain is the
fallback after *that*. A Method-1 attachable — one that rebuilds `root`, `waist`, `body` and hangs
its character off them — is relying on exactly this clause.

### 3.4.5 The first-person X axis is not mirrored, and the check that says so

**Tried and refuted, because it is the reading that would make the corpus's numbers work.** A pack's
first-person animation puts a character at `root2 position [-32, …]`, and the Bedrock client shows
her on the screen's **left**; this build's conversion sends Bedrock `−X` to the screen's right, so a
mirror on this axis would reconcile them. It would also explain vanilla's first-person `rightarm`,
which is displaced to a positive `x` and appears on the right.

**It breaks the one attachable that is confirmed correct.** With the axis flipped, the
`waist`-parented character — whose own first-person animation turns her half a turn — ends up facing
the camera and standing through it. The mirror and that half turn cancel, which is exactly why she
looked untouched under both readings and why this took three attempts to eliminate: **a character
who is nearly symmetric about the spine cannot testify about a mirror**, and this is the third time
in this feature that symmetry has hidden a sideways error.

So the axis stands as §3.4.2 has it, and the contradiction stays open:

```
Bedrock +x            → the screen's left   (measured twice, with and without the pack's animation)
the Bedrock client    → the character is on the screen's left
that animation's root → x = -32, which is the screen's right
```

`TODO(SC-180)`: the three cannot all hold. **Do not resolve it by fitting a rotation to `body` —
five constants were fitted that way and all five were refuted** (§4.2.1).

#### What the vanilla files settle, and what they do not

Asked in the form "read Mojang's own player files rather than a screenshot", which was the right
instinct: two of the three open questions around this fall out of them, and the axis is not one.

**Settled: there is no hidden first-person transform to find.** Across all eighteen animations of
`player_firstperson.animation.json` the bones written are `rightarm` (9), `rightitem` (8), `leftarm`
(7), `leftitem`, `head` and `body` — and `body` **once**, rotation only:

```json
"body": { "rotation": [ "query.target_x_rotation", "query.target_y_rotation", 0.0 ] }
```

`waist` and `root` are never written by any of them, and the player entity uses one geometry for both
views (`geometry.humanoid.custom`, `scripts.scale` 0.9375) — there is no separate first-person rig.
Mojang defines the angles as "the rotation required to aim at the entity's current target **if it has
one, else it returns 0.0**", and a player has no such target. So the wearer's first-person skeleton is
`body` at identity and `head` at half a turn, **which is what this build already had** — now derived
from the source rather than from a measurement.

**Settled: the wearer bone is not a choice between replacing and composing.** That argument ran for a
day (§4.2.1). `base_pose` writes `body`'s **rotation**; the corpus's first-person animation writes
`body`'s **position** (`[0, -1, -6]`). §4.1 composes per channel component, so the two never meet —
there is nothing to arbitrate, and replacing throws away a third of a block of forward offset that
the pack asked for. **A dilemma whose two horns touch different channels is not a dilemma.**

**Refuted, by the instrument, before it reached a screen: the idea that the axis observations were
taken in two different spaces.** The measurement that fixed the axis was made on `gun2`, which hangs
below a `root3` that its own animation turns by `[0, 180, 0]` — so it looked as though that bone had
been read one rotation below the space the other character's `root2` lives in, and "+x is the
screen's right" would then satisfy both. **It does not.** `AddonSurvey` reports coordinates after the
whole chain is composed, so that half turn is already in the number, and both characters sit at
negative x in the same space:

| | posed x | reported on screen |
|---|---|---|
| `gun2` | −4.43 … −1.96 | centre-**right**, and both clients agree |
| the other character's `body2` | −26.08 … −14.62 | the Bedrock client shows her on the **left** |

Two negative values cannot land on opposite sides of the screen under any single convention. So the
contradiction is **not** in the axis rule and not in a missing vanilla transform: it is between the
two screen observations themselves, and one of them is about something other than what it says.

**RESOLVED — by two probes, and every number in the table above was right.** Probe v9 (§4.1.3)
measured the composition: positions apply unrotated, so `-32` really is two blocks to the player's
right, exactly as this section's axis reading says. Probe v10 (§4.2.1) measured the missing piece:
**in first person the wearer's `body` bone carries a half turn**, which maps that right to the
screen's LEFT for the one character whose rig hangs off `body` — and never touches the character
who hangs off `waist`, which is why she was correct throughout and could not testify. The Bedrock
client's left-side, point-blank frame and this section's axis table were never in conflict; the
half turn between them was simply unmeasured. Three candidate resolutions shipped and were refuted
on the way (§3.6's X negation, §4.1.3's offset-riding-rotation, §4.4's conditions-never-fire) —
each explained the one frame in front of it, and only the instrument survived all of them.

### 3.6 An animation's `position` X does NOT run opposite to the geometry's — tried, and refuted by a frame

**This section was written the other way round and shipped for under an hour.** It is kept in the
shape it was found, because the argument for the negation is genuinely available in the
documentation, reads convincingly, and will be found again.

**The claim:** a keyframe's `position` is measured on an X axis pointing the other way from the one a
cube's `origin` and a bone's `pivot` use. Two independent sources appear to say so. The Bedrock Wiki,
from the editor:

> "If you move boxes in the positive X direction the box coordinates show as positive x values,
> **however if you create a bone, and go to the animation tab, and try adjusting the position of the
> bone … dragging the handle in the positive direction actually gives you negative values on X.**"

and Blockbench's own issue tracker, from the other side:

> "The X and Y rotation channels and **the X position channel are inverted in keyframes**."

**Why it was convincing.** Every animation in the corpus that mattered writes `position` X as zero —
the idles, and the first-person animation of the character whose placement was *confirmed correct*
(`root3 position [0, 0, -11]`). The single exception is the first-person animation of the character
whose placement was *wrong*: `root2 position [-32, -2, -9]`. One number, in one file, and it was the
number the open question was about. A rule that touches exactly the broken case and nothing else is
the most seductive kind there is.

**The frame said no.** With X negated she left the view entirely, and the person looking reported
that she had stopped being drawn at all:

| | camera space, in blocks |
|---|---|
| before | 0.94 … 1.63 to the right, and 0.22 in front to 0.84 **behind** the eye — partly visible, the head across the near plane |
| after | **2.25 … 2.94 to the left**, 0.36 … 1.41 in front — some sixty degrees off axis, outside the frustum |
| the Bedrock client | at the frame's edge: thirty to forty-five degrees, with hair showing top-left |

So the negation overshoots by more than it corrects. **Both quotations are about the editor's
display** — Blockbench inverts what it *shows* so the drag handle matches edit mode, and the file it
writes is already in the engine's space. Read carefully that is what they say; read quickly it is
not.

`AnimationSampler.POSITION_X_SENSE` is `+1` and named, so the next person to find those two sentences
finds this measurement beside them.

**What this does NOT settle.** The placement is still wrong — she is off-axis to the *right* by about
a block and a half with her head across the near plane, where Bedrock has her at the left edge. The
sign is not the whole error, and it may not be part of it at all. `TODO(SC-180)`: the magnitude and
the space are still unexplained, and §3.4.5's contradiction is open again.

`TODO(SC-180)`: the same sources say the X and Y *rotation* channels are inverted in keyframes,
which does not match §3.4.1's measured senses (X and Z inverted, Y not). Given how this section
turned out, **that is a statement about Blockbench's display too, until a frame says otherwise.**

### 3.5 Not yet parsed

`poly_mesh`, `texture_meshes` and `binding` are recognised and preserved in the unknown bag rather
than modelled. `binding` is Molang, and SC-110 §7 forbids storing Molang as text that something might
evaluate — a raw string that looks evaluable is exactly how a parse error surfaces mid-frame with no
provenance. It is kept only so the round trip is lossless and so a diagnostic can name it, and it
becomes a `MolangExpr` when SC-130 lands.

`inheritance` — the `geometry.a:geometry.b` syntax — is split into `identifier` and `parent` at parse
time, but nothing resolves the parent yet.

### 3.6 `item_display_transforms`

A model in the world sits where its block is. The same model in a hotbar slot, in a hand, on a head
or in an item frame has no block to sit in, so a geometry may state where to put it per context —
`item_display_transforms`, a sibling of `bones` on the model and **not** a member of `description`.

Java has the same thing under the name `display`, with **the same eight context names, the same
three fields and the same units**: `rotation` in degrees, `translation` in sixteenths of a block,
`scale` as a multiplier. Bedrock took the idea and the vocabulary from Java, so the mapping is the
identity and there is no table here to get wrong.

| | |
|---|---|
| contexts | `thirdperson_righthand`, `thirdperson_lefthand`, `firstperson_righthand`, `firstperson_lefthand`, `gui`, `head`, `ground`, `fixed` |
| a name outside that list | **reported (`SCE-1035`) and dropped** |
| an omitted `rotation` or `translation` | zero |
| an omitted `scale` | **one**, not zero — the other default would scale the model to a point |

The IR keeps the pack's numbers unchanged, as §3.4 requires of everything else. Where they are
converted, and to what, is SC-150 §5.6 — a block model is the only thing that consumes them today.

**A name outside the list is dropped rather than passed through** because Java's model loader
silently ignores a display context it does not recognise. Passing a typo through would make it
invisible in the file and in the game at once, which is the failure the diagnostic exists to
prevent.

**Absence is visible in exactly one place.** A block whose geometry states these and a build that
ignores them draws correctly everywhere the world draws it and at Java's default angle everywhere an
item is drawn — roughly a quarter turn from the angle the pack chose. That reads as a mirrored icon
rather than as a dropped component, and it is why this is the one part of a geometry that can be
wrong while every screenshot of the block itself looks right.

## 4. Animation

Bedrock animations are a general skeletal keyframe system: `bones.<name>.rotation/position/scale`
with **Molang-valued** keyframes, `lerp_mode` (`linear`, `catmullrom`), `pre`/`post` keyframe pairs,
`anim_time_update`, `blend_weight`, `override_previous_animation`, `loop` and `loop_delay`,
`start_delay`, plus `particle_effects`, `sound_effects` and `timeline` maps that fire at times.

Java has nothing comparable for entities. Sections to write: the sampler, the effect timeline, and
how `anim_time_update` interacts with a variable frame rate.

### 4.1 Two animations naming one bone: the components add

**When several entries of `scripts.animate` play in the same frame and name the same bone, their
channel components ADD, and a transform is built once at the end.** Mojang states it:

> "At the beginning of each frame, the skeleton is reset to its default pose from its geometry
> definition and then animations are applied **per-channel-additively in order**." … "The channels
> (x, y, and z) are added separately across animations first, **then converted to a transform once
> all animations have been cumulatively applied**."

Two consequences the build got wrong for a long time. **A matrix per animation cannot express this**
— summing components and then building one transform is not the same as building transforms and
combining them, and this used to build one per animation and then discard all but one. And
**`this` is the running sum**, not zero: Molang's `this` is "the value this expression will
ultimately write to", which in an additive system is what the animations before this one left there.
That is why the corpus writes `query.target_x_rotation - 110.0 - this` on every bone it aims —
**subtracting the accumulation and adding the result is how a pack says *set* in a system that only
adds.** Answering zero for `this` silently turns each of those into an offset.

Scale multiplies here rather than adding; the documentation does not separate it out, and no corpus
animation scales a bone two ways, so nothing observable distinguishes them. `TODO(SC-180)`.

#### A zero-length looping animation contributes nothing

**An animation of only constants has no keyframes, so its length is zero** — Mojang: "a single key
frame is created at t=0.0", and `animation_length` defaults to "time of last key frame". It therefore
finishes on the frame it starts, and `loop` decides what that means: `true` "loops back to t=0.0
when it finishes", `hold_on_last_frame` keeps the last pose. **This build drops the first and keeps
the second.**

That one line is what separates the corpus's two characters, and it took most of a day because
every other candidate had to go first:

| candidate | how it was eliminated |
|---|---|
| the hand the item is held in | tested both hands on the Bedrock client, for both characters: no change |
| `c.item_slot` unanswered | vanilla's own shield branches on `c.item_slot == 'main_hand'` |
| `c.is_first_person` | documented for `scripts.animate`, and used by that same shield |
| the composition rule | Mojang documents it, and it is now implemented as documented |
| conditions evaluated before `pre_animation` | explains too much — neither character's animation would play, and one demonstrably is posed by hers |

What is left is `loop`, and the corpus is pointed about it: `hold_on_last_frame` occurs **once in
thirty-two animation files**, on the first-person animation of the one character the Bedrock client
poses with it. The other's says `loop: true`, and hers does not show. With this rule and the
additive composition, both clients agree for both characters.

`TODO(SC-180)`: `AnimationIr` collapses `hold_on_last_frame` to `loop: false`, so a plain
non-looping animation cannot be told from a holding one. Bedrock ends both, but removes the pose of
one and keeps the other. No corpus animation is plainly non-looping, so nothing can see it yet.

`blend_weight` **is now parsed and applied** — §4.1.1. `override_previous_animation` is neither: it
is documented as "should the animation pose of the bone be set to the bind pose before applying this
animation", which is the escape hatch from the additive default. Neither appears in the surveyed
corpus. `TODO(SC-180)`.

**That sentence read "`blend_weight` and `override_previous_animation` are parsed and not applied"
for as long as this section has existed, and the first half of it was never true** — `AnimationFiles`
did not read the field at all, and `AnimationIr` had nowhere to put it. Nothing caught it because
nothing in the corpus writes the field, so the ledger, the code and this document agreed on a
capability none of them had. **A claim about a field no input exercises is worth checking against the
parser rather than against the other prose.**

#### Four rules have been in this position. Three were adopted without a source

This is the most-revised paragraph in the document, and the revisions are worth keeping because each
wrong rule was adopted the same way: it explained the screenshot in front of it, and **nobody looked
for documentation.** The rule that survives is the one that was read rather than inferred.

| rule | what it looked like |
|---|---|
| compose the matrices | a piggybacking character stood two blocks off the player's shoulder |
| add the channel values | the same character's companion **turned to face the player and stepped in front of him**, and the character's head detached from her body |
| the last one takes it | plausible for months, and wrong |
| **add the components, `this` carries** | Mojang's own wording, and the first that puts both characters of the corpus on screen correctly |

The first was replaced too eagerly. Its symptom was really §3.4.1's angle sense on the Y axis, found
days later, and once that was fixed the composition rule was never re-tested — so "the last one wins"
had been adopted on evidence that no longer stood.

The second was worse: it was adopted **from this document's own reasoning** — Bedrock's documented
default really is additive blending — against a rule that a person had already confirmed looked
right. A pack's first-person animation moves a root bone by `rotation [0, 180, 0]`,
`position [0, 0, -11]`; added to an idle that seats the character on the player's back, that is a
character standing in front of him facing backwards, which is exactly what appeared. Another
animation's `head` entry carries `position [8, 4, 0]`, and added to the idle's it takes the head off
the shoulders.

**Do not replace this rule on reasoning alone.** It has survived screenshots that the other two did
not. Replacing it needs a frame that this rule gets wrong, not an argument that another rule is more
principled.

**That frame has now been produced, and it is one character out of two.** A Bedrock first-person
capture shows the `body`-parented character filling the screen's edge at point-blank range, turned
side-on. Under this rule her first-person animation contributes nothing but a translation of a
wearer bone, so she stays where third person puts her: on the player's back, small, behind him. The
rule gets that frame wrong. It gets the other character's right — hers is confirmed unchanged
between the views, which is what the rule predicts for her, because every bone her first-person
animation names is named again by the idle.

So the rule was not simply wrong; **it fired when it should not, for one of the two.**

**And the resolution was that the animation never plays at all.** With the documented rule
implemented, the other character — whose first-person placement matches the Bedrock client — spun
half a turn and stepped through the camera, because her first-person animation writes
`root3: rotation [0, 180, 0]` and nothing else writes that channel. The client does not do that. So:

> the documented rule is right, **and the corpus's first-person animation does not run in Bedrock.**

Its condition is `v.main_hand && c.is_first_person`, with `v.main_hand = c.item_slot == 'main_hand'`
assigned in `pre_animation`. `context.is_first_person` is documented in `scripts.animate` and works.
A conjunction whose right half works and whose whole is false has a false left half — and
`context.item_slot` is documented in exactly one place, inside a bone's `binding`, and is absent
from Mojang's list of 315 queries. **The pack's author wrote a first-person pose that has never been
drawn.** §4.4 records what follows for this build.

That also retires the `loop` question this section carried: `hold_on_last_frame` occurring exactly
once in thirty-two files, on that animation, is a coincidence of authorship and not a mechanism.

`TODO(SC-180)`: the one thing this rule does not explain. In the Bedrock client a character's head
follows the player's gaze in third person and does **not** in first person, and under this rule the
head is taken by the idle in both — so it tracks in both. The first-person animation does name that
bone, with a constant, so a per-view or per-channel refinement would explain it; nothing yet says
which, and neither of the two rules above explains it either.

**A refinement was very nearly adopted here and is not needed.** Two attachables of one pack, whose
`scripts.animate` lists have the same shape, behave differently in first person: one turns to face
the camera, the other does not. No composition rule can tell them apart — they play the same kinds of
entries in the same order, and the hand they are held in was ruled out on the Bedrock client. The
difference is not in the composition at all; it is §4.2.1, a wearer bone that only one of them hangs
off and that only first person drives. **A rule that cannot distinguish two cases is evidence that
the distinction is somewhere else, not that the rule needs another clause.**

### 4.1.1 A condition in `scripts.animate` is an amount, not a switch

**The Molang expression beside an entry of `scripts.animate` is how MUCH of that animation applies,
not whether it applies.** Mojang states it, in the passage that explains why a tutorial's animation
plays only once:

> "The reason for that is the fact that **the query in the scripts section is only a blend value for
> the animation. It defines 'how much' the animation plays, not when it plays and when it doesn't.**
> That's why the animation will start playing once `!query.is_on_ground` is `true/1`, but it will
> never stop playing. It will just fade out once the value is `false/0` again, and the next time it
> will fade into the animation again. It won't play from the start again."

The same quantity is spelled `blend_weight` inside the animation file — "default = '1.0'. How much
this animation is blended with the others. 0.0 = off. 1.0 = fully apply all transforms. Can be an
expression." **They are one number said twice, from the two ends, so they multiply**: an animation
declared at half strength, played by an entry blending at half, contributes a quarter.

So a channel's contribution to §4.1's running sum is scaled by that number:

| channel | contribution | why not the obvious thing |
|---|---|---|
| rotation, position | `carried += blend × value` | — |
| scale | `carried ×= 1 + blend × (value − 1)` | **off must mean a factor of one.** `blend × value` would shrink every scaled bone to nothing the instant a pack faded an animation out |

**With `this`, a partial blend is a lerp and not an approximation of one.** The corpus's `set` idiom
is `target − this`; adding `blend × (target − carried)` to `carried` lands proportionally between
where the animation found the bone and where it aims it. That falls out of the two rules rather than
being arranged, which is the reason to believe it.

Nothing in the surveyed corpus writes a fraction here — every condition in it is a comparison, which
answers zero or one — so **this changes no frame of the corpus, and reading the conditions as
booleans was right for every input this build has seen.** It is wrong for vanilla's own entries,
which blend a walk cycle by `query.modified_move_speed`, and that is the case worth being correct
for before it arrives.

**The other half of the quoted passage is the clock, and it is now implemented.** Bedrock starts an
animation's own time when its blend first becomes non-zero and never restarts it — an animation that
fades out and back in resumes where it was. So the clock belongs to the **holder**, not to the
animation, which everyone carrying one of these items shares: `Playback` keys it by the `Playable`'s
identity and the renderer keeps one per entity id and slot.

**What that was costing was visible, in the corpus, on the character whose first-person placement is
the open question.** One of her animations runs for **six hundred seconds**: the head is written
awake until t≈300 and then holds a slept pose from t≈301.75 to t≈599.2, so roughly half of every
cycle is a character asleep. Nothing later overrides it — the idle that follows in `scripts.animate`
writes that bone's *position* only. Against the old clock, measured from when the client started, the
phase was the client's uptime modulo six hundred: she fell asleep five minutes after the game
launched rather than five minutes after being picked up, and could be asleep the instant she was
first drawn. **A head at a fixed odd angle is the symptom, and it reads as a posing bug rather than
as a clock** — which is why it went unreported through every session that looked at her.

`TODO(SC-180)`: the clock now exists, and three things that need it are still not read —
`anim_time_update` (whose default is `query.anim_time + query.delta_time`, i.e. exactly this),
`start_delay`, and `query.all_animations_finished`. They are parsing and query work now rather than
architecture.

A holder is forgotten after a minute without being drawn, which bounds the store on a busy server;
a player who walks out of render distance and back starts their animations afresh, as Bedrock does
when it stops and restarts drawing them.

Nor is the *fade* a duration here. `blend_transition` gives one, and it belongs to an animation
controller's states (§5); no documentation gives `scripts.animate` a fade time of its own, so this
build applies the blend as the expression answers it and does not invent a time constant to smooth
it with.

### 4.1.2 A segment is a curve when either of its ends says so, and a keyframe holds two values

**A keyframe carries a `pre` and a `post` — the value the channel arrives at and the value it leaves
with.** A segment therefore runs from its earlier keyframe's `post` to its later keyframe's `pre`,
and the two differ only where the pack wrote both, which is how an animation steps at an instant.
Reading `post` for both ends — which this build did — turns every step into a ramp on the incoming
edge.

**`lerp_mode` sits on the keyframe, not on the channel, and a segment is curved when EITHER of its
two ends asks for it.** The reference implementation is explicit about the direction of that test:
it takes the straight line only when the earlier keyframe is linear *and* the later one is linear or
a step, and falls to the spline when either says `catmullrom`. A reader that asked only the earlier
keyframe would sample the run-up to every eased landing as a line.

The spline is the **uniform, tension-half Catmull-Rom** through four values: the neighbour before the
segment, its two ends, and the neighbour after.

```
v0 = (p2 − p0) / 2                      p1 = the segment's earlier end (its `post`)
v1 = (p3 − p1) / 2                      p2 = its later end (that keyframe's `pre`)
f(t) = (2p1 − 2p2 + v0 + v1) t³
     + (−3p1 + 3p2 − 2v0 − v1) t²
     + v0 t + p1
```

**Uniform**, so the keyframes' times do not space the parameter: two keyframes a second apart and two
a frame apart shape the curve equally. That is what Bedrock's editor evaluates — it builds a
two-dimensional spline through the neighbouring keyframes and reads the value off it, which for four
points is exactly the above — and copying it matters more than the arithmetic being defensible alone.

Two rules about the neighbours:

| case | control point |
|---|---|
| no keyframe past that end | **the end itself**, which is the standard clamp and is what stops the curve overshooting outside every value the pack wrote |
| the segment's own end **steps** (two values) | the end itself again — the pack asked for a discontinuity there, and reaching across it for a tangent smooths out the thing it wrote |

**This was a real fidelity gap, not a hypothetical one: 233 keyframes across 22 files of the surveyed
corpus ask for `catmullrom`, and every one of them was sampled as a straight line.** The `pre`/`post`
half is the opposite case — no animation in that corpus writes two values — and is implemented
because the sampler now has to ask each end of a segment for a *side*, which makes the distinction
free rather than speculative.

`TODO(SC-180)`: a looping animation does not wrap its interpolation. The reference joins the last
keyframe to the first across the loop point, and reaches round the ends for the two outer control
points; this build holds the outermost keyframe instead. The channel does not know the animation's
length, which is where the wrap has to come from.

`TODO(SC-180)`: Bedrock's third mode, `step`, is not read. The reference holds the earlier keyframe's
value across the whole segment for it. It appears nowhere in the corpus, and the `pre`/`post` pair —
which does the same thing at a single instant — is the form these packs would reach for.

### 4.1.3 The offset does NOT ride the rotation — measured, on the Bedrock client, with probes

**The three channels of one bone are independent: the position translates, and the rotation and
scale turn and size the bone IN PLACE about its pivot.** The translation is outermost, exactly as
this build always had it before one afternoon's excursion.

**This is now a measurement, not a reading of prose.** Probe v9
(`spec/features/0005-attachables/probe/`, 100 % original content, a behaviour-pack item so the
attachable actually runs) split the corpus's heaviest keyframe — `rotation [0,-85,0]`,
`position [-32,-2,-9]`, `scale 1.2` — across three items and put marker bones on a Bedrock client:

| probe | Bedrock frame, third person | settles |
|---|---|---|
| position only | markers **two blocks to the player's RIGHT**, rest orientation | positions apply, unrotated; **−X = the player's right**, re-confirmed on screen |
| rotation + scale only | markers turn **in place**; the forward marker swings to the **player's LEFT** | rotation does not displace; **§3.4.1's Y sense is right**, measured directly for the first time |
| all three | markers at the **same spot as position-only**, turned in place, ×1.2 | the channels compose independently — **translate · rotate · scale, translation outermost** |

**The opposite order shipped for part of a day and this probe refuted it.** Mojang's sentence —
"Order of operations: vertices are translated, rotated, then scaled" — reads temptingly as the
vertex being moved first and the rotation then carrying the offset. Implemented that way, the full
probe would land front-left; the Bedrock frame has it right. It briefly *looked* confirmed because
it happened to put the misplaced character on the correct side of the screen — a coincidence that
survived until an instrument with no symmetry and no other animations measured the rule itself.
**An argument from a sentence has now lost to a frame twice in this feature** (§3.6 is the other).

#### What this reopens, and where the suspect now stands

With the semantics pinned, her `.hand` puts her **two blocks to the player's right, off the
first-person screen** — which is exactly where the full probe sits, invisible, in first person.
**And the Bedrock client shows her large, close and to the LEFT.** The probe with her own numbers
does not land where she appears, so **what the client draws for her cannot be her `.hand` playing.**

The one difference left between the probe and her attachable is **the condition**: v9 plays
unconditionally; hers plays through `v.main_hand && c.is_first_person`, with `v.main_hand` assigned
in `pre_animation`. That is precisely the mechanism §4.4 recorded as untested — **`scripts.animate`
conditions evaluated before `pre_animation`, or in a scope that cannot see its assignments** — and
if it holds, her first-person animation has never played on a Bedrock screen, her left-side
appearance is the rest pose seen from inside it, and the other character is unaffected because hers
never plays anyway (§4.1, zero-length). Probe v10 (three items: no animation at all / her exact
indirect condition / the same condition written inline) is built to read it. `TODO(SC-180)`.

### 4.2 Bones named after the wearer's are driven by the wearer

**An attachable's geometry may name bones after the player's own, and those bones are not the
pack's to pose — the wearer's skeleton drives them.** A halo's whole geometry is two bones:

```
head   parent -             pivot 0,24,0    0 cubes
halo   parent head          pivot 0,31,-1   1 cube
```

The `head` bone carries nothing. It exists to be bound: it sits at the player's head pivot, and the
ring hangs off it so that turning the head turns the ring. A build that treats it as an ordinary
bone draws a halo that stays put while the head turns underneath, which is exactly how it was
reported. Character attachables do the same one level up — a piggybacking model's root hangs off
cube-less `root`, `waist` and `body` bones.

**The answer differs per view, and only the caller knows which view it is.**

| view | a bone both name |
|---|---|
| third person | **composes** — the pack's pose stands, the wearer's transform goes outside it |
| first person | **replaces** — the wearer's stands, the pack's pose of that bone is discarded |

Third person composing is what makes a halo work: the ring hangs off a `head` the wearer turns, and
the pack is free to pose it as well.

First person replacing is newer and was arrived at the hard way. This section said *compose* in both
views, on one piece of evidence: a character's first-person animation moves the cube-less `body` she
hangs off by `[0, -1, -6]`, and that third of a block forward was read as what carries her head in
front of the first-person camera rather than behind it. **A Bedrock capture shows her head is not in
front of the camera.** The evidence was a claim about a frame nobody had looked at, and the frame
says the opposite — with that translation composed in, the client draws a wall of her clothing at
point-blank range where Bedrock draws her riding on the player's back.

Two independent checks agree, and both were made before the change went near a screen:

- Suppressing the animation entirely — a probe, by answering `c.is_first_person` zero — moved the
  frame **towards** the Bedrock client. Replacing reproduces that without lying about the query.
- The survey then puts her first-person extents exactly on her third-person ones, and leaves the
  other character of the same pack **byte-identical**, as a pack that poses no wearer bone must be.

**"The wearer drives them" is about which bones EXIST for the wearer to drive.** Who wins them is
this table, and it is passed per call site rather than decided inside the poser, because the two
render paths are the two views.

`head` and `body` are bound today. **The four limbs are not, and the reason is a trap worth
recording**: a Java `ModelPart` carries an absolute position, not a displacement, and only these two
rest at the origin. `rightArm` sits at `(-5, 2, 0)` when the player stands, so feeding its position
in as a displacement would throw every arm-bound attachable five units sideways — the binding needs
each part's rest position subtracted first.

### 4.2.1 The wearer is posed by a different animation set per view, and first person never touches `waist`

**The two views do not drive the same wearer bones.** Bedrock poses the player from one animation
controller with a state per view, and the first-person state and the third-person state share not one
animation between them:

| view | writes | leaves alone |
|---|---|---|
| first person | `body`, `head`, `rightarm`, `leftarm`, `rightitem`, `leftitem` | **`waist`**, `root`, the legs |
| third person | `waist`, `head`, both arms, both legs, `root`, and `body` only while attacking | — |

In first person one animation is unconditional, and it is the whole of what the torso does:

```
body  rotation [ target_x_rotation, target_y_rotation,       0 ]
head  rotation [ target_x_rotation, target_y_rotation + 180, 0 ]
```

Third person instead leaves `body` at rest, zeroes `waist`, and aims `head` from a separate
look-at animation that is **not played in first person at all**.

**This is the asymmetry §4.1 could not produce and does not have to.** The vanilla player skeleton is
`root -> waist -> body -> head, arms`. An attachable that rebuilds it hangs its character off one of
those bones, and *which one* is the whole difference between the two characters in §4.1:

| character's root parent | third person | first person |
|---|---|---|
| `body` | at rest, so the pack's pose stands | inherits the torso rotation above — turns |
| `waist` | zeroed, so the pack's pose stands | **nothing writes `waist`** — identical to third person |

Measured rather than argued. With the wearer's bones held at identity, `:testkit:survey` reports the
`waist`-parented character's first-person extents as **bit-identical to her third-person ones**, and
the `body`-parented one's as third person plus `[0, -1, -6]` — the one translation her first-person
animation contributes that the idle does not also name. Both match the Bedrock client except for the
rotation above, which nothing in this build supplies: `WearerSkeleton.upright` answers identity for
`body`.

**That also bounds the fix.** Because no first-person animation names `waist`, giving `body` its
rotation cannot move a `waist`-parented attachable, and the survey output for her must come back
byte-for-byte unchanged. A change to a shared seam that can be shown *not* to reach the one case
already confirmed correct is worth more than one that merely looks right in a screenshot.

**`query.target_*_rotation` does not track the view in first person — measured** (feeding the
player's real angles made the character swing where the Bedrock client holds her still). **But
constant is not the same as zero, and reading it as zero cost weeks.** That frame only proves the
value does not move; it says nothing about which constant it is. `body` was set to identity on that
reading.

**Probe v10 measured the constant: `body` carries a half turn in first person.** An animation-less
`body`-parented rig on the Bedrock client is visible in first person with its +X marker on the
screen's right, its −X marker on the left, and its forward marker straddling the camera — the whole
rig turned 180° about the player's centre, with no pack animation anywhere to confound it. Whether
the engine does that via `base_pose` with `target_y_rotation` answering 180, or upstream of Molang
entirely, is not measured; the half turn is. `FIRST_PERSON_WEARER` now answers `R_y(180)` for both
`body` and `head`, and the wearer's contribution combines with a pack's channels as one more
animation in the stack — translation outermost, §4.1.3 — so the corpus's `body [0,-1,-6]` still
pushes forward underneath it.

**What `body` does carry in first person is half a turn**, and the difference between that sentence
and the five fitted constants below is what makes it worth keeping. A half turn was tried three
times before and refuted every time — because the pack's own first-person animation was not running,
so the rotation had nothing to act on but the character's idle seat, and turning that put her on the
wrong side of the player. Once §4.1's `loop` rule lets that animation play, what the rotation acts on
is its `root2 position [-32, …]` — the two blocks sideways the pack asks for — and the Bedrock client
puts her on the side this rotation sends it to. **The same number, refuted three times and then
right, because what it multiplied changed.**

`waist`-parented content cannot see it, which is the check rather than the argument, and the other
character of the corpus came back unchanged on screen.

**Their history is kept below because the way the five were arrived at is the lesson, not the
numbers.**
Asked of the Bedrock client rather than of this paragraph — does the `body`-parented character stay
fixed on the screen as the view turns, or swing — the answer was *fixed*. A bone that tracked the
gaze would swing, so whatever `query.target_*_rotation` answers for a first-person player, what
survives into camera space here does not vary with it. §3.4.2's camera space already holds the model
to the screen; this bone supplies the rest.

**Which constant is open, and half a turn was tried and is wrong.** It was adopted because it
answered two complaints with one number — the character should face the camera, and should sit
further to the player's side than she does — and the survey agreed in figures:

```
body2   x   0.48 ..  10.07   ->   x  -10.07 ..  -0.48
head2   x  -4.40 ..  16.05   ->   x  -16.05 ..   4.40
```

**On screen she went to the wrong side, and still showed no face.** The argument left out *where*
the rotation happens. This bone pivots at `(0, 24, 0)` — the wearer's spine — and the character
hangs off it at `z + 6`, on his back. A yaw there does not turn her where she stands; it walks her
around him, `z + 6` to `z - 6`, which is a character stepping in front of the camera. That is
exactly the frame that came back: a wall of her clothing while crouched, a sliver at the screen edge
while not.

A quarter turn was then fitted to the same description and refuted before it reached a screen: the
survey puts her in **front** of the camera, not to the side. Two constants, two refutations, and the
second one is where the mistake became visible — **both were fitted to prose.** The description they
were fitted to ("further to the side, and turned towards the camera") was written down before the
angle convention of §3.4.1 and the wearer-bone composition of §4.2 were fixed, and **nobody had put
the two clients side by side for this character since.**

**Both were fitted to the wrong starting position, and that is the correction.** Each was applied on
top of §4.2's composed `body` translation — the character had already been dragged a third of a
block forward before the rotation reached her, so turning her about the spine swung her through the
camera. Once first person **replaces** wearer bones (§4.2), she starts from her third-person
placement instead, and the same quarter turn reads differently:

```
from   x  +0.48 .. +10.07   z  +2.65 ..  +9.98    riding on his back
to     x  +2.65 ..  +9.98   z -10.07 ..  -0.48    beside him, forward, close
```

That is where the capture puts her: at the screen's left, near enough to fill that edge. The
direction follows from the same capture, since Bedrock `+X` is the screen's left here.

**And that was refuted too.** On screen it went further from the client, not closer. Identity is what
the build holds.

**Three constants, three refutations, and the pattern is the finding.** All three moved the whole
model, and the frame that settles it cannot be reached that way: the character's **shield sits
correctly already** while her head does not appear at all. A rigid transform that brings the head
into view takes the shield with it. **So nothing about where this bone puts her is what remains
wrong** — the remaining divergence is inside her own pose, which is §4.1's business and not this
section's. What *is* settled here is the bound: `waist`-parented content comes back byte-identical
whatever this holds.

Two things the run did settle, and they are why the run was worth making:

- **The mechanism is real.** Changing this bone moved the `body`-parented character and **nothing
  else on the screen**; the `waist`-parented one was reported unchanged, as her extents predicted.
  §4.2.1's asymmetry is confirmed on a Bedrock-facing frame, not just in a survey.
- **Bedrock `+x` is the screen's left in this view**, and `-x` its right — read off the change, not
  argued from the axis table. Which is what the pack's own `position: [-32, …]` means when §4.1 calls
  it "the wrong direction": it really is the player's right.

`TODO(SC-180)`: what this bone carries. Settle it against a Bedrock first-person frame — one
screenshot fixes a position and a facing at once, where prose fixes neither.

Two vanilla facts fell out of the same reading and are recorded so they are not re-derived:

- `controller.animation.elytra.default` — which two of the corpus's three attachables name as
  `default_controller`, and which this build still reports unresolved because it is Mojang's file —
  is a five-state machine that plays whichever of `default`, `gliding`, `sneaking`, `sleeping`,
  `swimming` the **attachable's own** `animations` map defines. That is how the corpus's `sleeping`
  entries ever play: nothing in `scripts.animate` names them. **The third attachable ships its own
  controller and is now run** — §5.1, where the assumption that this was blocked on Mojang's file is
  corrected.
- The player's sneak pose is a **third-person** animation. First person does not tip the torso, so
  the crouch pitch `WearerSkeleton.upright` records as missing is not a divergence.

### 4.3 `query.target_*_rotation` is where the wearer is looking

Bedrock states these as the rotation needed to face the target; for something attached to a player,
that is the player's own head pitch and yaw — **in third person; in first person they answer zero**
(§3.4.2).

**They are the head's angles in the world, not relative to the body**, and the documentation is
specific about the sense: `query.target_*_rotation` is *identical to* `query.head_*_rotation`, whose
yaw runs `-179.9 .. 179.9` and wraps — a heading, not a difference — and whose pitch is `-89.9`
looking **up**. Vanilla corroborates it from the other side: the third-person look-at animation
writes the head with `relative_to: { "rotation": "entity" }`, a flag that exists precisely so a
world angle can be written into a bone expressed in its parent.

This build feeds the head's yaw **less the body's** in third person. That is not the documented
value, and it is left alone deliberately: the composition here has no `relative_to`, a person has
confirmed the corpus looks right in that view, and §4.1 records what replacing a confirmed rule with
a documented one costs. **It becomes a divergence to fix the moment `relative_to` is implemented**,
and not before — the two errors currently cancel. **They are what aims a limb**: the corpus poses a rifle
with `query.target_x_rotation - 110.0 - this` on an arm bone, and an unbound query answering zero
leaves the arm at a fixed angle regardless of where the player looks. That reads on screen as "the
arm is not doing anything".

`this` in such an expression is Molang's "the value this will ultimately write to". It answers zero
here. **That is a stub and it is not, on the evidence so far, observable**: the corpus uses `- this`
only on channels whose bone declares no rotation about that axis, where zero is the right answer.
It becomes wrong as soon as a pack aims a bone that has a declared angle on the same axis.

### 4.4 The corpus's first-person animation DOES run — closed by probe v10, reversing this section

**Probe v10 closed this the other way.** Three items on the Bedrock client, held in the main hand in
first person: one with no animation at all is **visible** — so an attachable whose animation does
not play is seen, not hidden; one with the corpus's exact `pre_animation` indirection is invisible;
one with the same condition written inline is invisible. Invisible here means *played and moved out
of view* (§4.1.3's probes put the same keyframe two blocks right and behind), and the rest probe
proves not-playing would have been visible. **Both condition forms fire. `pre_animation`'s
assignments are visible to `scripts.animate`. Her first-person animation plays on Bedrock.**

What actually distinguishes the clients' frames is §4.2.1's wearer half-turn, measured by the same
probe. The section below is kept as this document's most instructive wrong conclusion: it reversed
itself once already, and every paragraph of its reasoning was defensible at the time.

#### The earlier state of this section, kept for the record

**What is settled is the observation.** §4.1 records the frame: with the documented additive
composition implemented, playing that animation spins a character half a turn through the camera,
and the Bedrock client does not do that. So the condition
`v.main_hand && c.is_first_person` is false there, and this build reproduces the client by not
answering `c.item_slot`, which makes the left half false.

**That reason is wrong, and vanilla says so.** Mojang's own shield — a *held* attachable, the same
case as the corpus's — branches on exactly this query:

```json
"animations": [
  { "wield_main_hand_first_person": "c.item_slot == 'main_hand'" },
  { "wield_off_hand_first_person":  "c.item_slot != 'main_hand'" }
],
"transitions": [ { "third_person": "!c.is_first_person" } ]
```

`c.item_slot` therefore works in this context. It was ruled out because it is absent from Mojang's
list of 315 queries — **the list is incomplete, and an absence there is not evidence.** Recorded
because the same reasoning is available for every other query in that file.

**The difference between vanilla and the corpus is one indirection.** Vanilla writes `c.item_slot`
*inside the condition*. The corpus assigns `v.main_hand = c.item_slot == 'main_hand'` in
`pre_animation` and reads the variable. `TODO(SC-180)`: whether `scripts.animate` conditions are
evaluated before `pre_animation` has run, or in a scope that does not see its assignments. That
would leave `c.item_slot` working, make the corpus's condition false, and match every frame — but
nothing has tested it, and this section has already been wrong once for want of that.

**Until then this build has the right behaviour for the wrong reason, and says so here rather than
in a coverage note that would read as settled.**

**The instrument for that test now exists, and the surrounding facts have hardened.** Probe v9
(§4.1.3) played her exact keyframe unconditionally on the Bedrock client: it lands two blocks to
the player's right, out of the first-person view — so if her condition were true there, she would
be invisible, and she is not. The condition being **false** on Bedrock is now the only reading that
fits every frame. Probe v10 splits the mechanism: one item with no animation at all (where does a
rest pose sit in first person), one with her exact `pre_animation` indirection, one with the same
condition written inline. Whichever pair matches decides whether the break is the scope, the
timing, or the query itself. Note the shield's own `c.item_slot` lives in its animation
*controller*'s entries, not in `scripts.animate` — vanilla proves the query, not the placement.

**The field of view is not the variable, and that was measured.** The two clients are set to 60 and
70 degrees, and **changing either moves nothing** — so both draw this pass with a projection that
does not read the setting, which in Java is the known behaviour of the held-item pass. A character
who fills half this build's screen and a fifth of the client's is therefore a difference in geometry
or in that pass's own fixed projection, not in a user setting. `TODO(SC-180)`: which. Note that a
rotation of the wearer's `body` cannot be the answer either — it turns the character about the
player's spine, so her distance from the eye is invariant under it.

A consequence worth being explicit about, because it re-frames the whole first-person question:
**everything the corpus authored for first person — a two-block sideways offset, an eighty-five
degree turn, a scale of 1.2 — has never appeared on a Bedrock screen.** Four candidate values were
fitted to those numbers before this was known, and all four were refuted by captures. What first
person looks like is Bedrock's behaviour, not the pack's intent.

## 5. Animation controllers

A finite state machine: states with `animations[]` (each with a Molang blend weight),
`transitions[]` guarded by Molang conditions, `blend_transition`, `on_entry` and `on_exit` scripts,
and `particle_effects` / `sound_effects` per state. The initial state is `initial_state`, and
`default` when the pack names none.

**This is where a Bedrock pack says *when*.** §4.1.1 establishes that a condition in
`scripts.animate` cannot: it is an amount, and an animation it starts never restarts. Mojang's own
tutorial reaches for a controller at exactly that point — "if we want to start the animation every
time the query changes, we need a different approach. This is where animation controllers come in."

**Transitions are ordered and the first non-zero one wins.** Mojang: "the first to return non-zero is
the state to transition to". So the IR keeps a list; a map keyed by the target state would silently
decide between two guards that are both true by hash order.

### 5.1 The corpus's controllers are not the ones this document assumed

For most of this feature's life the note here read: the corpus's attachables name
`controller.animation.elytra.default`, that file is in Mojang's resource pack rather than in the
add-on, and so nothing can be done until a way to resolve it is found. **That is true of two of the
three attachables and false of the third, which ships its own controller in its own pack** — seven
states, its own transitions, and six of its states naming animations the attachable defines.

The consequence is that this was never blocked. **What was needed was to read the pack.**

| what the corpus names | where it lives |
|---|---|
| `controller.animation.elytra.default` (two attachables) | Mojang's resource pack. Not resolvable here, and not vendorable — constitution rule 10 |
| `controller.animation.hoshino_totem.default` (one attachable) | **in the add-on**, and now read |

Both cases behave the same way from the renderer: a name that resolves to nothing plays nothing.
That is also what the elytra controller would mostly do if it were resolved — its five states name
`default`, `gliding`, `sneaking`, `sleeping` and `swimming`, and the attachables that borrow it
define two of those five, so the other three are authored to draw nothing.

### 5.2 The machine remembers, one transition per frame

**The state is the holder's**, kept in the same `Playback` as the animation clocks (§4.1.1) and keyed
by entity id and slot. Each frame the machine takes **at most one transition**, from where the last
frame left it, and the first guard that fires wins — which is Bedrock's.

An earlier build had no memory and resolved the machine from its initial state every frame, chasing
transitions until none fired or a state repeated. It agreed with this for every controller in the
corpus, and the reason was structural rather than lucky: every state carries the transition back out
that its own guard's negation opens, and every state is one hop from the initial one, so a holder's
first frame lands in the right state either way. **It is recorded because "no rule can tell these
apart" is not the same as "the rule does not matter"** — it parts company for anything that depends
on how the entity got here, and the corpus contains one such case already:

One controller has a `walking` state whose way back is `!query.is_gliding`, true whenever the player
is not gliding, so `default → walking → default` is a cycle **in the file itself**. Bedrock takes one
transition per frame and flickers between the two; the memoryless version froze at the repeat. Both
states draw nothing, so nothing on screen distinguishes them — this build flickers because that is
what the client does, not because anything showed it.

Still not implemented: `blend_transition`, `on_entry` / `on_exit`, and
`query.all_animations_finished`. The first is a cross-fade whose length is a fact about the state
just left, which is now expressible and simply not read. `TODO(SC-180)`.

### 5.3 The queries a controller asks are about the wearer, and they used to read zero

A controller's guards are `query.is_sneaking`, `query.is_in_water`, `query.is_swimming`,
`query.is_gliding`, `query.is_sleeping`, `query.is_onfire` — and nothing else, in the whole corpus.
Every one of them answered zero until now, which is why a pack that ships a sneaking pose, a
swimming pose and a burning pose drew the standing one in all four situations.

They are answered from the render state in third person and from the player in first, which are the
same six facts from the two sources the two views have. `Pose`'s constants and
`LivingEntityRenderState`'s fields are identical on 1.21.11 and 26.2, checked against both jars.

**One query is deliberately left reading zero.** `query.is_speeding` guards the `walking` state
above and **appears in no version of Mojang's documentation**, so it answers zero on a Bedrock client
too and that state is unreachable on both. Answering it here would be this build inventing a feature
the pack's author never got — and it would make a screenshot of this build disagree with the Bedrock
client for a reason nobody could find later.

**The same pack has a plain typo in the same list**: `scripts.animate` plays `atsuiyo` and the
`animations` map defines `atuiyo`. It resolves to nothing, is reported as unresolved, and costs the
one entry. Constitution rule 5 — and the survey prints it, which is how it was found.

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
Lepus ships its own particle engine, simulated in version-free code.

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
world, and a raised `lepus.blockPool` produces more slots than any build-time file set covers.
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

#### 8.1.1 Rewriting the pack is not enough

**Binding must ask the client to reload its resources.** The client bakes every block model once,
during its resource load, on the way to the main menu — long before any world exists and therefore
long before any pack is bound. What it bakes is the pack as it stood then: every slot unbound, every
blockstate pointing at the empty model.

Binding rewrites the pack and nothing tells the client. The result is a bound block that is
**invisible**: its collision box and its outline are correct, because those are asked for per query
through the live reference (SC-150 §1), and its model is the one baked before it existed. It is worth
stating the symptom precisely, because it presents as a texture problem and is not one — the model
and the texture were both generated correctly.

The reload is requested **only when the pack's bytes actually changed**. Binding runs on every world
load and every activation change, and most of those rebuild byte-identical files; a reload is a
visible pause and one per world load would be a permanent tax for nothing. Comparing snapshots needs
a real comparison rather than `Map.equals`, whose `byte[]` values compare by identity and would
report every rebuild as a change.

It is requested on the **render thread**, from the server thread that binds. A resource reload
rebuilds every atlas and every baked model; starting one from the wrong thread races the frame being
drawn.

### 8.2 Texture bytes

Read at **bind time**, into memory, and served from the generated pack beside the model that names
them.

An earlier revision of this section said reading them at all would undo SC-100 §12 closing an
add-on archive. **That was wrong**, and the correction is worth keeping: nothing closes a loaded
add-on, so every archive is already held open for as long as the registry lives. There was no new
constraint to weigh — only when to read.

On demand would therefore work. It is still not what happens, because the resource manager asks on
its own thread at its own time and pinning an archive to that is a lifetime nobody is tracking. A
block texture is a few kilobytes and there is one per bound state.

`terrain_texture.json` is **merged across the enabled packs**, later winning, rather than consulted
per pack: Bedrock resolves a key against the whole enabled stack, and a behaviour pack naming a key
its companion resource pack declares is the normal shape of an `.mcaddon`.

Every step may come up empty and each one answers empty rather than failing. The block then draws
with the missing texture, which is visible and reportable; refusing a block over an absent picture
would be neither.

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
