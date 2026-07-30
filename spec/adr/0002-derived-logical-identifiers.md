# ADR-0002 — Logical identifiers are derived, physical slots are allocated

**Status:** accepted
**Date:** 2026-07-30
**Affects:** SC-120, SC-270

## Context

Bedrock content needs a stable identity on the Java side: for commands, for the sideband protocol,
for diagnostics, for the coverage ledger, and for chunk and inventory storage.

Two obvious schemes exist. Derive the identifier from the Bedrock one by a pure function, or
allocate identifiers from a counter and persist the mapping.

Allocation has a well-known failure mode: two machines that allocate independently disagree, and
reconciling them is either a negotiation protocol or a corrupted world. Since SweetCookie must run on
both a client and a server that may have been set up independently, that failure is not hypothetical.

Derivation has its own problem: it cannot express "this content used to be at this storage location",
which is exactly what a chunk palette needs when a block's identity is opaque.

## Decision

Use both, for different things, and never confuse them.

**Logical identity is derived**: `sweetcookie:<sanitise(ns)>.<sanitise(path)>`, a pure function of
the Bedrock identifier. It is what every human-visible and network-visible surface uses.

**Physical storage slots are allocated** and recorded in the per-world ledger. They appear in chunk
palettes and nowhere else.

## Consequences

**Good.** Client and server agree on every identity with no synchronisation, on any machine, which is
what allows the client to bind slots independently and rebind live (SC-120 §9). The mapping is
reproducible from the pack alone, so a bug report can be reproduced from the same add-on.

**Bad, and accepted.** Two identifier spaces is conceptually heavier than one, and confusing them is
a plausible mistake — hence its promotion to a constitution rule and a review-blocking defect.
Derived identifiers can also collide after sanitisation, which needs a deterministic tiebreak
(SC-120 §3.1), and the tiebreak's result depends on the installed pack set, which is why the ledger
records it too.

## Alternatives considered

**Derivation only.** Rejected: chunk palettes need a slot concept, and forcing derived names into
them would mean one registry entry per block, which forbids runtime attach/detach (ADR-0007).

**Allocation only.** Rejected: it makes every network message require an identifier table, and it
makes two independently-configured installations disagree.

**Use the add-on's own namespace** (`wizardry:magic_wand` → `wizardry:magic_wand`). Rejected: two
add-ons could collide in the Java registry, our content becomes indistinguishable from another mod's,
and the mapping stops being a function we control.

## Reversal cost

**Very high** for the derived half — it is baked into every saved world through the ledger. The
slot-allocation half is already an implementation detail behind the ledger and could be changed with
a migration.
