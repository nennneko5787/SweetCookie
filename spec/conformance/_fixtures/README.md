# Fixtures

Reusable pieces so that a case contains only what it is actually testing.

| Fixture | What it is |
|---|---|
| `minimal_bp/` | a behaviour pack with a valid `manifest.json` and nothing else |
| `minimal_rp/` | a resource pack with a valid `manifest.json` and nothing else |
| `flat_world/` | a fixed, seeded superflat world with fixed time, weather and gamerules |

A case extends a fixture with `pack: { extends: minimal_bp }` and supplies only the files that
differ. A case that repeats a manifest is a case whose diff is mostly noise, and the manifest will
drift from the fixture over time.

## UUIDs

Fixture UUIDs are fixed and follow the pattern `5c00c1e0-0000-4000-8000-0000000000NN`
(`5c00c1e0` ≈ "SweetCookie"). They are deliberately recognisable in a log, so that a diagnostic
naming one is instantly identifiable as coming from the test corpus rather than a user's pack.

A case needing its own pack identity allocates from `5c00c1e0-0001-4000-8000-…` and records it in
its `case.yaml`.

## Originality

Constitution rule 10. Everything here was written for this project. Textures are solid colours,
models are hand-authored, nothing is copied from a community pack, from `bedrock-samples`, or from
the game.
