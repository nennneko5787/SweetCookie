# ADR-0005 — Scripting is an optional companion mod on GraalJS

**Status:** accepted
**Date:** 2026-07-30
**Affects:** SC-200, SC-260

## Context

Bedrock's Script API is executed by a QuickJS runtime inside the game. `@minecraft/server` 2.8.0
alone exports 439 classes across 25 600 lines of type declarations, plus `-server-ui`, `-server-net`,
`-server-admin` and `-gametest`.

Reimplementing it is a project of comparable size to the rest of SweetCookie. Meanwhile, a large
fraction of real add-ons use no scripting at all, and every JSON-only add-on would pay for a
JavaScript engine it never invokes.

Three engine options exist on the JVM. GraalJS is modern and complete but drags in Truffle, is a
large artifact, and runs interpreted on a stock JDK with unmeasured performance. Rhino is small but
ES2015-era, while Bedrock scripts are ES modules using current syntax. A QuickJS JNI binding would
match Bedrock's semantics most closely, including its quirks, but requires native artifacts per
platform — a genuine distribution problem for a Minecraft mod.

Scripting is also the largest attack surface in the project: it is arbitrary code from an untrusted
source, and on a client that source may be a server the user just joined.

## Decision

Define a `JsEngine` SPI in the main jar. Ship the implementation as a **separate, optional companion
mod** backed by **GraalJS**.

Support only `@minecraft/server` core and `@minecraft/server-ui`. `-server-net` and `-server-admin`
are not supported, for security as much as scope: they grant HTTP and secret access to pack code.

Sequence scripting last. Phase 1 may ship with none at all.

## Consequences

**Good.** Users who want JSON add-ons do not download a JavaScript engine. The engine can be replaced
without touching the main jar. Security review is confined to one artifact that a cautious operator
can simply not install. Scripting can slip without blocking anything else.

**Bad, and accepted.** Two artifacts to build, version and publish. A pack whose scripting half is
absent works partially, which needs clear diagnostics so users understand why. And the split has to
be designed in from the start — retrofitting an SPI after the API is embedded is much harder.

## Alternatives considered

**Rhino.** Smaller and simpler, but ES2015-era. Bedrock scripts use ES modules and modern syntax, so
this would mean transpiling pack code, which is both fragile and slow.

**QuickJS via JNI or Panama.** Closest to Bedrock's real semantics, including behaviours packs may
accidentally depend on. Rejected for 0.x on distribution grounds: native binaries per platform in a
Minecraft mod is a support burden out of proportion to the benefit. Worth revisiting if GraalJS
performance proves unacceptable.

**No scripting, ever.** Tempting, and it would be defensible. Rejected because
`BlockComponentRegistry` and `ItemComponentRegistry` mean modern add-ons increasingly put real
behaviour in scripts, and refusing them would cap how much of the ecosystem can ever work.

## Reversal cost

**Low**, deliberately. The SPI exists so the engine can be swapped, and the companion split can be
collapsed into the main jar if it proves to be more trouble than it is worth. This ADR exists to
record the reasoning, not because it is expensive to undo.

`TODO`: measure GraalJS interpreted-mode performance and artifact size, including whether ICU4J can
be excluded, before committing to it in a release.
