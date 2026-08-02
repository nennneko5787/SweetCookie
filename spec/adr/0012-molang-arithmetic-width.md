# ADR-0012 — Molang evaluates in `double`, and the divergence is stated

**Status:** superseded-by ADR-0013
**Date:** 2026-07-31
**Affects:** SC-000, SC-130, SC-250
**Supersedes:** —

> **Superseded the same day it was accepted.** This ADR proposed accepting a numerical divergence
> from Bedrock in exchange for keeping a third-party bytecode compiler. It was rejected on review:
> the project exists to run Bedrock content, and "matches Bedrock except where a dependency made it
> inconvenient" is not the product. The analysis below is kept because ADR-0013 rests on its
> measurements — the reasoning is what changed, not the facts.

## Context

SC-000 §7 and SC-130 §1 both require Molang arithmetic in `float`, with the reason stated plainly:

> Do not widen to `double` "for accuracy" — it changes which branch a pack takes.

ADR-0008 chose `team.unnamed:mocha` and left a `TODO` to verify its coverage before the render stack
landed. That verification has now run (SC-130 §2.6, `MochaCapabilityTest`), and it found that
**mocha is `double` from end to end**: `NumberValue.of(double)`, `MochaFunction.evaluate()`, the
AST's `DoubleExpression`, and the bytecode it emits. `NumberValue.normalize` maps NaN and Infinity to
zero and does not narrow.

The two rules cannot both hold. The conflict is real rather than theoretical:

```
0.1 + 0.2 > 0.3     double: true      float: false
```

A render controller or animation controller branching on a comparison near a representable boundary
takes the other branch.

## Decision

**Evaluate in `double`, narrow to `float` at every boundary where a value leaves the expression
layer, and record the difference as a stated fidelity divergence rather than pretending it away.**

Concretely:

- `MolangExpr.evaluate` returns `float`. The narrowing happens once, at the return.
- Constant folding at parse time folds in `float`, because that is our code and there is no reason
  for it to disagree with Bedrock.
- **Intermediate arithmetic inside one expression stays `double`.** This is the divergence, and it
  is confined to expressions containing a comparison or a discontinuous function applied to a value
  within about 2⁻²⁴ of a boundary.
- SC-000 §7 is amended to say this, rather than left stating a rule the implementation breaks.
- A coverage entry `molang/arithmetic_width` carries the `fidelity` note, so it appears in the
  published compatibility table instead of only in a specification nobody reads.

## Why not the alternatives

**Drop mocha and write our own float evaluator.** The parser is not the hard part and we could do
it. But ADR-0008 chose mocha for its **bytecode compiler**, and that is the whole of the answer to
SC-250's budget of one millisecond per frame at fifty entities — the alternative is walking an AST
per bone per frame. Writing a correct float Molang *compiler* is a different and much larger project
than writing a float *interpreter*, and taking it on now trades a bounded, stated numerical
difference for an unbounded schedule risk on the hottest path in the mod.

**Use mocha's parser and write our own evaluator over its AST.** Keeps the part we could have
written and discards the part we could not. Same objection, and it also forks us from mocha's AST
evolution.

**Narrow after every operation.** There is no hook for it. `postCompile(Consumer<byte[]>)` exposes
the emitted bytecode, so rewriting every arithmetic instruction to round through `float` is
*possible*; it would mean maintaining a bytecode rewriter against a third-party compiler's output,
which is a worse thing to own than the divergence it removes.

**Accept it silently.** Rejected on constitution rule 8. A divergence nobody wrote down is one that
turns into an argument when a user reports it.

## Consequences

**Good.** The dependency stays, the compiler stays, SC-250's budget keeps its foundation, and the
difference is written where users can read it.

**Bad, and accepted.** Lepus is not bit-identical to Bedrock for expressions that compare near
a float boundary. No conformance case can currently detect this, because detecting it requires
observing a real pack take a different branch; the first one that does becomes the case, and this
ADR becomes reversible evidence rather than a judgement call.

**Watch for.** If a pack is ever reported behaving differently in a way that traces to a comparison,
this ADR is the first place to look, and "write our own float evaluator" becomes a costed option
rather than a hypothetical one.

## Reversal cost

**Medium.** `MolangExpr` is Lepus's own type and the boundary is already narrow (ADR-0008's
facade), so replacing what is behind it does not touch the IR or any caller. What is not cheap is
the replacement itself: a float Molang compiler meeting SC-250's budget. The cost is in building the
alternative, not in switching to it.
