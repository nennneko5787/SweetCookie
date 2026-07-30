# Contributing

Read [`spec/constitution.md`](spec/constitution.md) first. It is twelve rules, it overrides
everything else including the rest of the specification, and most review comments here are just a
pointer back to one of them.

---

## Getting a build

```bash
./gradlew --project-dir core build      # the Minecraft-free half. Seconds. Start here.
./gradlew chiseledCompile               # all four nodes. Slow the first time.
./gradlew chiseledBuild                 # all four jars
```

The first `chiseledCompile` downloads and decompiles Minecraft for four configurations and can take
half an hour. After that it is cached.

**Nodes** are `<minecraftVersion>-<loader>`: `26.2-fabric`, `26.2-neoforge`, `1.21.11-fabric`,
`1.21.11-neoforge`. `.sc_active_version` selects the one your IDE sees; switch it with
`./gradlew "Set active project to <node>"` or by editing the file.

Java 25 is required for the daemon, because 26.2 needs it. Gradle provisions toolchains itself.

**On Windows:** git does not track the executable bit, so a shell script added from Windows lands in
the repository as `100644` and every Linux CI job fails with `Permission denied`. This already
happened once, to `gradlew`. After adding a `.sh` file, run `git update-index --chmod=+x <file>` and
check with `git ls-files -s`.

## Where code goes

| | |
|---|---|
| `core/` | anything that does not need Minecraft. **A separate build** — it cannot see `net.minecraft.*` and that is enforced by the compiler, not by review. |
| `src/main/java` | Minecraft-dependent, compiled by every node |
| `src/fabric/java`, `src/neoforge/java` | loader-specific; only the matching nodes compile it |
| `src/client/gfx-<version>/` | per-version render backends |

If you want `net.minecraft.*` inside `core/`, the intermediate representation is missing something.
That is a bug in [SC-110](spec/normative/SC-110-ir.md), not a reason to move the file.

## Version and loader divergence

`//?` Stonecutter comments are for **five lines or fewer**. Anything larger goes behind a service
interface or into its own source directory. Render code always uses a directory — two thousand lines
interleaved with `//? if >=26.2 {` is unmaintainable, and rendering is where the temptation peaks.

Design version abstractions against the **newest** version's constraints and emulate them on older
ones. A restrictive contract can always be satisfied by a permissive backend; the reverse is
impossible, and discovering that late costs a rewrite. See [SC-220](spec/normative/SC-220-versioning.md)
and [ADR-0004](spec/adr/0004-render-abstraction-from-newest-version.md).

## The loop

Work is scoped by **feature identifier** — one entry in `spec/coverage/*.yaml`, e.g.
`minecraft:behavior.melee_attack`. If your change has no coverage entry, it is either infrastructure
or out of scope; see [`spec/process.md`](spec/process.md).

1. `spec/features/NNNN-slug/` — `spec.md`, then `plan.md`, then `tasks.md`. **Japanese is fine
   here**; everything else in the repo is English.
2. Amend the relevant `spec/normative/SC-*.md` **in its own commit**. The specification changes
   before the code lands, not after.
3. Write the conformance case. It fails. That is the point.
4. Implement, annotated `@SpecImpl("SC-nnn#<feature>")`.
5. Update the coverage entry — `impl`, `fields`, `fidelity`. **Never write `status: implemented`
   yourself**; `specReport` promotes it when the tests pass.
6. `./gradlew specAll && ./gradlew --project-dir core build`

## Things that will get a change rejected

- **Throwing on bad add-on input.** Unknown components, goals, queries, filters and
  `format_version`s log a diagnostic and become a no-op. One `NullPointerException` in a 171-goal
  registry takes down a world. This outranks fidelity.
- **A silent degradation.** If SweetCookie does less than Bedrock would, it says so with an
  `SCE-####` code naming the pack, the file and the feature.
- **Registering anything named after a Bedrock feature.** Items are one carrier item plus a data
  component; entities are a fixed set of types plus NBT; blocks bind to anonymous pool slots. Packs
  must attach and detach at runtime. See [SC-120](spec/normative/SC-120-registration.md).
- **Putting custom content on the wire.** Every custom block, item and entity travels as a vanilla
  carrier plus a name-addressed sideband. This is what makes ViaVersion work perfectly instead of
  not at all. See [SC-270](spec/normative/SC-270-wire-protocol.md).
- **Confusing logical identity with a storage slot.** `sweetcookie:wizardry.magic_block` is the
  identity; `sweetcookie:block_16/0037` is where it happens to live in this world's chunk palettes.
  Slots appear in the ledger and nowhere else.
- **Copying GPL code.** Blockbench and Snowstorm are the de-facto specifications for the model and
  particle formats and you are welcome to read them. You may not copy them. A file informed by one
  says so in a comment and asserts that the implementation is independent — if you cannot say that
  honestly, the code does not go in.
- **Vendoring anything from `Mojang/bedrock-samples`.** It is fetched at build time and used only to
  generate code.
- **A conformance add-on that is not 100 % original.** Not a community pack, not a vanilla file, not
  a fragment.
- **Marking your own work `implemented`.**

## Scope

The feature vocabulary is roughly 2 500 identifiers and much of it is used by nobody. **Implementing
the specification in order is how this project dies.** Work is driven by what the acceptance add-ons
actually use, by user diagnostics, and by upstream additions — in that order.

If you want to work on something and are not sure it is in scope, ask before writing code. "It has a
coverage entry" is the test.

## Commits

English, imperative. Reference the specification identifier when there is one:
`SC-150: resolve block permutations at bind time`.

Specification changes are their own commit so they can be read and reverted alone. They may share a
pull request with the code that implements them.
