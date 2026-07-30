# SweetCookie documentation

**Nothing here is normative.** The specification is [`spec/`](../spec/); this directory is either
generated from it or written for users.

| Path | Audience | Written by |
|---|---|---|
| `compatibility/` | anyone asking "does my add-on work?" | **generated** — do not edit |
| `guide/` | people installing and using the mod | hand-written |
| `troubleshooting/` | anyone who hit an `SCE-####` code | generated from SC-240 |
| `ja/` | Japanese readers | hand-written translations |

## Compatibility

SweetCookie implements someone else's specification, so "what works" is tracked explicitly rather
than claimed. [`compatibility/summary.md`](compatibility/summary.md) is produced by `specReport`
from [`spec/coverage/`](../spec/coverage/), and CI fails if it is stale.

Every Bedrock feature identifier has a status, the implementing class, an observable fidelity note
and a link to the conformance test that proves it — and the build refuses to let any of those be
dishonest.

## Generated content

`compatibility/**` is **committed** so it renders on GitHub without a Pages build, and CI fails if
`specReport` would change it — the same discipline as committed generated code. Editing a generated
file by hand will be reverted by the next build.

## Diagnostics

Every degradation SweetCookie performs emits a code. Searching for `SCE-2001` should land on a page
in `troubleshooting/` explaining what it means and what, if anything, an add-on author can do about
it. That is the payment for constitution rule 1: unknown input degrades silently *to the game*, but
never silently *to the user*.
