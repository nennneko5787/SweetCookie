# ADR-0001 — Format parsing has no Minecraft dependency

**Status:** accepted
**Date:** 2026-07-30
**Affects:** SC-000, SC-100, SC-110, SC-220

## Context

The add-on format parsing layer is the largest single body of code in this project: roughly 120
entity components, 171 AI goals, 106 filter tests, 44 item components, 32 block components, and
around forty content kinds each with several `format_version` breakpoints.

It is also, by construction, entirely independent of Minecraft. It converts JSON written by Bedrock
add-on authors into data structures. Nothing about that requires `net.minecraft.*`.

The project must support two Minecraft versions now and more later, across two loaders. Any code
inside the Stonecutter tree pays a per-line tax in build complexity and a per-file tax in the
temptation to write `//? if` comments.

## Decision

Put format parsing in `core/`: plain `java-library` subprojects with no Minecraft on the classpath,
no loader API, no Stonecutter, compiled with `--release 21`, tested with plain JUnit.

`core/**` referencing `net.minecraft.*` **does not compile**. That is the enforcement mechanism.

## Consequences

**Good.** The parser suite runs in seconds in CI on every commit, with no game bootstrap. A
contributor adding a component parser needs no working modding toolchain. Forty thousand lines stay
free of version-conditional comments. The module is publishable on its own, which may be useful to
other projects.

**Bad, and accepted.** The intermediate representation must be expressive enough to describe
everything an add-on can say without borrowing Minecraft's vocabulary — no `BlockState`, no
`ResourceLocation`, no `Goal`. That makes SC-110 the largest document in the specification and adds
a translation layer that would not otherwise exist. Some duplication between IR types and Minecraft
types is unavoidable.

`core/` also cannot log, since it must not depend on a logging framework; it returns diagnostics as
values instead (SC-000 §10). This is more disciplined than the alternative but it is more code.

## Alternatives considered

**Parse directly into Minecraft types.** Less code, and the translation layer disappears. Rejected:
it would put the entire parser inside the Stonecutter tree, make it untestable without a game, and
guarantee that Minecraft-version concerns leak into `format_version` handling — two version axes
tangled in one place.

**A documented-but-unenforced boundary.** Rejected on experience: a boundary that only a review can
catch does not survive eighteen months and several contributors.

## Reversal cost

**High.** Every IR type and every parser would need rewriting. This is a foundational decision and is
treated as effectively permanent.
