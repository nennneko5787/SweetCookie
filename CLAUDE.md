# SweetCookie — agent instructions

Runs Minecraft **Bedrock** add-ons (`.mcaddon`/`.mcpack`) inside Minecraft **Java** Edition.
Fabric + NeoForge, MC 26.2 and 1.21.11, required on client and server.

**Respond to the user in Japanese. Everything you write into the repo is English**, except
`spec/normative/ja/**`, `docs/ja/**` and `spec/features/**`. See `spec/constitution.md` §11.

## Source of truth

`spec/` is normative — nothing else is. `docs/` is generated; never hand-edit
`docs/compatibility/**`. If code and spec disagree, the spec wins or the spec gets amended first.

Read before touching anything:

| | |
|---|---|
| `spec/constitution.md` | the 12 rules. Non-negotiable. |
| `spec/normative/SC-110-ir.md` | the intermediate representation everything parses into |
| `spec/normative/SC-120-registration.md` | why no Bedrock feature gets a registry entry, and the block slot pool |
| `spec/normative/SC-270-wire-protocol.md` | why custom content never gets a vanilla network ID |
| `spec/ids.md` | the ID scheme |
| `spec/process.md` | how a change flows through the repo |

## Before implementing anything

1. Read the SC- doc for your domain (`spec/normative/`).
2. Find the feature's entry in `spec/coverage/*.yaml`. **If it is absent, it is out of scope — stop
   and ask the user.** Do not invent coverage entries to justify work.
3. Create or continue `spec/features/NNNN-slug/` (`spec.md` → `plan.md` → `tasks.md`).

## Definition of done, per feature ID

- [ ] implementation class carries `@SpecImpl("SC-160#minecraft:behavior.melee_attack")`
- [ ] coverage entry updated: `impl`, `fields`, and a `fidelity` note if not fully faithful
- [ ] at least one conformance case under `spec/conformance/`, golden committed
- [ ] `./gradlew specAll` green
- [ ] **`status: implemented` is written by hand and verified by the build** (ADR-0011): it needs an
      `@SpecImpl` class, a conformance case that *passed*, no `fidelity` note, and an all-`ok`
      `fields` map. No tool edits `spec/coverage/**`. `partial` is never promoted

## Hard rules

- **No `net.minecraft.*` in `core/**`.** No Stonecutter `//?` comments in `core/**` either.
- **Never crash on bad add-on input.** An unknown component / goal / query / filter / format_version
  logs a diagnostic and becomes a no-op. One NPE in a 171-goal registry takes down the world. This
  outranks fidelity, always.
- **Never vendor anything from `Mojang/bedrock-samples`** (license: NOASSERTION). Fetch at build
  time, generate code from it, ship neither.
- Conformance add-ons must be 100 % original content. No copied community packs.
- Stonecutter `//?` is for divergences of **≤ 5 lines**. Anything bigger goes in a per-version source
  directory. Render code especially.
- **Never register anything named after a Bedrock feature** (SC-120). Items are one carrier item plus
  a data component; entities are a fixed set of types plus NBT; blocks bind to anonymous pool slots.
  Packs must attach and detach at runtime, per world.
- Custom content must never occupy a vanilla network registry ID (SC-270). If you find yourself
  writing a custom ID into a vanilla packet, stop.
- Logical identity (`sweetcookie:wizardry.magic_block`) and physical slot
  (`sweetcookie:block_16/0037`) are different things. Slots appear in chunk storage and the ledger,
  nowhere else — never in a spec, command, annotation or packet.

## Layout

```
core/                    SEPARATE BUILD (includeBuild). Minecraft-free, plain JUnit, --release 21.
                         api / format / molang / script / testkit.
src/main/java            MC-dependent, compiled by every node. Version-free and loader-free.
src/fabric/java          Fabric entry point + mixins   } added to the source set by that
src/neoforge/java        NeoForge entry point + mixins } node's buildscript
src/client/gfx-<ver>/    per-version render backends
build.<loader>.gradle.kts  the per-node buildscript
stonecutter.properties.toml per-node dependency versions
spec/                    normative specification (see above)
docs/                    generated compatibility tables + hand-written user guide
```

Stonecutter nodes are `<mcVersion>-<loader>`: `26.2-fabric`, `26.2-neoforge`, `1.21.11-fabric`,
`1.21.11-neoforge`. **There are no `common`/`fabric`/`neoforge` subprojects** — divergent code gets
a source directory, not a project.

`./gradlew chiseledCompile` compiles all four. `./gradlew --project-dir core build` runs the
Minecraft-free half in seconds.

Package root `net.nennneko5787.sweetcookie`. Mod id and resource namespace: `sweetcookie`.
