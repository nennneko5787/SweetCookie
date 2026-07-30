# ADR-0004 — The render abstraction is designed from the newest version's constraints

**Status:** accepted
**Date:** 2026-07-30
**Affects:** SC-180, SC-220

## Context

The two supported Minecraft versions render very differently.

1.21.11 uses `MultiBufferSource` and `VertexConsumer`: buffers obtained mid-frame, mutated
immediately, with implicit ordering. 26.2 removed `MultiBufferSource` entirely for a submission model
— `FeatureRenderer<SUBMIT>`, `SubmitNode` — on a Vulkan-capable Blaze3D, where nodes are immutable,
submission is deferred, and static meshes are rewarded.

SweetCookie must render Bedrock geometry, skeletal animation, render controllers and a particle
engine on both. Roughly ninety per cent of that work — geometry traversal, bone matrices, animation
sampling, state machines, Molang, particle simulation — is genuinely version-independent, and
belongs in shared code behind an abstraction.

The abstraction's shape is therefore the highest-leverage decision in the client, and getting it
wrong means rewriting the client.

## Decision

Design the abstraction against **26.2's** constraints — immutable submission, no mid-frame mutation,
explicit mesh upload — and implement 1.21.11 as an emulation of it.

Implementations live in **per-version source directories**, not inline Stonecutter comments.

## Consequences

**Good.** A restrictive contract can always be satisfied by a permissive backend. 1.21.11 can trivially
implement "submit an immutable mesh" using `MultiBufferSource`. The reverse is impossible: an
interface that hands out a mutable buffer mid-frame has no implementation on 26.2. Designing from the
newer version also means future versions, which are trending further in that direction, need less
adaptation.

**Bad, and accepted.** The 1.21.11 backend does slightly more work than a native implementation would
— it must batch and upload where it could have streamed. Persistent mesh handles need lifetime
management that 1.21.11 does not otherwise require. And shared code cannot use any 1.21.11 convenience
that 26.2 lacks, even where it would be simpler.

## Alternatives considered

**Design from 1.21.11, adapt to 26.2.** The natural instinct, since 1.21.11 is the older and better
understood version. Rejected: it produces an interface 26.2 cannot implement, discovered late.

**Two independent renderers with no shared abstraction.** Rejected: it duplicates the ninety per cent
that is version-independent, which is where all the difficulty is.

**Inline Stonecutter comments in the renderer.** Rejected by constitution rule 12: two thousand lines
interleaved with `//? if >=26.2 {` is unmaintainable, and rendering is where the temptation peaks.

## Reversal cost

**High** — it is the client's foundation. This is why a throwaway spike implementing a single
textured cube on 26.2 precedes all other client work: the spike is cheap and it either validates the
interface's shape or invalidates it before anything is built on top.

`TODO`: this ADR's interface sketch in SC-180 §2 is provisional until that spike lands.
