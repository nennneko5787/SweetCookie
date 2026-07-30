# ADR-0008 — Molang runs on `team.unnamed:mocha`

**Status:** accepted
**Date:** 2026-07-30
**Affects:** SC-130, SC-250

## Context

Molang is evaluated per bone, per frame, per visible entity, plus every particle field and every
render-controller decision. At fifty entities and sixty frames per second this is easily hundreds of
thousands of evaluations per second, so the execution strategy — not the language surface — decides
whether the client is playable.

Four JVM implementations exist:

| Library | Licence | Notes |
|---|---|---|
| **`unnamed/mocha`** | MIT | lexer, parser, interpreter **and a compiler to JVM bytecode**; on Maven Central; actively maintained |
| `Ocelot5836/molang-compiler` | MIT | compiler-based, last activity 2025-04 |
| `hollow-cube/molang` | MIT | minimal parser and evaluator with a constant-folding optimiser |
| `bedrockk/MoLang` | — | reference-style parser and evaluator |

## Decision

Use `team.unnamed:mocha`, behind a thin SweetCookie-owned facade so it can be replaced.

## Consequences

**Good.** The bytecode compiler removes per-evaluation AST walking, which is the difference between
meeting and missing the one-millisecond frame budget in SC-250. MIT and on Maven Central means no
vendoring and no licence friction. It is actively maintained, by the same group as
`hephaestus-engine`, which renders Bedrock models in Java and is therefore solving adjacent problems.

**Bad, and accepted.** A third-party dependency on the hottest path in the project. Compiling
expressions to classes consumes metaspace, and the behaviour at a few thousand compiled expressions
is unmeasured. Mocha may not express every Molang construct — `loop`, `for_each`, struct
dereference chains and the binary-if form all need verifying — and each gap needs closing on our
side.

## Alternatives considered

**Write our own.** Full control, no dependency, and the parser is not the hard part. Rejected as a
poor use of early effort: the hard part is the 315 query bindings, which we must write regardless,
and mocha does not help or hinder there. Revisit only if mocha's gaps prove large.

**`hollow-cube/molang` or `bedrockk/MoLang`.** Interpreters. Rejected on the performance argument
above.

**`Ocelot5836/molang-compiler`.** Also compiler-based and viable. Mocha was preferred for its
availability on Maven Central and more recent activity.

## Reversal cost

**Low**, by construction — the facade exists precisely so this can change. Expressions are parsed
into SweetCookie's own `MolangExpr` at ingest (SC-110 §7), so the dependency is confined to
compilation and evaluation, not to the IR.

`TODO`: verify mocha's construct coverage and metaspace behaviour before the render stack lands.
Record the gaps in SC-130 §2.6.
