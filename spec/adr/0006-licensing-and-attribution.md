# ADR-0006 — Licensing and attribution

**Status:** accepted
**Date:** 2026-07-30, licence settled 2026-07-31
**Affects:** all

## Context

Lepus interoperates with a legally heterogeneous ecosystem, and some of the most useful
reference material is under licences that constrain what may be read, copied or shipped.

| Source | Licence | Usable how |
|---|---|---|
| `Mojang/bedrock-samples` — schemas, Molang query list, vanilla data | **NOASSERTION** (Mojang terms) | build-time code generation only |
| `GeyserMC/Geyser`, `mappings`, `Hydraulic`, `MCProtocolLib` | MIT | reference and adapt with attribution |
| `EaseCation/FabricRock` | MIT | reference and adapt with attribution |
| `unnamed/hephaestus-engine`, `unnamed/creative`, `team.unnamed:mocha` | MIT | depend on, adapt with attribution |
| `CloudburstMC/Protocol` | Apache-2.0 | depend on, with attribution |
| `GeyserMC/Rainbow` | **GPL-3.0** | its *output schema* may be matched; its code may not be copied |
| `JannisX11/blockbench`, `snowstorm` | **GPL-3.0** | read to understand the format; **never copy** |
| `Kas-tle/java2bedrock.sh` | **AGPL-3.0** | do not copy |
| `AllayMC/Allay`, `PowerNukkitX` | LGPL-3.0 | reference; linking has conditions |
| `CloudburstMC/Nukkit` | GPL-3.0 | reference only |

Blockbench is the de-facto specification for the `.geo.json` format and its animation files.
Understanding it requires reading GPL code. Reading to learn a **file format** is not derivation of
the code; reimplementing that format independently is legitimate. But the distinction has to be
maintained deliberately and be defensible, because the risk is real and asymmetric.

## Decision — the parts that are settled

1. **Ship no Mojang content.** `bedrock-samples` is fetched at build time, pinned by commit SHA and
   per-file hash, and used only to generate code. Neither the metadata nor generated copies of
   Mojang's data appear in a released artifact. Generated *code* derived from a schema is committed
   so the build works offline. Constitution rule 10.
2. **GPL and AGPL sources are read, never copied.** Blockbench, Snowstorm, Rainbow and
   `java2bedrock.sh` may be consulted to understand a format. Any file whose implementation was
   informed by them carries a comment saying what was consulted and stating that the implementation
   is independent. If that cannot be said honestly, the code does not go in.
3. **MIT and Apache attributions are preserved** in `NOTICE`, per file where code was adapted.
4. **Conformance add-ons are 100 % original content.** No community packs, no vanilla files, not even
   a fragment. Constitution rule 10.
5. **No AGPL dependency, ever.** Its network clause is incompatible with how Minecraft servers are
   operated.

## Decision — the project licence

**MIT**, chosen by the maintainer.

The alternatives were LGPL-3.0-or-later (modifications stay open, other mods may still depend on it,
but more friction for modpack and server redistribution) and GPL-3.0 (strongest copyleft, but it
would force every dependent mod to be GPL, making Lepus unusable as a library).

MIT matches the projects this one interoperates with and borrows most from — Geyser, FabricRock,
hephaestus, mocha — which matters if code ever flows back upstream, and a compatibility layer
benefits from being maximally embeddable.

One consequence worth naming: `com.viaversion:viaversion-api` is GPL-3.0. It is a **`compileOnly`**
dependency used for optional runtime detection; no ViaVersion code is linked into or distributed
with Lepus, which is why its licence does not propagate. If that ever changes — if any
ViaVersion code is shaded or redistributed — this ADR must be revisited before the change lands.

## Consequences

Build-time fetching means CI needs network access on a cold cache, and generated sources are
committed, which makes some diffs noisy. Writing the conformance corpus by hand is real work that a
copied community pack would have made free. Reading Blockbench without copying it is slower than not
having that discipline.

All three are the cost of not having a legal problem later.

## Reversal cost

The project licence is **effectively irreversible once third parties contribute** — relicensing then
needs every contributor's agreement. This is the argument for settling it early.
