# ADR-0013 — Lepus owns the Molang pipeline, and it is `float`

**Status:** accepted
**Date:** 2026-07-31
**Affects:** SC-000, SC-130, SC-250
**Supersedes:** ADR-0008, ADR-0012

## Context

ADR-0008 chose `team.unnamed:mocha` for its bytecode compiler and said, of writing our own:

> Rejected as a poor use of early effort … **Revisit only if mocha's gaps prove large.**

The measurement (SC-130 §2.6, `MochaCapabilityTest`) established what the gaps are:

| | |
|---|---|
| numeric type | `double` end to end — value model, AST, emitted bytecode |
| `math.*` coverage | 25 of Bedrock's 61; the 36 absent include all thirty easing curves |
| `math.die_roll` | ignores its range; returns a random value below 1 |
| `math.random`, `math.random_integer`, `math.die_roll_integer` | throw rather than returning |
| syntax errors | silent unless a handler is installed, and `1 +` and `1 ? : 2` are silent even then |

ADR-0012 proposed absorbing the first of those as a stated fidelity divergence, on the argument that
replacing the compiler was an unbounded schedule risk on the hottest path.

**That trade was rejected on review, and correctly.** Lepus's entire purpose is to run Bedrock
add-ons as Bedrock runs them. A numeric model that takes the other branch of a comparison is not a
rounding footnote — it is the product failing at the thing it exists to do, in a way that surfaces to
an author as "my animation is wrong here and right on my phone" with no way to act on it. The
gaps proved large. ADR-0008's own revisit condition is met.

## Decision

**Lepus owns the whole Molang pipeline: lexer, parser, folding, compilation and evaluation. It
is `float` throughout, per SC-000 §7. The `team.unnamed:mocha` dependency is removed.**

Consequences of owning it, in the order they matter:

1. **`float` everywhere**, including intermediates. SC-000 §7 goes back to stating one rule with no
   exception.
2. **All 61 `math.*` functions are ours**, which they were going to be regardless — the coverage
   ledger has tracked them as 61 entries from the beginning, and mocha's 25 would each have needed
   verifying against Bedrock's definition anyway. `math.die_roll` shows why: it was bound, it was
   wrong, and only an explicit test found it.
3. **A syntax error is always detectable**, because the parser is ours. `1 +` becomes a diagnostic
   with provenance at ingest instead of a silent 1.

## The performance argument, answered

ADR-0008's case for mocha was that the alternative is "tree-walking an AST per bone per frame". That
is a false choice. Between an AST walker and a bytecode compiler sits **closure compilation**: the
AST is compiled once into a tree of small `FloatOp` objects, each holding its already-compiled
children, so evaluation is virtual dispatch over a fixed shape with no node inspection, no
`instanceof`, and no environment lookup on the hot path. It is a well-understood technique and it is
what this project will do.

Combined with what SC-130 §6 already required — constant folding at parse time, source interning,
integer query identifiers, per-frame memoisation — the budget is approachable without emitting
bytecode. **And if it is not, a bytecode backend behind the same interface remains available**, on
our terms and in `float`, which is the option mocha never offered.

What is genuinely given up: the several hundred lines of a competent expression compiler, written by
someone else, working today. That cost is real and it is accepted.

## Consequences

**Good.** Bedrock semantics with no asterisk. No third-party code on the hottest path. Every one of
the 61 math functions verified against Bedrock's definition rather than assumed from a binding.
Parse errors reportable with provenance. One less dependency to track for licence and supply chain.

**Bad, and accepted.** More code to write and own, on the critical path to the render stack. The
performance claim above is *reasoned, not measured* — SC-250's regression test is what will settle
it, and until that test exists this ADR is carrying an unverified assumption of its own. That is
recorded here deliberately, because it is the same shape of claim that got ADR-0008 into trouble.

**Retained.** `MochaCapabilityTest` is deleted along with the dependency, but its findings live in
SC-130 §2.6. Re-running it is a documented step if mocha is ever reconsidered.

## Reversal cost

**Low in one direction, medium in the other.** Adopting a third-party engine later is easy: the
facade ADR-0008 asked for now genuinely exists, because everything behind it is ours. Going back to
mocha specifically would reintroduce the `double` divergence, which is the thing this ADR exists to
refuse.
