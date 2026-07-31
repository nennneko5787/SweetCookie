# Conformance corpus

Executable statements of the form **"given this add-on, this must happen"**.

A coverage entry that claims `partial` or `implemented` and has no case here fails the build
(`specConformance`). This is what makes the ledger a fact rather than a claim.

---

## Legal constraint, before anything else

**Every add-on in this directory is 100 % original content, authored for this project.** No
community pack, no file from `bedrock-samples`, no vanilla asset, not a fragment of any of them.
Constitution rule 10, ADR-0006.

This is non-negotiable and it is checked in review. If you need a case that mirrors how a real pack
does something, write your own pack that does the same thing — do not copy theirs.

Textures are solid colours or trivial generated patterns. Models are hand-authored. Nothing here
needs to be pretty; it needs to be *minimal and unambiguous*.

## Tiers

Choose the **lowest tier that can prove the claim**. A T0 case runs in milliseconds; a T3 case needs
a graphical client and is two orders of magnitude slower and flakier.

| Tier | Runs | Needs | Proves |
|---|---|---|---|
| **T0** | `:core:testkit:test` | nothing | the pack parses into the expected IR, with the expected diagnostics |
| **T1** | `:core:testkit:test` | nothing | translation, Molang evaluation, filter evaluation, permutation resolution |
| **T2** | gametest, both loaders | headless server | runtime behaviour: blocks place, entities act, loot rolls, items work |
| **T3** | client harness | graphical client | rendering, animation, particles |
| **T4** | a human | a real client, sometimes Geyser and a Bedrock device | what cannot be automated |

## Layout

```
spec/conformance/
├── README.md
├── _fixtures/                  reusable minimal packs and worlds
│   ├── minimal_bp/             a behaviour pack with a valid manifest and nothing else
│   ├── minimal_rp/             a resource pack with a valid manifest and nothing else
│   └── flat_world/             a fixed, seeded, flat world
├── <domain>/<case>/
│   ├── case.yaml               schema: spec/schemas/conformance-case.schema.json
│   ├── pack/                   the add-on under test — ORIGINAL CONTENT
│   └── expected/
│       ├── ir.json             canonical JSON (SC-000 §6)
│       ├── diagnostics.json
│       ├── trace.json
│       └── screenshot.png
├── security/                   malicious-input corpus (SC-260 §8)
└── manual/<case>/manual.md     T4 checklists
```

Domains match the coverage shards: `packaging`, `block`, `entity`, `item`, `molang`, `filter`,
`loot`, `render`, `wire`, `geyser`.

## Writing a case

**Write it before the implementation.** It fails; that is the point (`process.md` §3).

1. `mkdir spec/conformance/<domain>/<case>/`
2. Write `case.yaml`. Say in `description` what would break if this regressed — not what the feature
   is, but what a user would notice.
3. Build the smallest pack that exercises exactly the claim. Extend a fixture rather than repeating
   a manifest.
4. Run the case with `./gradlew --project-dir core :testkit:test`. It fails. Commit it anyway if the
   implementation is coming in the same PR.
5. Implement.
6. Regenerate goldens with
   `./gradlew --project-dir core :testkit:test -Dsweetcookie.accept=true` and **read the diff**
   before committing it. A golden accepted without being read is worse than no golden, because it
   converts a future regression into a green build.

Two traps worth knowing before you write one:

- **`case.yaml` is YAML, so ` #` starts a comment.** An expectation of `Slot #1` written unquoted
  becomes `Slot`. The first `.lang` case in this corpus failed against correct behaviour for exactly
  that reason.
- **A case must not list a feature it does not exercise.** Loading a directory proves nothing about
  `container/mcaddon`; that is what `pack.container` is for. `specConformance` checks that every
  `features[]` entry names a real coverage entry under the case's own `spec`, but it cannot check
  that the case truly exercises it — that part is review.

## `pack.container`

`directory` (the default) hands the tree to the loader unpacked. `mcpack`, `mcaddon` and `mcworld`
zip it first, so a case claiming a container feature exercises that container rather than a directory
that looks like one.

## Goldens

- Canonical JSON (SC-000 §6): sorted keys, shortest round-tripping numbers — **indented**, and
  compared after canonicalising both sides. SC-000 §6.3's "no insignificant whitespace" governs
  hashing and comparison; a one-line golden would make `git diff` useless, and a golden nobody can
  read on failure is a golden nobody trusts. Reformatting one by hand can never fail a case; a
  changed value always does.
- A mismatch reports the first differing line with context, not the whole file.
- Committed with `-text` in `.gitattributes`, so git never rewrites line endings inside one.
- Screenshots compare with a small per-channel tolerance, because rendering is not bit-exact across
  drivers. Default 2/255; a case may raise it and should say why.

## Assertions

`equals`, `contains`, `notContains`, `matches` and `absent`, over the path syntax
`ir.packs[0].texts.entries['en_US']['pack.name']`. Bracket keys need quoting when the key contains a
dot, which Bedrock keys constantly do.

`notContains` is the mirror of `forbidden` below, and deserves the same liberal use: on an array it
checks membership rather than substring, so it means what it looks like.

## Diagnostics are part of the contract

A case may assert `expected` and `forbidden` diagnostic codes, and `exhaustive: true` makes any
unlisted diagnostic a failure.

Use `forbidden` liberally. A feature that starts emitting a warning it did not emit before is a
regression, and it is exactly the kind that otherwise goes unnoticed for months.

## The Via equivalence case

`case.yaml`'s `via:` block runs the case natively **and** through version translation, then requires
the client state traces to be **byte-identical** (SC-270 §13). This is the normative definition of
"works through ViaVersion" and it is not optional for anything visible over the network.

## Acceptance add-ons

`manual/acceptance/` tracks a small set of real, popular Bedrock add-ons as targets. They are **not
committed** — they are third-party content. Each has a manual checklist naming the add-on, where to
get it, and what should work.

What those add-ons actually use is what drives scope (`process.md` §1). Implementing the
specification in feature-ID order is how this project dies.
