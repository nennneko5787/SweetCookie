# ADR-0009 — Platform abstraction is a hand-rolled `ServiceLoader`

**Status:** accepted
**Date:** 2026-07-30
**Affects:** SC-220, SC-230

## Context

SweetCookie targets Fabric and NeoForge, and separately targets multiple Minecraft versions. These
are two independent axes and conflating them produces unmaintainable predicates.

For the loader axis, three approaches are current in 2026:

- **Architectury API** plus `@ExpectPlatform` — least boilerplate, but requires the Architectury
  Gradle plugin at build time and, for the runtime API, that users install a second mod.
- **A hand-rolled `ServiceLoader`** — one interface, two implementations, one `META-INF/services`
  file per hook.
- **Per-loader duplication** with no abstraction.

The most widely used multi-loader template in 2026 uses neither Architectury API nor
`@ExpectPlatform`: `common` compiles against plain Minecraft through ModDevGradle's NeoForm mode,
and platform hooks are hand-rolled service lookups.

The relevant risk is schedule coupling. Architectury Loom's "26.1 support" issue sat open for around
six months after 26.1 shipped. A project blocked on a third party's Minecraft-drop cadence is a
project that cannot ship on release day.

## Decision

Hand-rolled `ServiceLoader`. No Architectury runtime dependency, no `@ExpectPlatform`, no
Architectury Gradle plugin.

Services are resolved once, eagerly, at mod init. Zero providers or two providers is a fatal startup
error naming the interface.

## Consequences

**Good.** No third-party dependency on the critical path, so a new Minecraft version is blocked only
on Fabric Loom and ModDevGradle, which ship promptly. Users install one mod. The mechanism works
identically under obfuscated and unobfuscated Minecraft, which matters because the two supported
versions straddle that seam. And it is plain Java — a contributor needs no framework knowledge.

**Bad, and accepted.** Boilerplate: an interface, two implementations and a services file per hook.
The service set has to be curated so it does not become a dumping ground, hence the explicit list in
SC-230 §3. And `ServiceLoader` resolution failures are runtime errors rather than compile errors,
which is why resolution is eager and fatal rather than lazy.

## Alternatives considered

**Architectury API with `@ExpectPlatform`.** Dramatically less boilerplate. Rejected on the coupling
argument: two single points of failure — the plugin's and the API's release cadence — for a saving
that is measured in typing.

**Architectury API as a runtime dependency only**, with hand-rolled hooks. Possible, but it forces
users to install a second mod for registry and event helpers we would largely write anyway.

**No abstraction, duplicate per loader.** Rejected: the duplicated code would be the interesting
code.

## Reversal cost

**Low.** Adopting Architectury later would mean deleting service interfaces and replacing call sites,
which is mechanical. This ADR records reasoning rather than locking anything down.
