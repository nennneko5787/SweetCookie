# SC-260 — Security

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

An add-on is untrusted input. On a server it is chosen by an operator; on a client it may arrive
**from the server the user just joined** (SC-270 §9). The second case is the one that matters: it
turns "install this mod" into "execute content from anyone whose server you visit".

---

## 1. Threat model

| Actor | Can supply | Trust |
|---|---|---|
| Server operator | add-ons on the server | trusted by their own players, not by us |
| **Any server the client joins** | **add-ons pushed to the client** | **untrusted** |
| Add-on author | JSON, JavaScript, textures, models, sounds | untrusted |
| Player | commands, interaction | authenticated, permission-checked |

Out of scope: Minecraft's own vulnerabilities, and the loaders'.

## 2. Archive handling

Fully specified in SC-100 §3 and normative there: zip-slip, absolute paths, symlinks, total size,
compression ratio, entry count, nesting depth, single-file size, path length, UTF-8 and NFC
normalisation, case-insensitive collision.

The one addition here: **limits apply identically to packs received from a server**, and the client's
limits **MUST NOT** be relaxed relative to the server's. A client is the more exposed side.

## 3. Parse limits

`TODO(SC-260)`: set each of these.

| Limit | Why |
|---|---|
| JSON nesting depth | stack exhaustion |
| JSON document size | already bounded by SC-100 §3, restated per document type |
| Geometry vertex and bone count | a pathological model is a client-side denial of service |
| Animation keyframe count | same |
| Particle emitter rate and live-particle cap | same, and this one is trivially triggered by accident |
| Molang expression depth and node count | compile-time and evaluation blowup |
| `loop` / `for_each` iteration cap | Molang gained loops; an unbounded one hangs a frame |
| Entity component-group count and event recursion depth | event storms |
| Block state product | already capped by the pool size classes (SC-120 §6) |
| NBT depth and size in `.mcstructure` | the classic NBT bomb |

Every limit is configurable, has a diagnostic, and **aborts the affected content only** — never the
pack, never the game (constitution rule 1).

## 4. Scripting

The largest single risk, and the reason scripting is a separate optional artifact (SC-200 §1).

Prohibited, without exception: filesystem access, network access, reflection into Java, access to
Minecraft or Lepus internals, `eval` and the `Function` constructor, access to another pack's
state, native code, thread creation.

Enforced by: the GraalJS host-access policy denying everything not explicitly exposed, a module
resolver that only resolves inside the pack VFS and the supported `@minecraft/*` set, and the
watchdog's memory and time limits.

`@minecraft/server-net` and `@minecraft/server-admin` are **not supported**, and this is a security
decision as much as a scope one: they grant HTTP access and secret access to pack code.

## 5. Network

- Every inbound sideband payload is size-capped and structurally validated before use (SC-230 §5).
  `SCE-5012`.
- Session handles are validated against the negotiated table; an unknown handle is dropped, not
  resolved optimistically. `SCE-5004`.
- Pack transfers are hash-verified against the handshake before being written to disk, and a
  mismatch discards the file. `SCE-5010`. **A pack is never executed because a server said to run
  it** — only because its hash matched what the server declared and the user's policy allows it.
- `TODO(SC-260)`: whether the client should require explicit user consent before accepting a pack
  from a server, and whether to keep a trusted-server list. Leaning toward a prompt on first
  encounter with a server, with a remembered decision — the balance between the Bedrock-like
  frictionless experience the user asked for and not silently executing content from strangers.

That last question is the most important open decision in this document and should be settled before
SC-270 §9 is implemented.

## 6. Commands and permissions

`/lepus` subcommands that change global state — `enable`, `disable`, `order`, `reload`,
`prune` — require operator permission. `prune` additionally requires typed confirmation naming the
content, because it discards a ledger entry (SC-120 §7).

Bedrock `run_command` in block and entity events executes with the **server's** authority, not a
player's. `TODO(SC-260)`: the permission level for pack-originated commands. Bedrock effectively
grants them operator level; matching that means an add-on can do anything, which is worth an explicit
decision and an operator-facing configuration option rather than a silent default.

## 7. Resource exhaustion

Beyond parse limits: a cap on total loaded packs, on total IR memory, and on per-tick work performed
on behalf of pack content, so that a badly-written add-on degrades rather than hanging a server.
Tied to SC-250's budgets — a budget that is merely a performance target on a trusted input is a
security control on an untrusted one.

## 8. Testing contract

A malicious-input corpus under `spec/conformance/security/`, authored by us (constitution rule 10):
zip bombs, zip-slip paths, symlink entries, deeply nested JSON, a geometry with a million cubes, a
Molang expression with a million nodes, an unbounded `loop`, an event that triggers itself, an NBT
bomb, and an oversized sideband payload.

Each asserts: the specific diagnostic, that the game keeps running, that unaffected content still
loads, and that nothing was written outside the intended directory.
