# SC-240 — Diagnostics

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

Constitution rule 8: Bedrock fails silently; we do not. Every degradation, refusal, clamp and
unsupported construct emits a coded, located, human-readable diagnostic — and the user can see it
without reading a log file.

This matters more here than in a normal mod. Constitution rule 1 means bugs *hide* as silent no-ops,
and this document is what pays for that.

---

## 1. The `Diagnostic` value

```java
public record Diagnostic(
    int code,                    // SCE-####
    Severity severity,           // ERROR | WARNING | INFO
    String messageKey,           // translation key; never a formatted string
    List<Object> args,
    Optional<Provenance> where,  // pack, file, JSON pointer (SC-110 §4)
    Optional<String> featureId   // the Bedrock feature this concerns, when applicable
) {}
```

- **`core/` returns diagnostics, never logs them** (SC-000 §10). It has no logging dependency.
- Messages are translation keys, so the in-game surface can be localised and the log can be English.
- `featureId` is what links a diagnostic to a coverage entry, and therefore what turns user reports
  into a demand signal (`process.md` §1).

## 2. Severity

| | Meaning | Surface |
|---|---|---|
| `ERROR` | content did not load, or something is broken | in-game on join for operators; always logged |
| `WARNING` | content loaded with reduced fidelity | in-game summary; logged |
| `INFO` | a normal, expected difference | `/lepus diagnostics` only; logged at debug |

An unimplemented feature that a pack uses is `WARNING`, not `ERROR`: the pack still works, mostly.
Reserving `ERROR` for genuine breakage is what keeps it meaningful.

## 3. Deduplication

Diagnostics are on hot paths — a filter with an unknown test runs every tick, per entity. Every
diagnostic site therefore has a **dedup key**, by default `(code, provenance)`, and is emitted at
most once per key per load. A count accompanies it (`… and 412 more occurrences`).

Emitting per occurrence would flood the log, and a flooded log is the same as no log.

Deduplication happens **on report, not on read**. A collector that stored every occurrence and
filtered later would not stop the allocation, which is the thing being prevented.

The count is why the result of a load is not a bare `List<Diagnostic>`:

```java
public record DiagnosticLog(List<Occurrence> occurrences) {
    public record Occurrence(Diagnostic diagnostic, int count) {}
}
```

`DiagnosticLog.merge` **concatenates and does not re-deduplicate**. Two packs reporting the same
code at different locations are two reports, and collapsing them would hide the second pack — which
is exactly what provenance exists to prevent.

Each specification document's codes are declared as constants in **one holder per module**, never
written inline at the emitting site. §5 forbids reusing or renumbering a code, and that is only
checkable if allocation happens somewhere a test can enumerate.

## 4. Surfaces

| Surface | What appears |
|---|---|
| Log | everything, one line each, with the code first for greppability |
| Join message | for operators and in single-player: a one-line summary with counts and a pointer to the command |
| `/lepus diagnostics [pack] [severity]` | the full list, paginated, clickable to copy |
| `/lepus why <bedrockId>` | why one specific piece of content is not behaving — the most useful command in the mod |
| Add-on management screen | a per-pack badge |

`TODO(SC-240)`: the exact wording rules. A diagnostic must name what was affected, what happened
instead, and — where one exists — what the author can do. "Unknown component" is a bad message;
"`minecraft:behavior.swim_wander` is not implemented, so the entity will not wander while swimming"
is a good one.

## 5. Code space

Ranges are allocated in `ids.md` §2 and assigned here. Codes are **never reused and never
renumbered**: users search the internet for them.

| Range | Class | Allocated in |
|---|---|---|
| 1000–1999 | Parse | SC-100 (1001–1029), SC-110 (1030–1040) |
| 2000–2999 | Semantic | SC-100 (2001–2005), SC-110 (2010–2012), SC-120 (2020–2021) |
| 3000–3999 | Runtime | SC-120 (3020), SC-130, SC-160, SC-200 |
| 4000–4999 | Registration and persistence | SC-120 (4001, 4010–4015) |
| 5000–5999 | Networking | SC-270 (5001–5012) |
| 6000–6999 | Interoperability | SC-230 (6001–6004), SC-210 (6010–6014) |

`specValidate` checks that every code referenced anywhere in `spec/` or in code appears in the table
below, that none is allocated twice, and that none is missing a message key.

`TODO(SC-240)`: the full table with one row per code — message key, arguments, severity, dedup key,
and the emitting site. Generated into `docs/troubleshooting/` so a user searching a code finds a
page.

## 6. Retirement

A code that stops being emitted is marked `retired` with the version, and its documentation page
stays up saying so. It is never reused.

## 7. Testing contract

- Every allocated code has at least one test that provokes it.
- A test asserts no code is allocated twice and every referenced code exists.
- A test asserts deduplication actually caps output, by running a pack that would otherwise emit
  thousands of identical diagnostics.
- A lint rejects a diagnostic whose message key has no translation in `en_us`.
