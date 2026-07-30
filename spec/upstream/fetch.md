# Upstream metadata

**Nothing in this directory is Mojang's content, and nothing derived from it ships.**
Constitution rule 10, ADR-0006.

---

## What upstream gives us

`Mojang/bedrock-samples` publishes, under `metadata/`, a machine-readable description of the add-on
format that is far better than the prose documentation:

| Path | Contains |
|---|---|
| `metadata/json_schemas/` | 524 JSON Schemas, versioned per `format_version` |
| `metadata/doc_modules/` | the documentation as structured JSON — component, goal and filter lists |
| `metadata/molang_modules/mojang-molang-queries.json` | the canonical Molang query list |
| `metadata/vanilladata_modules/` | 1 342 block ids, 1 518 item ids, 128 entity ids |
| `metadata/command_modules/mojang-commands.json` | the command grammar |
| `metadata/script_modules/@minecraft/` | the Script API type declarations |
| `metadata/engine_modules/` | event ordering guarantees |

Hand-writing 2 500 feature identifiers from prose documentation would be slow and would rot. This is
the authoritative list, and it is what makes the coverage ledger able to tell us when Bedrock adds
something (`specUpstreamDiff`).

## The licence constraint

`bedrock-samples` is licensed **NOASSERTION** — Mojang's own terms, not an OSI licence. So:

- It is **fetched at build time**, never committed to this repository.
- It is pinned by commit SHA and per-file SHA-256 in `bedrock-samples.lock.json`. A hash mismatch
  fails the build rather than silently generating from different data.
- It is used **only to generate source code**. Generated code is committed, so the build works
  offline and so a contributor without network access can still build.
- **Neither the metadata nor any copy of Mojang's data appears in a released artifact.**
- No file from it is used as a conformance fixture. Conformance add-ons are 100 % original content.

Generating code from a schema is not redistributing the schema. Shipping the schema would be.

## The tasks

```
./gradlew fetchUpstreamMetadata     # clone at the pinned SHA into .upstream-cache/, verify hashes
./gradlew specUpstreamDiff          # compare upstream feature lists against spec/coverage/
./gradlew generateBedrockConstants  # emit enums, id tables and record skeletons
./gradlew updateUpstreamLock        # bump to a new SHA and rewrite hashes — MANUAL, never automatic
```

`.upstream-cache/` is gitignored. `fetchUpstreamMetadata` is skipped when the cache is present and
its hashes match.

### `specUpstreamDiff`

The mechanism that keeps the ledger honest in the *other* direction. It fails the build when
upstream declares a feature identifier that `spec/coverage/**` does not know about.

That failure is resolved by **adding a coverage entry at `status: stub`** — not by implementing the
feature, and not by silencing the check. The result is that a Bedrock update shows up as a concrete,
enumerated list of new work rather than as a slow drift into inaccuracy.

An identifier deliberately excluded goes in `allowlist-missing.yaml` **with a reason**.

## Updating the snapshot

Bumping the pinned SHA is a deliberate act, never automatic:

1. `./gradlew updateUpstreamLock -PupstreamRef=<sha>`
2. `./gradlew specUpstreamDiff` — expect it to fail, listing what Bedrock added.
3. Add a `status: stub` coverage entry for each new identifier.
4. `./gradlew generateBedrockConstants` and review the generated diff.
5. Commit the lock file, the coverage additions and the generated code together, in one commit whose
   message names the Bedrock version.

Step 3 is the point of the whole arrangement, and skipping it defeats it.

## `overrides/`

Mojang's schemas are occasionally wrong — a field typed as a string that is really a Molang
expression, an enum missing a value that vanilla content uses, a component absent from the schema
but present in the samples.

Corrections live in `overrides/*.patch.yaml`, applied after the upstream read. Each **must** carry a
comment saying what is wrong, how we know (a vanilla file that contradicts it, or observed
behaviour), and when it was last checked against upstream — so that a correction that upstream later
fixes can be retired instead of silently diverging forever.

## `allowlist-missing.yaml`

Upstream identifiers deliberately absent from the ledger, each with a reason. Deliberately small: an
entry here is a decision not to track something, and the default should be to track it as a `stub`
instead, which costs one line and keeps it visible.
