# SC-220 — Version abstraction rules

**Status:** complete · **Since:** 0.1.0 · **Supersedes:** —

How SweetCookie spans multiple Minecraft versions, what may diverge and where, and the checklist for
adding a version. Constitution rule 12.

---

## 1. Supported versions

A **node** is one (Minecraft version × loader) pair. Four exist:

| Node | Obfuscated | Java | Fabric plugin | Notes |
|---|---|---|---|---|
| `1.21.11-fabric` / `1.21.11-neoforge` | yes | 21 | `net.fabricmc.fabric-loom-remap` | last obfuscated release; last Yarn-supported |
| `26.2-fabric` / `26.2-neoforge` | **no** | **25** | `net.fabricmc.fabric-loom` | Vulkan-capable Blaze3D; `MultiBufferSource` removed |

Mojang dropped obfuscation at 26.1. The two Minecraft versions therefore straddle a hard toolchain
discontinuity: different mapping requirements, different Java version, different Gradle plugin id.
This is deliberate — building across the seam from day one is what stops the abstraction from being
quietly wrong.

`dev.kikugie.loom-back-compat` selects the Loom flavour per node, so one Fabric buildscript spans
the seam. Both flavours share a version line (1.17.17 at time of writing), which removes a risk this
document previously carried: the `-remap` variant is maintained in lockstep with mainline Loom, not
frozen on an older Gradle.

### 1.1 The version pair is unusually cheap to span

Mojang renamed `ResourceLocation` to `Identifier` **in 1.21.11 itself** — the last obfuscated
release — as part of retiring Yarn and adopting the community's familiar names. Both supported
nodes therefore use the post-rename vocabulary, and no source-level renaming is needed between them.

This will not hold if a node below 1.21.11 is added later. That is a cost of the *next* version, not
of the current pair, and §6 step 3 is where it surfaces.

More nodes will be added. §6 is the checklist and it is the acceptance criterion for this document:
if adding a version requires touching anything not on that list, the abstraction is broken and that
is the bug.

## 2. The three mechanisms

Ordered by preference. Use the earliest one that works.

### 2.1 Write version-free code (always try this first)

The overwhelming majority of this project does not need to know its Minecraft version. All of
`core/` (constitution rule 3), and above it: geometry traversal, bone matrices, animation sampling,
animation-controller state machines, render-controller evaluation, Molang, Snowstorm simulation,
filter evaluation, the entity component system, permutation resolution, the sideband protocol.

**Target: under 10 % of the codebase is version-conditional, and under 10 % of client code.**
Exceeding that is a design smell, not an inevitability.

### 2.2 A service interface (the default for real divergence)

When two versions genuinely need different code, put the difference behind an interface in
version-free code and provide an implementation per version.

The interface **MUST** be designed against the **newest** version's constraints, then emulated on
older ones — never the reverse. A restrictive contract can always be satisfied by a permissive
backend; a permissive contract cannot be satisfied by a restrictive one.

Interfaces are resolved by the platform service loader (SC-230), which handles version and loader
selection identically.

**Introduce the interface when the divergence is real, not when it is anticipated.** Rendering was
this document's worked example of an unavoidable abstraction, and the spike found it was not one:
`submitCustomGeometry` is byte-identical on both versions and shared code calls it directly
(ADR-0010, SC-180 §2). An abstraction built for a divergence that has not happened is guaranteed to
be the wrong shape when one does, and — worse — it is never exercised, so it is silently wrong by
the time it is needed.

The rule about designing against the newest version still governs wherever an interface *is*
warranted; it just does not license inventing one in advance.

### 2.3 Stonecutter comments (small, local divergence only)

```java
//? if >=26.1 {
gui.setScreen(screen);
//?} else
/*minecraft.setScreen(screen);*/
```

Permitted **only** for a divergence of **five lines or fewer**. Anything larger goes in §2.2 or a
per-version source directory (§3).

Prohibited entirely:

- anywhere in `core/**`;
- in any public API signature — a method whose parameter or return type is version-conditional
  cannot be called from version-free code, which defeats the point;
- in `spec/**` examples, since a specification describes behaviour rather than a build;
- more than three `//?` blocks in one file. Four means the file wants splitting.

Version predicates use the node's Minecraft version, never a Java version or a loader name. Loader
differences are SC-230's job and mixing the two axes in one predicate is a defect.

## 3. Source layout

There are **no `common` / `fabric` / `neoforge` subprojects.** Each node is a single Gradle project
over one shared source tree, and divergent code gets its own directory added to that node's source
set by its buildscript:

```
src/main/java            compiled by EVERY node - version-free and loader-free
src/fabric/java          added by build.fabric.gradle.kts        }  the LOADER axis,
src/neoforge/java        added by build.neoforge.gradle.kts      }  named by loader
src/1.21.11/java         added by the 1.21.11 nodes              }  the VERSION axis,
src/26.2/java            added by the 26.2 nodes                 }  named by version
```

The two axes are named the same way, by the thing that selects them, and a node adds one directory
from each. An earlier revision called the version axis `src/client/gfx-<version>`, which came from
ADR-0004's assumption that geometry rendering would be the divergence that mattered. ADR-0010
removed that, and the directory's first real inhabitant turned out to be a settings screen — neither
graphics nor, on a dedicated server, client. The name was describing a plan rather than its
contents.

This is simpler than subprojects and gives the same physical separation: a Fabric-only class cannot
be referenced accidentally from shared code, because the NeoForge nodes never compile it. Each
divergent implementation satisfies the same version-free interface, and none is referenced by name
from version-free code.

`core/` is separate again — a **composite build** (`includeBuild("core")`), not a subproject. It has
its own settings file, its own repositories and no Minecraft plugins, so constitution rule 3 is
enforced by the build graph rather than by review.

**Render code in particular MUST use a directory**, not §2.3. Two thousand lines interleaved with
`//? if >=26.2 {` is unmaintainable and rendering is exactly where the temptation peaks.

Expected version-split surface, now that the spike has narrowed it: the particle submission hook,
the block-model and item submission hooks, the entity-renderer entry point, and the packet-encode
mixin targets (SC-270). **Geometry submission is not on this list** — it is identical on both
versions (SC-180 §2). Anything else appearing here deserves scrutiny in review.

## 4. Mixins

Mixin targets are the least stable thing in the project — SC-270's encode-time substitution requires
hooking chunk serialisation, block updates, entity spawn and `ItemStack` encoding, and those move.

Rules:

- A mixin **MUST** be as small as possible and **MUST** delegate immediately to version-free code.
  Logic inside a mixin is logic that has to be written twice.
- Mixins live in the loader modules (`fabric/`, `neoforge/`), never in `common/`, because their
  configuration and tooling are loader-specific.
- Every mixin **MUST** carry a comment naming what it hooks and why, and the SC- document that
  requires it.
- A mixin that fails to apply **MUST** fail the build, not degrade at runtime. A silently missing
  packet-encode hook means custom content leaking onto the wire, which is a correctness failure, not
  a cosmetic one.

## 5. Version-conditional behaviour is forbidden

The build may differ per version. **Observable behaviour may not.**

If a feature works on 26.2 and not on 1.21.11, that is a coverage-ledger fact — a `fidelity` note on
the affected entries — not a silent difference. There is no `if (version >= X)` in behavioural code.

The IR is version-free by construction (SC-110): nothing downstream of it may branch on a Minecraft
version to decide *what* to do, only *how* to do it.

## 6. Adding a Minecraft version

The complete checklist. If a step not on this list is needed, file it as a bug against this
document.

1. Add one argument to the `match(...)` call in `settings.gradle.kts`, which creates one node per
   loader: `match("<mcVersion>", "fabric", "neoforge")`.
2. Add the dependency versions for those nodes to `stonecutter.properties.toml`.
3. Run `./gradlew chiseledCompile` and fix what does not compile, preferring §2.1, then §2.2, then
   §2.3. Adding a version **below 1.21.11** additionally needs `replacements.string` rules for the
   `ResourceLocation` → `Identifier` rename (§1.1).
4. If a per-version source directory is needed, add it to that node's buildscript.
5. Update the carrier set assertion (SC-270 §4) — every carrier must exist in the new version.
6. Run the full conformance suite on the new node, including the Via equivalence test (SC-270 §13)
   against every other supported node in both directions.
7. Add the node to the CI matrix.
8. Update the table in §1 and the README.

Steps 1 and 2 are the only ones that touch shared files. Steps 3–5 are the real work and their size
is the measure of whether this document is being followed.

## 7. Dropping a Minecraft version

Remove the `match(...)` argument, its `stonecutter.properties.toml` sections, and any per-version
source directory that no other node uses. Then **remove the now-dead `//?` branches** — a
`//? if <1.21` block whose
lowest node is 26.1 is dead code that misleads readers. `specValidate` reports Stonecutter
predicates that no configured node can satisfy.

## 8. Java version

`core/**` compiles with `--release 21` regardless of node, because Java 21 bytecode runs on the Java
25 runtime that 26.2 requires. The cost is no Java 25 language features in the format layer, which
is accepted.

`common/`, `fabric/` and `neoforge/` use the node's own Java version via a Gradle toolchain.

## 9. Testing contract

- CI builds **every** `{loader × node}` combination on every push. A change may land on one
  combination first, but **a change that breaks another combination's build is rejected.**
- The conformance suite runs on every node.
- The Via equivalence test (SC-270 §13) runs across node pairs, which is the only test that can
  prove the version abstraction has not leaked into behaviour.

Deferring cross-version parity is how a project discovers at month nine that its render abstraction
does not fit. It is checked from the first milestone that produces a jar.
