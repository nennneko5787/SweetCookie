# SC-250 — Performance budgets

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

Each budget is a **measurable assertion with a regression test**, not an aspiration. A budget with
no test is not a budget.

---

## 1. Why budgets rather than "make it fast"

Three subsystems here are structurally capable of destroying frame rate or tick time, and all three
are on paths that run per entity per frame or per tick:

- Molang evaluated per bone per frame per entity;
- filters evaluated per goal per tick per entity;
- the Snowstorm particle simulation.

Each is fine at one entity and unusable at fifty if nobody is watching. Budgets are set before the
code exists so that the design is forced to accommodate them.

## 2. Budgets

| Path | Budget | Conditions | Set by |
|---|---|---|---|
| Molang, client | **< 1 ms/frame** | 50 visible custom entities, each with a render controller, an animation controller and 2 animations | SC-130 §6 |
| Entity tick, server | `TODO(SC-250)` | 200 custom entities with typical goal sets | SC-160 |
| Filter evaluation | `TODO(SC-250)` | included in the entity tick budget | SC-140 §6 |
| Particles | `TODO(SC-250)` | 2 000 live particles across 20 emitters | SC-180 §7 |
| Block permutation resolution | one-off at bind time; **< 50 ms per 1 000 state indices** | — | SC-150 §1 |
| Pack parse | **< 2 s** for a 100 MB add-on, cold | excludes archive I/O | SC-100 |
| Chunk overlay | **< 15 %** of the vanilla chunk packet size | a chunk 10 % custom blocks by volume | SC-270 §6 |
| Sideband, steady state | `TODO(SC-250)` | 50 custom entities in view | SC-270 §8 |
| Memory per pack | `TODO(SC-250)` | IR only; excludes textures, which are not in the IR | SC-110 §8.2 |
| Script tick | `TODO(SC-250)` | | SC-200 §4 |

Numbers marked `TODO` are set when the corresponding subsystem's design settles. **Setting one is a
prerequisite for that subsystem's document leaving `outline`.**

## 3. Measurement

- Benchmarks are JMH for `core/`, which needs no Minecraft, and a headless-server harness for
  runtime paths.
- Rendering budgets need a client and are measured with a fixed scene and a fixed camera path, so
  the number is comparable across runs.
- Every budget test records its number so a trend is visible, and fails on regression beyond a
  tolerance rather than on an absolute value that varies by machine.

`TODO(SC-250)`: choose the tolerance and the baseline mechanism. A percentage against a committed
baseline is the usual answer; the baseline must be per-machine-class or CI will be noisy.

## 4. Design consequences already committed

These exist because of budgets and are recorded here so they are not "optimised away" later:

- Molang compiles to bytecode rather than being tree-walked (SC-130).
- Molang expressions are interned so identical sources across a pack compile once.
- Queries have integer identifiers, not string lookup.
- Client-relevant boolean queries share a bitset so an entity's flags sync as one word.
- Block permutations are pre-resolved per state index, never evaluated per block access (SC-150).
- Entity components are stored in an interned-key array with a presence bitset, not a `HashMap`
  (SC-160 §3.1).
- The IR holds texture *paths*, never texture bytes (SC-110 §8.2).
- Chunk overlays are sparse: only sections containing custom blocks are sent (SC-270 §6).
- Item state is sent once per handle per session, not per stack (SC-270 §7).
- Diagnostics are deduplicated by site (SC-240 §3).

## 5. What is explicitly not optimised in 0.x

- Bedrock physics fidelity, which would cost more than it is worth (SC-160 §1).
- The native-identifier wire optimisation, which is prohibited for correctness reasons (SC-270 §12).
- Path B block rendering, which is expected to be slower than Path A and is only used when Path A
  cannot express the geometry (SC-150 §5).
