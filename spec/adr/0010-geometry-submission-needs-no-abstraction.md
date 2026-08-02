# ADR-0010 — Geometry submission needs no version abstraction

**Status:** accepted
**Date:** 2026-07-31
**Affects:** SC-180, SC-220
**Supersedes:** ADR-0004

## Context

ADR-0004 committed to designing the render abstraction against 26.2's constraints and emulating it
on 1.21.11. It rested on a specific factual claim:

> 26.2 removed `MultiBufferSource` for a submission model with immutable nodes and no mid-frame
> mutation. Design `GfxSink` from 26.2's rules and 1.21.11 implements it easily; design it from
> 1.21.11's and 26.2 is impossible.

That produced SC-180 section 2's interface: `GfxBackend.uploadMesh` returning an opaque
`MeshHandle`, `GfxSink.mesh(...)`, and a `RenderLayerRef` token that was `RenderType` on 1.21.11 and
`RenderPipeline` on 26.2. Two backends in per-version source directories, roughly 600 lines each.

The ADR flagged the claim as unverified and made a spike a precondition for building on it. The
spike ran: read both `minecraft-merged` jars with `javap` rather than trusting release notes, then
write the geometry path in the **shared** source tree and let the compiler decide.

## What the spike found

The premise was wrong in three ways.

**`SubmitNodeCollector` is already in 1.21.11.** The renderer rewrite landed at 1.21.9, not 26.x.
Both supported versions have the submission API; 26.2 merely finished the job by deleting
`MultiBufferSource`.

**Submission did not abolish vertex writing, it deferred it.**
`submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)` takes a callback, and
`CustomGeometryRenderer.render(PoseStack.Pose, VertexConsumer)` hands back a live `VertexConsumer`.
The signature is byte-identical on both versions, as is `VertexConsumer` itself.

**`RenderType` is the layer token on both**, at the same package
(`net.minecraft.client.renderer.rendertype.RenderType`), as are `Identifier`, `PoseStack` and
`VertexConsumer`. There is no `RenderType`-versus-`RenderPipeline` split to abstract over.

`src/main/java/.../client/render/BedrockCubeSubmitter.java` compiles unchanged on all four nodes.

## Decision

**Call `submitCustomGeometry` directly from shared code. Build no abstraction for the geometry
path.** Delete the `GfxBackend`, `GfxSink`, `MeshHandle` and `RenderLayerRef` design from SC-180;
it solved a problem that does not exist.

Version divergence in rendering is confined to the parts that genuinely differ, and those are named
rather than guessed at:

| Path | 1.21.11 vs 26.2 |
|---|---|
| `submitCustomGeometry` | **identical** — this is the path Lepus lives on |
| `submitModelPart`, `submitModel` | differing overloads |
| `submitBlockModel` | `BlockStateModel` vs `List<BlockStateModelPart>` |
| `submitItem` | `BakedQuad` moved package |
| Particles | `submitParticleGroup(ParticleGroupRenderer)` vs `submitQuadParticleGroup(QuadParticleRenderState)` |
| `submitBlock` | removed in 26.2 |
| `submitNameTag` | one extra parameter in 1.21.11 |

Each gets an abstraction **when it is first needed**, sized to the actual difference.

## Consequences

**Good.** Roughly 1,200 lines of planned backend code do not get written. The largest client risk in
the project is closed. And the callback shape suits Lepus better than the imagined one would
have: Bedrock render controllers choose geometry, texture and material *per frame* through Molang,
so an upload-immutable-meshes API would have fought the format the whole way.

**Bad, and accepted.** Shared render code now references Minecraft types directly, so a future
version that *does* diverge here will require introducing the abstraction late, against existing
call sites. That is a real cost and it is the right trade: an abstraction built for a divergence
that has not happened is guaranteed to be the wrong shape when one does.

**What survives from ADR-0004.** The general rule — design a version abstraction against the newest
version's constraints, because a restrictive contract can be satisfied by a permissive backend and
not the reverse — is still sound and still applies to the divergences listed above. What failed was
not the rule but the input: the constraints were inferred from release notes and a research summary
instead of read from the artifact.

## Alternatives considered

**Keep the abstraction anyway, for future versions.** Rejected on the reasoning above, and because
`specLanguage`-style dead-code rot is worse than a late refactor: an unused seam is not exercised,
so it is silently wrong by the time it is needed.

**Abstract only the diverging paths now.** Premature — none of them is implemented yet, and their
shape will be clearer once particles and block models exist.

## Reversal cost

**Low.** Introducing an interface over `submitCustomGeometry` later is mechanical, because every
call site goes through the same three arguments.

## What this says about the process

The plan called this the second-largest risk in the project and made the spike a precondition rather
than a task. That was right, and the correction cost two hours instead of a client rewrite. It is
also the second time in this project that a confident secondary source was wrong about a version
fact — the first was `fabric-loom-remap` being pinned to 1.14.x. **Read the artifact.**
