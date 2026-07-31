# ADR-0011 — The ledger *verifies* `implemented`; it does not promote it

**Status:** accepted
**Date:** 2026-07-31
**Affects:** SC-000, SC-240, `process.md`, `spec/schemas/coverage.schema.json`
**Supersedes:** —

## Context

Constitution rule 9 says the coverage ledger is verified rather than trusted. `process.md` §4 turned
that into a specific mechanism, repeated in `CLAUDE.md` and in every shard header:

> **Never set `status: implemented` yourself** — `specReport` promotes an entry once its conformance
> cases pass.

Implementing SC-100 and then the conformance runner made it clear the mechanism cannot work.

**Promotion has no legal source state.** `specReport` would have to promote from something. The only
candidate is `partial`, and SC-000 §3 defines `partial` as *"correct for the common case; every known
divergence is enumerated"*. Promoting that to `implemented` — *"observably indistinguishable from
Bedrock for all inputs the conformance corpus covers"* — turns a stated list of divergences into a
claim that there are none. The schema makes this worse rather than better: `partial` **requires** a
`fidelity` note, so an author who believes their work has no divergences has nothing valid to write.
They can write `stub`, which forbids naming an implementation, or `partial`, which requires
describing a divergence that does not exist. There is no third option.

**Promotion also runs at the wrong time.** `specReport` rewrites `docs/compatibility/**` and would
have had to rewrite `spec/coverage/*.yaml` too — source files, under `--write`, which a contributor
runs only when the generated tables already differ. A status that is correct only after somebody
remembers to run a write-mode task is not a verified status.

The fourteen SC-100 entries this was discovered on are all legitimately `partial`: extraction limits
refuse add-ons Bedrock loads, subpack tiers come from configuration rather than device memory,
`min_engine_version` never refuses. None of them is waiting to be promoted. The problem is not that
promotion is blocked — it is that the rule describes a workflow nobody can execute.

## Decision

**`implemented` is written by a human and verified by the build.** `specReport` promotes nothing and
never edits `spec/coverage/**`.

An entry at `implemented` **MUST** satisfy all of:

| | Checked by |
|---|---|
| names an `impl` class carrying a matching `@SpecImpl` | `specLinks` |
| names at least one conformance case | `specValidate` |
| every named case **ran and passed** | `specConformance` |
| carries **no** `fidelity` note | `specValidate` |
| every entry in `fields`, if present, is `ok` | `specValidate` |

The last two are new, and they are what makes the status mean what SC-000 §3 says. A `fidelity` note
describes an observable difference from Bedrock; an entry claiming there is none cannot carry one. A
`fields` map with a `missing` or `partial` value is an enumerated divergence in table form, and says
`partial` whatever the `status` line says.

`partial` keeps its meaning exactly and is never promoted to anything. It is the honest terminal
state for work with known, stated divergences, which is most work.

## Why this satisfies rule 9

The rule's purpose is that a status must be backed by evidence, not that a particular field is
read-only to humans. Verification is **strictly stronger** than promotion:

- it runs on **every build**, not only when somebody passes `--write`;
- it cannot be bypassed by *not* running a task;
- it fails with the reason rather than silently rewriting the file it disagrees with;
- and it leaves `spec/coverage/**` as source that only humans edit, which is the property that makes
  a shard diff reviewable at all.

A contributor who writes `implemented` without the evidence gets a red build naming what is missing.
That is the outcome the original rule wanted.

## Consequences

- `process.md` §3's loop no longer ends in "specReport promotes the entry". It ends at the checks.
- `CLAUDE.md`'s definition of done drops the promotion bullet and gains the verification list.
- Nothing in `spec/coverage/**` changes as a result of this ADR. No entry was waiting on promotion.
- **The `implemented` path is unexercised today**, because no SC-100 feature is free of divergence
  while the stages that consume packs are unwritten. It is covered by `build-logic` tests instead of
  by a live entry, which is the same reason those tests exist for the other rules.

## Reversal cost

**Medium, and it drops over time.** Reversing this means reinstating a task that edits
`spec/coverage/**`, and every entry written under this ADR would have to be re-examined: a human-
written `implemented` and a promoted one are indistinguishable once written, so a reversal cannot
tell which entries had been verified and which had merely been asserted before the checks existed.

The cost is bounded today because no entry is at `implemented` yet, and it grows with each one that
lands. If the promotion model is going to be revived, it should be revived before that happens.

## Alternatives considered

**A sixth status, `candidate`.** Human-writable, meaning "no known divergence, awaiting
verification", promoted to `implemented` when the corpus passes. It works, and it keeps the
never-write-`implemented` rule intact. Rejected because it adds a word to a vocabulary SC-000 calls
"the five words", it must appear in the schema enum and therefore in shard files, and it buys only
the appearance of the old rule — the build still has to check the same five things, and it now has a
transient status to explain to anybody reading a diff.

**Keep promotion, and forbid `fidelity` on the promoted result.** This is the same set of checks with
a file rewrite bolted on. The rewrite is the part that is hard to review and easy to skip.
