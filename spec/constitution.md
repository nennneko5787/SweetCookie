# Constitution

Twelve rules. They override every other document in this repository, including the rest of `spec/`.
Changing one requires an ADR that supersedes this section and a specific justification for why the
rule's original reason no longer holds.

Each rule states its cost, because a rule whose cost is hidden gets quietly abandoned the first time
it is inconvenient.

---

## 1. Never crash on add-on input

An add-on is untrusted third-party data of unknown quality, frequently written against a Bedrock
version we have never seen. Every unknown component, AI goal, Molang query, filter test, event
response or `format_version` **MUST** degrade to a logged diagnostic and a no-op. It **MUST NOT**
throw, and it **MUST NOT** abort loading of the surrounding pack.

*Why:* one `NullPointerException` in a registry of 171 goals takes down a world that was otherwise
fine. Partial function beats total failure, every time.

*Cost:* bugs hide as silent no-ops. Paid for by rule 8 — every degradation emits a diagnostic with a
code, and the diagnostics are visible in-game, not just in the log.

*This rule outranks fidelity.* When faithfulness and robustness conflict, robustness wins and the
divergence is recorded in the coverage entry's `fidelity` field.

## 2. `spec/` is the only normative source

Code, Javadoc, `docs/`, commit messages and issue threads are all non-normative. If behaviour is
worth relying on, it is written down here first.

*Cost:* a specification edit in the same PR as the code that implements it. Accepted; they may share
a PR, but the spec change must be its own commit so it can be read on its own.

## 3. No Minecraft in `core/`

`core/**` **MUST NOT** reference `net.minecraft.*`, any loader API, or any Stonecutter `//?` comment.

*Why:* this is the only architectural boundary in the project that a compiler can enforce, and it is
what keeps ~40k lines of format parsing testable in seconds and identical across Minecraft versions.
A boundary that is merely documented does not survive eighteen months.

*Cost:* the intermediate representation must be expressive enough to describe everything an add-on
can say, without borrowing Minecraft's vocabulary. That is SC-110, and it is the largest document
here for exactly this reason.

## 4. Logical identity is derived, never allocated

The logical identifier for Bedrock content is a pure function of its Bedrock identifier
(`sweetcookie:<namespace>.<path>`, SC-120). It is what commands, the sideband protocol, the coverage
ledger, diagnostics and every internal lookup use. There is no counter, no allocation table, and no
negotiation between client and server.

*Why:* client/server agreement falls out for free, on any machine, with no synchronisation. Every
allocation scheme eventually produces a split brain.

*Cost:* Bedrock identifiers that sanitise to the same string need a deterministic tiebreak. SC-120
specifies one.

**Physical storage slots are a separate concept and they *are* allocated.** A block occupying pool
slot `sweetcookie:block_16/0037` is a storage detail recorded in the per-world ledger, never a
logical identity, never sent over the network, and never written into a specification, a command or
an annotation. Confusing the two is a review-blocking defect.

## 5. Removing a pack must not destroy a world

Content that a loaded pack no longer provides but that the registry ledger has seen **MUST** be
re-registered as a placeholder preserving the exact recorded block-state schema and NBT, and
**MUST** be losslessly restored when the pack returns. Placeholders are **never** pruned
automatically.

*Why:* the alternative is that temporarily disabling an add-on silently deletes player builds and
inventories. That is unrecoverable and unforgivable.

*Cost:* the ledger, the schema-drift record, and three placeholder content types that must be
maintained forever.

## 6. Custom content never occupies a vanilla network registry ID

On the wire, every custom block, item and entity is a vanilla carrier; its real identity and state
travel by name over SweetCookie's own channel (SC-270). Custom entity state **MUST NOT** use
`SynchedEntityData`. Custom entries **MUST NOT** appear in vanilla dynamic-registry sync.

*Why:* it is what makes ViaVersion / ViaBackwards work perfectly rather than not at all, it removes
an entire class of client kicks, and it gives graceful behaviour to clients that lack the mod.

*Cost:* packet-write interception and a sideband transport. Real, and paid deliberately.

## 7. Packs attach and detach at runtime, per world

Bedrock lets you drop an add-on into a world and toggle it. SweetCookie **MUST** offer the same,
per world, without restarting Minecraft — because a compatibility layer that is clumsier than the
thing it is compatible with will not be used.

Concretely, **no Bedrock content may require a Java registry entry of its own**:

| Content | Registrations | Consequence |
|---|---|---|
| Items | **one** carrier `Item`; identity lives in a data component | fully hot-pluggable |
| Entities | a **fixed** set, one per (class family × spawn category) | fully hot-pluggable |
| Blocks | a **pre-reserved pool** of anonymous slots, bound at runtime, assignment persisted in the ledger | hot-pluggable until the pool is exhausted |
| Everything else | none | fully hot-pluggable |

Nothing in the registry ever names a Bedrock feature. The single documented exception is pool
exhaustion: adding blocks beyond the reserved pool needs a config change and a restart, and the
user **MUST** be told exactly that, with the numbers.

*Why the pool rather than unfreezing the registry:* unfreezing means hand-maintaining every cache
Minecraft derives from the block registry, on two loaders, across every version, forever. The pool
never touches the registry after startup, so it is boring in the way infrastructure should be.

*Cost:* reserved slots consume block-state palette space whether used or not; block identity in
chunk storage is opaque and needs the ledger to interpret; and every construction argument Java
bakes at registration must be routed through a function closing over a live reference.

## 8. Silence is a bug

Bedrock fails silently; we do not. Every degradation, refusal, clamp and unsupported construct
**MUST** emit a diagnostic carrying a stable `SCE-####` code, the offending pack and file, and a
source position where one exists. Diagnostics are surfaced in-game, not only in the log (SC-240).

*Cost:* a diagnostic code space that must be curated and never reused.

## 9. The ledger may not lie

`spec/coverage/**` is checked by CI, not trusted. A `status: implemented` entry with no
`@SpecImpl`-annotated class fails the build; so does one with no conformance case. **A human never
writes `status: implemented`** — `specReport` promotes an entry when its conformance tests pass.

*Why:* a compatibility table nobody can trust is worse than none, because it converts user bug
reports into arguments.

*Cost:* every feature needs a test before it can be called done. That is the point.

## 10. No Mojang content ships

`Mojang/bedrock-samples` is licensed NOASSERTION. It is fetched at build time, pinned by commit SHA
and per-file hash, and used **only** to generate code. Neither the metadata nor anything derived
from Mojang's assets appears in a released artifact. Conformance add-ons are 100 % original content
authored by this project — no community packs, no vanilla files.

*Cost:* the conformance corpus has to be written by hand, and generated sources are committed so
the build works offline.

## 11. English in the repository, Japanese to the user

All identifiers, specification IDs, code, YAML keys, commit messages, ADR titles, `spec/normative/`,
`spec/coverage/`, `spec/adr/` and `CLAUDE.md` are **English**. `spec/normative/ja/`, `docs/ja/` and
`spec/features/` may be Japanese. Conversation with the user is Japanese.

*Why:* the domain vocabulary is already Mojang's English, feature IDs must be greppable, and the
audience for a Bedrock-compatibility mod is international. Translations are informative; when a
translation and the English text conflict, the English text governs.

*Enforced by* `specLanguage`, which rejects CJK codepoints in `spec/**` outside the exempt paths.

## 12. Version divergence is contained, not scattered

A Stonecutter `//?` comment is permitted only for a divergence of **five lines or fewer**. Anything
larger goes behind a platform/version service interface or into a per-version source directory
(SC-220). Public API signatures **MUST NOT** contain version-conditional types.

*Why:* two thousand lines of render code interleaved with `//? if >=26.2 {` is unmaintainable, and
render code is precisely where the temptation is strongest.

*Cost:* an abstraction layer written before it is obviously needed — designed against the *newest*
version's constraints, since a restrictive interface can be emulated on a permissive backend but
never the reverse.

---

## Amending

1. Open an ADR proposing the change, stating which rule and why its original justification fails.
2. Get it accepted.
3. Amend this file in a commit that references the ADR and nothing else.

Rules 1, 5 and 10 are considered effectively immutable: they exist to prevent data loss, user harm
and legal exposure respectively, and no engineering convenience outweighs those.
