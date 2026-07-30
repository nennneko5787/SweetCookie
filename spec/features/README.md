# Feature work units

Transient working directories, one per unit of work. Numbered sequentially, four digits.

**Japanese is fine here.** These are scratch notes, they are archived when the work lands, and
forcing English buys nothing (`constitution.md` rule 11). Everything else in `spec/` is English.

```
spec/features/NNNN-slug/
├── spec.md     what the Bedrock feature does
├── plan.md     how we will do it on Java
└── tasks.md    a checklist
```

| File | Contains |
|---|---|
| `spec.md` | What the feature does, from Mojang's documentation **and from observation**. Where the documentation is wrong, say so and say how you know — that is the most valuable thing in the file. |
| `plan.md` | The Java approach: which IR types, which classes, and what the fidelity gap will be. Decide the gap here, not after the fact. |
| `tasks.md` | A checklist. Tick as you go. |

Move the directory to `_archive/` when the coverage entry is promoted.

## Scope

A work unit corresponds to **one or more coverage entries**. If it does not, it is infrastructure —
still gets a directory, still amends a normative document, but has no coverage entry and no
promotion step (`process.md` §7).

**If a feature has no coverage entry, it is out of scope.** Stop and ask. Do not create a coverage
entry to justify work; entries arrive through the upstream-diff flow or an explicit scoping
decision, and both leave a record.

## Where the first units come from

Not from the top of the feature list. From the acceptance add-ons (`process.md` §1) — what real,
popular packs actually use. Implementing 2 500 feature identifiers in order is how this project
dies.
