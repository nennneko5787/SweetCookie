# Process

How a change moves through this repository. Normative.

---

## 1. Where work comes from

Three sources, in descending priority.

**An acceptance add-on needs it.** The project tracks a small set of real, popular Bedrock add-ons
as acceptance targets (`spec/conformance/manual/acceptance/`). What they actually use drives what
gets built. *Implementing the specification in feature-ID order is how this project dies* — the
vocabulary is ~2,500 identifiers and a large fraction of it is used by nobody.

**A user diagnostic says it is missing.** Every degradation emits an `SCE-####` code naming the
feature ID it refused (constitution rule 8). Aggregated, that is a demand signal.

**Upstream added it.** `specUpstreamDiff` fails the build when Mojang publishes a feature ID absent
from the ledger. That failure is resolved by adding a coverage entry at `status: stub`, not by
implementing it.

## 2. The unit of work is a feature ID

Not a file, not a class. One entry in `spec/coverage/*.yaml`, e.g.
`minecraft:behavior.melee_attack`. If what you are doing does not correspond to one or more feature
IDs, it is infrastructure — see §7.

**If the feature ID has no coverage entry, it is out of scope.** Stop and ask. Do not add a coverage
entry in order to justify work; entries are added by the upstream-diff flow or by an explicit
scoping decision, and both leave a record.

## 3. The loop

```
  upstream diff / diagnostic / acceptance add-on
            │
            ▼
  spec/features/NNNN-slug/spec.md     what should happen, in Bedrock's terms
            │                          (Japanese is fine here)
            ▼
  spec/features/NNNN-slug/plan.md     how, against SC-110 and the domain SC- doc
            │
            ▼
  spec/features/NNNN-slug/tasks.md    checklist
            │
            ├─► amend spec/normative/SC-<domain>.md   ← its own commit
            ├─► write the conformance case first
            ├─► implement, annotated @SpecImpl
            └─► update the coverage entry
            │
            ▼
  ./gradlew specAll && ./gradlew test
            │
            ▼
  PR ──► merge ──► feature dir moves to _archive/
```

### `spec/features/NNNN-slug/`

Transient. Numbered sequentially, four digits. Japanese is permitted throughout — these are working
notes, they are archived when the work lands, and forcing English here buys nothing.

| File | Contains |
|---|---|
| `spec.md` | What the Bedrock feature does, from Mojang's documentation *and* from observation. Where the documentation is wrong, say so and say how you know. |
| `plan.md` | The Java-side approach. Which IR types, which classes, what the fidelity gap will be. |
| `tasks.md` | A checklist. Tick as you go. |

Move the directory to `spec/features/_archive/` when the coverage entry is promoted.

### Amending a normative document

A change to `spec/normative/**` is **its own commit**, never mixed with implementation, so it can be
read and reverted on its own. It may share a pull request with the code that implements it.

The spec is amended **before** the code lands, not after. A pull request whose implementation
contradicts the current specification and does not amend it is rejected regardless of quality.

### Conformance case first

Write `spec/conformance/<domain>/<case>/` before the implementation. It fails; that is the point.
See `spec/conformance/README.md` for the tiers and layout.

The case's add-on must be **100 % original content** (constitution rule 10). Copying a fragment of a
community pack, however small, is not acceptable.

## 4. Coverage entries

Update `status`, `impl`, `fields`, `fidelity` and `conformance` in your PR.

| Status | Meaning |
|---|---|
| `stub` | recognised and parsed into the IR; runtime behaviour is a diagnostic and a no-op |
| `implemented` | no known divergence, and the build proves it — see below |
| `partial` | works for the common case; `fields` and `fidelity` say precisely where it does not |
| `unsupported` | deliberately not implemented **yet**; `fidelity` states why and what it would take |
| `wontfix` | will not be implemented; `fidelity` states why (usually: no Java analogue exists) |

**`implemented` is written by hand and verified by the build** (ADR-0011, SC-000 §3.1). No tool
promotes an entry and no tool edits `spec/coverage/**`. Claiming `implemented` without an `impl`
class, a passing conformance case, no `fidelity` note and an all-`ok` `fields` map is a red build
naming exactly what is missing.

Earlier revisions of this document said `specReport` would promote `partial` to `implemented`. That
mechanism had no legal source state — `partial` requires a `fidelity` note, so an author with no
divergence to describe had nothing valid to write — and it ran only under `--write`. ADR-0011 records
why verification replaced it.

**`partial` is never promoted.** It is the terminal state for work with known, stated divergences.

`fidelity` is required for `partial`, `unsupported` and `wontfix`, must be at least 40 characters,
and must describe an **observable behavioural difference** — not an implementation note. "Not done
yet" fails review. "Bedrock re-evaluates the target filter every tick; we re-evaluate every 4 ticks,
so a target that becomes invalid is dropped up to 150 ms late" passes.

`stub` is exempt, because its meaning is already fully defined by the status: recognised, parsed
into the IR, no runtime effect, emits a diagnostic. Demanding prose for thousands of not-yet-started
entries would produce noise rather than information. **The moment an entry moves off `stub`, the
note becomes mandatory.**

## 5. Checks

```
./gradlew specAll
```

| Task | Fails when |
|---|---|
| `specValidate` | a coverage or conformance file violates its schema |
| `specUpstreamDiff` | upstream has a feature ID the ledger does not, and it is not in `spec/upstream/allowlist-missing.yaml` |
| `specImplCheck` | a non-`stub` entry names a class that does not exist or lacks `@SpecImpl`; or an `@SpecImpl` names a feature ID with no entry |
| `specConformance` | a case claims to prove a feature with no coverage entry, or an entry above `stub` relies on a case that did not run or did not pass. It runs the corpus first, so there is no path through it that reports success without evidence |
| `specReport` | `docs/compatibility/**` differs from what the ledger implies. It generates documentation and nothing else; it does not edit the ledger |
| `specLanguage` | CJK appears in `spec/**` outside `ja/`, `features/` and fenced code blocks |
| `adrIndex` | `spec/adr/index.md` is stale, or an ADR links to one that does not exist |

CI runs the same task on every push. It also builds all four
`{fabric, neoforge} × {1.21.11, 26.2}` combinations from milestone M2 onward: a change may land
Fabric-first, but **a change that breaks another combination's build is rejected**.

## 6. Adding a Minecraft version

See SC-220. Summary: one `vers(...)` line in `settings.gradle.kts`, one
`versions/<node>/gradle.properties`. If anything else has to move, the version abstraction is wrong
and that is the bug to fix.

## 7. Infrastructure work

Build logic, the IR itself, the diagnostic framework, the wire protocol — work that has no feature
ID. It still needs a `spec/features/` directory and it still amends a normative document, but it has
no coverage entry and no promotion step. Reference it as `SC-<nnn>` without a feature suffix.

## 8. Decisions

Anything expensive to reverse gets an ADR before it is implemented: on-disk formats, the wire
protocol, module boundaries, licensing, third-party dependencies with viral licences, anything that
persists into a saved world.

Anything cheap to reverse does not. Do not write an ADR to choose a method name.
