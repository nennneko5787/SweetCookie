# SC-200 — Script API

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

Bedrock's JavaScript API. `@minecraft/server` 2.8.0 alone is 439 exported classes and 25 600 lines
of type declarations, executed by a QuickJS runtime with a watchdog. Reimplementing all of it is a
project comparable in size to the rest of SweetCookie.

**Scope: the core subset only**, and it is sequenced last.

---

## 1. Decisions already taken

**Scripting ships as an optional companion mod**, not in the main jar. The JS engine is large and
its performance on a stock JDK is uncertain; that cost should not be paid by users who only want
JSON add-ons. The main jar declares the `JsEngine` SPI; the companion provides it.

**Phase 1 may ship with no scripting at all** and still be useful. A pack with a `script` module
loads, its JSON content works, and one diagnostic says scripting is unavailable.

**Engine: GraalJS**, behind the SPI so it can be replaced. Rhino is ES2015-era and Bedrock scripts
are ES modules using modern syntax; a QuickJS JNI binding would match Bedrock's semantics most
closely but adds native artifacts per platform, which is a distribution problem for a Minecraft mod.
ADR-0005.

`TODO(SC-200)`: measure GraalJS interpreted-mode performance and jar size, including whether ICU4J
can be excluded. If the numbers are bad, revisit.

## 2. Supported surface

Target subset:

| Module | Scope |
|---|---|
| `@minecraft/server` | `world`, `system`, the event signals, `Entity`, `Player`, `Block`, `Dimension`, `ItemStack`, `Vector3`, `Component` accessors for what SC-150/160/170 implement |
| `@minecraft/server-ui` | `ActionFormData`, `ModalFormData`, `MessageFormData` — small, tractable, and the main reason packs use scripting at all |
| `@minecraft/server-net`, `-admin` | **not supported.** Beta-only, dedicated-server-only, and they grant network and secret access to untrusted pack code. |
| `@minecraft/server-gametest`, `-editor`, `debug-utilities` | not supported |

`TODO(SC-200)`: the exact class and member list, generated from the `.d.ts` in `bedrock-samples`
into a coverage shard so the gap is visible rather than asserted.

Anything outside the subset throws a JS `Error` naming the member and the coverage entry. Silently
returning `undefined` produces bugs a pack author cannot diagnose.

## 3. Module resolution

ES modules only. `manifest.json`'s `script` module names the entry (SC-100 §4.2). Relative imports
resolve inside the pack's VFS; bare specifiers resolve only to supported `@minecraft/*` modules.
No filesystem, no network, no `eval`, no `Function` constructor.

Module version matters: a pack declares `{"module_name": "@minecraft/server", "version": "2.8.0"}`
and Mojang has made breaking changes between majors. The declared version selects a facade; an
unsupported version disables that script module only, with `SCE-2005`.

## 4. Threading and scheduling

Bedrock runs scripts on the server tick thread. Sections to write:

- a dedicated script thread that the tick thread hands control to and waits on, so a hung script is
  interruptible without corrupting the world;
- `system.run`, `system.runTimeout`, `system.runInterval`, and `system.runJob`'s generator-based
  cooperative scheduling;
- before/after event ordering — Mojang publishes
  `metadata/engine_modules/engine-after-events-ordering.json`; use it rather than guessing;
- the per-tick script budget, and what happens when it is exceeded.

`TODO(SC-200)`: whether the handoff cost per tick is acceptable, or whether scripts should run
asynchronously with a command queue. The former matches Bedrock's semantics; the latter performs
better and changes observable behaviour, so the former wins unless it is unusable.

## 5. Watchdog

Bedrock terminates on `hang`, `stackOverflow`, `exceededMemoryLimit` (about 250 MB) and
`exceededTimeLimit`. SweetCookie must do the same, with configurable limits, and must survive
termination: a killed script realm unloads its pack's scripting without taking the server down
(constitution rule 1).

## 6. Exposing world state

Script objects are **handles**, not direct references. A `Block` handle holds a dimension and a
position and re-resolves on access, matching Bedrock's semantics, where a handle to an unloaded
chunk throws rather than returning stale data. Bedrock's specific error types
(`InvalidEntityError`, `LocationInUnloadedChunkError`, `CommandError`, and the other 41) are part of
the contract — packs catch them by name.

Custom components: `@minecraft/server` 2.x lets scripts register block and item components
(`BlockComponentRegistry`, `ItemComponentRegistry`), which JSON definitions then reference by name.
This is increasingly how real add-ons are written, so it is high priority within the subset despite
being an advanced feature.

## 7. Security

An add-on is untrusted code (SC-260). No filesystem, no network, no reflection into Java, no access
to other packs' state, and a memory ceiling. The Java bridge exposes only the facade objects, never
Minecraft or SweetCookie internals.

## 8. Testing contract

A conformance harness running scripts against a headless server with deterministic ticking; per-API
cases; watchdog cases for each termination reason; error-type cases proving the right JS error class
is thrown; and an ordering test against Mojang's published event-ordering table.
