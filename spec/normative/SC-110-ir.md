# SC-110 — Intermediate representation

**Status:** complete · **Since:** 0.1.0 · **Supersedes:** —

The Minecraft-free, `format_version`-normalised data model that every add-on file parses into. It is
the boundary between `core/` and everything else, and the reason constitution rule 3 is enforceable.

The machine-readable half of this document is `spec/schemas/ir/*.json`. Where prose and schema
disagree, the schema governs for *shape* and this document governs for *meaning*.

---

## 1. What the IR is for

Three jobs, and it is worth being explicit because they pull in different directions:

1. **Erase `format_version`.** A single real pack contains files declaring `1.8.0` through
   `1.26.30`. Nothing downstream should ever branch on a Bedrock version. That normalisation happens
   here, once.
2. **Describe Bedrock, not Minecraft.** The IR has no `BlockState`, no `ResourceLocation`, no
   `Goal`. It says what the add-on says. Translation to Java vocabulary happens above it, per
   Minecraft version.
3. **Survive unknown input.** An IR object always parses. Unrecognised keys are preserved, malformed
   values are dropped with a diagnostic, and the object is still usable.

What the IR is explicitly **not**: a validated model. It does not guarantee that a referenced
geometry exists, that a Molang expression type-checks, or that a component combination is coherent.
Those are resolution and runtime concerns.

## 2. Global rules

- **Immutable.** Every IR type is a Java `record` or an immutable collection. Nothing downstream
  mutates the IR; hot reload replaces whole IR graphs (constitution rule 7).
- **No `null` in public accessors.** Absent scalars are `OptionalX` or a documented sentinel; absent
  collections are empty. `null` inside the IR is a defect.
- **No Minecraft, no logging, no I/O.** `core/format` depends on a JSON facade, the diagnostics
  value type, and nothing else.
- **Numbers are `float`** unless a field is inherently integral (counts, indices, ticks). SC-000 §7.
- **Every type carries provenance.** See §4.

### 2.1 The JSON facade

`core/format` **MUST NOT** depend on Gson, Jackson or `com.google.gson` types in its public API. It
parses through:

```java
public sealed interface JsonValue permits JsonObject, JsonArray, JsonString, JsonNumber,
                                          JsonBool, JsonNull { }
```

*Why:* whether Gson remains on the Minecraft classpath is a per-version accident, `core/` must be
usable without Minecraft at all, and a facade makes the parser swappable when a faster one is worth
having. The cost is one adapter per backend, which is small.

JSON parsing accepts what Bedrock accepts, which is more than strict JSON: **trailing commas**,
**`//` and `/* */` comments**, and unquoted control characters in strings. Real packs contain all
three. Duplicate keys are an error (SC-000 §6). A BOM is stripped.

## 3. `format_version` normalisation

### 3.1 The dispatcher

```java
public interface FormatParser<T> {
    ParseResult<T> parse(JsonObject root, ParseContext ctx);
}

public interface ParserDispatch<T> {
    FormatParser<T> select(JsonObject root, ParseContext ctx);
}
```

Selection is by `(content kind, effective format version)`. The **effective** version is not
necessarily the declared one:

1. If the file declares `format_version` and a parser is registered for it, use the **highest
   registered version not exceeding** the declared one. Bedrock's own semantics are "this file was
   authored against version V", and a parser registered at 1.21.0 handles files declaring 1.21.40
   unless a 1.21.40 parser was added.
2. If the declared version is **below** the lowest registered parser, use the lowest and emit
   `SCE-1030`.
3. **Structural sniffing overrides a declared version that is inconsistent with the file's shape.**

Rule 3 is not an optimisation, it is a correctness requirement. Real packs declare `1.8.0` on files
in the `1.12.0` geometry shape and vice versa; the authoring tools have shipped this bug for years.
The canonical case:

| Shape | Family |
|---|---|
| root has a `minecraft:geometry` array | modern (`1.12.0`+) |
| root has keys matching `geometry\..*` | legacy (`1.8.0`) |

When the sniffed family contradicts the declared version, the **sniffed** family wins and `SCE-1031`
records both. Sniffing rules are per content kind and are specified in the relevant domain document
(geometry: SC-180 §3).

### 3.2 Version ladders

Where Mojang changed the *meaning* of a field rather than its shape, the IR models the newest
meaning and older parsers upgrade into it. Each such upgrade is a named, tested transform:

```java
public interface IrUpgrade<T> { T upgrade(T older, ParseContext ctx); }
```

Upgrades are pure and independently unit-tested against the `bedrock-samples` corpus. Chains are
applied lowest-to-highest. An upgrade that cannot preserve meaning emits a diagnostic and records
the loss in `Provenance.lossy` (§4).

**The IR never encodes "which version this came from" as a behavioural switch.** If downstream code
needs to know the origin version, that is a design failure in this document; report it.

## 4. Provenance

Every IR node reachable from a pack carries:

```java
public record Provenance(
    PackId pack,
    String path,             // VFS path within the pack
    String jsonPointer,      // RFC 6901, position within the file
    String declaredVersion,  // as written, may disagree with effective
    String effectiveVersion,
    boolean lossy            // an upgrade or clamp discarded information
) {}
```

*Why it is mandatory:* constitution rule 8. A diagnostic that says "unknown component" without
naming the pack, the file and the line is useless to an add-on author who has forty packs installed.
Provenance is also what makes hot reload able to report *which* pack changed.

Provenance is interned aggressively — `pack` and `path` are shared references, and `jsonPointer` is
built lazily from a parse-time cursor — so the cost is a few bytes per node, not a string per node.

## 5. Unknown data

```java
public record UnknownData(Map<String, JsonValue> keys) {}
```

Every IR record that models a JSON object has an `unknown()` accessor. Parsers put keys they do not
recognise there, **verbatim**.

This is load-bearing for three reasons: a future version can implement a feature without re-parsing;
diagnostics can list precisely what was ignored; and a round-trip test can prove the parser is not
silently dropping data. `core/format` ships a test that re-serialises every `bedrock-samples` file
from its IR plus its unknown bag and asserts semantic equality with the input.

Unknown data is **not** carried into runtime objects above the IR. It stops at the translation
boundary.

## 6. Shared value types

These appear throughout and are defined once.

| Type | Notes |
|---|---|
| `BedrockId` | namespaced identifier; namespace defaults to `minecraft` when absent. Preserves original case, compares case-insensitively. |
| `SemanticVersion` | SC-100 §4.3 |
| `BedrockVersion` | engine version, e.g. `1.26.30`; ordered |
| `MolangExpr` | a parsed, not-yet-bound Molang expression. **Never a raw string** — see §7. |
| `MolangFloat` | a field that is either a constant or a `MolangExpr`; constants are folded at parse time |
| `Filter` | the 106-test predicate tree, SC-140 |
| `Vec3f`, `Vec2f` | float triples/pairs; Bedrock's axis conventions preserved verbatim, **not** converted to Java's |
| `RangeF`, `RangeI` | Bedrock's `[min, max]` or scalar shorthand; a scalar means `[v, v]` |
| `Tick` | integral ticks; conversion from seconds happens at parse time per SC-000 §8 |
| `TexturePath` | a pack-relative texture reference, **without** extension, as Bedrock writes it |

### 6.1 Coordinate conventions

Bedrock and Java disagree about handedness and pivot conventions in geometry and animation. **The IR
preserves Bedrock's convention unchanged.** Conversion happens in the renderer (SC-180), once, where
it can be tested against a rendered image.

Converting in the parser was considered and rejected: it would make the IR untestable against
Mojang's own sample data, and it would bake a rendering decision into a module that must not know
about rendering.

## 7. Molang is parsed, never stored as text

A field that Bedrock allows to be a Molang expression is `MolangExpr` in the IR, parsed at ingest.

*Why:* parse errors surface at load with provenance, rather than mid-frame with none; constant
expressions fold once instead of per frame; and the set of queries a pack references is known
statically, which is what lets the runtime pre-bind them (SC-130).

A Molang field that fails to parse becomes `MolangExpr.constant(0)`, emits `SCE-1040` with the
source text and column, and sets `Provenance.lossy`. Bedrock's own behaviour on a bad expression is
to yield 0, so this matches.

Constant folding at parse time **MUST** use `float` arithmetic (SC-000 §7). Folding in `double` and
narrowing later changes which branch a pack takes.

## 8. The pack IR

```java
public record AddonIr(
    List<PackIr> packs,             // load order, SC-100 §5
    IrIndex index,                  // §9
    List<Diagnostic> diagnostics
) {}

public record PackIr(
    PackId id,
    PackHeader header,
    BehaviorIr behavior,            // empty if the pack has no `data` module
    ResourceIr resource,            // empty if the pack has no `resources` module
    ScriptIr scripts,               // empty if no `script` module
    Localisation texts
) {}
```

### 8.1 `BehaviorIr`

| Field | Type | Domain doc |
|---|---|---|
| `entities` | `Map<BedrockId, EntityDefIr>` | SC-160 |
| `blocks` | `Map<BedrockId, BlockDefIr>` | SC-150 |
| `items` | `Map<BedrockId, ItemDefIr>` | SC-170 |
| `recipes` | `Map<BedrockId, RecipeIr>` | SC-190 |
| `lootTables` | `Map<String, LootTableIr>` | SC-190 |
| `trading` | `Map<String, TradeTableIr>` | SC-190 |
| `spawnRules` | `Map<BedrockId, SpawnRuleIr>` | SC-190 |
| `functions` | `Map<String, McFunctionIr>` | SC-190 |
| `serverAnimations` | `Map<BedrockId, AnimationIr>` | SC-180 |
| `serverAnimControllers` | `Map<BedrockId, AnimControllerIr>` | SC-180 |
| `biomes`, `features`, `featureRules`, `dialogue`, `cameras`, `aimAssist`, `itemCatalog`, `structures`, `voxelShapes` | — | outline domains |

Loot tables and functions are keyed by **path**, not `BedrockId`, because Bedrock references them by
path (`loot_tables/entities/foo.json`). Everything else is keyed by identifier.

### 8.2 `ResourceIr`

| Field | Type |
|---|---|
| `clientEntities` | `Map<BedrockId, ClientEntityIr>` |
| `geometries` | `Map<String, GeometryIr>` — keyed by `geometry.<name>` identifier |
| `renderControllers` | `Map<String, RenderControllerIr>` |
| `animations` | `Map<String, AnimationIr>` |
| `animControllers` | `Map<String, AnimControllerIr>` |
| `particles` | `Map<BedrockId, ParticleIr>` |
| `attachables` | `Map<BedrockId, AttachableIr>` |
| `materials` | `Map<String, MaterialIr>` |
| `textureAtlases` | `TextureAtlasIr` — terrain, item, flipbook |
| `sounds` | `SoundIr` — `sounds.json` and `sound_definitions.json` |
| `fogs`, `clientBiomes`, `ui`, `fonts` | outline domains |

Raw binary assets (`.png`, `.ogg`, `.material` payloads) are **not** loaded into the IR. The IR
holds paths; the asset pipeline reads them through the VFS on demand. Loading a 300 MB texture set
into an in-memory IR would be indefensible.

### 8.3 `ScriptIr`

Entry point path, declared `@minecraft/*` module dependencies with versions, and the set of
JavaScript source paths. **No parsing of JavaScript happens in `core/format`** — that is SC-200's
concern and it needs a JS engine, which `core/format` must not depend on.

## 9. `IrIndex` — cross-pack resolution

`PackIr` is per pack. Almost everything downstream wants the *merged* view, and merge semantics
differ per content kind. `IrIndex` is that merged view, computed once after all packs parse.

```java
public interface IrIndex {
    Optional<EntityDefIr>      entity(BedrockId id);
    Optional<BlockDefIr>       block(BedrockId id);
    Optional<GeometryIr>       geometry(String id);
    List<PackId>               providersOf(BedrockId id);   // for diagnostics
    // …one accessor per content kind
}
```

### 9.1 Merge rules

| Kind | Rule when two packs provide the same key |
|---|---|
| Resource-pack assets (geometry, textures, animations, render controllers, materials, particles, client entities, attachables) | **Later pack in load order wins wholesale.** This is Bedrock's behaviour and it is what pack authors rely on for retexture packs. `SCE-2010`, severity *info*. |
| Behavior-pack definitions (entity, block, item) | **Later pack in load order wins wholesale**, but `SCE-2011` at severity *warning* — two behavior packs defining the same entity is nearly always an authoring mistake, unlike the RP case. |
| Loot tables, recipes, trade tables, functions | later wins, `SCE-2010` |
| `sound_definitions.json`, `terrain_texture.json`, `item_texture.json`, `flipbook_textures.json` | **Merged key-by-key**, later wins per key. These are registries, not documents, and Bedrock merges them. |
| `sounds.json` | merged key-by-key |
| `texts/*.lang` | merged key-by-key |

There is **no** deep merge of an entity or block definition across packs. Bedrock does not do it and
attempting it produces incoherent component sets.

### 9.2 Resolution is lazy and diagnostic

`IrIndex` lookups that miss return empty and **do not** emit a diagnostic — a missing geometry is
only a problem if something references it. Reference validation is a separate pass
(`IrIndex.validate()`) that walks known reference sites and emits `SCE-2012` per dangling reference,
run once after indexing. Splitting it this way keeps lookups cheap on the hot path.

## 10. Determinism

Given the same set of pack files, `AddonIr` **MUST** be identical, including collection iteration
order. Maps in the IR are insertion-ordered (`LinkedHashMap`-backed or sorted), never
`HashMap`-backed, and insertion follows load order then archive-entry order.

This is not fastidiousness: identifier collision tiebreaks (SC-120 §3) and the ledger depend on it,
and a non-deterministic ledger corrupts worlds.

## 11. Testing contract

`core/format` **MUST** maintain three test families:

1. **Corpus parse** — every JSON file in the pinned `bedrock-samples` snapshot parses without an
   error-severity diagnostic. This is the regression net for `format_version` handling.
2. **Round trip** — IR plus unknown bag re-serialises to something semantically equal to the input,
   for the whole corpus. Proves nothing is silently dropped.
3. **Golden IR** — a small hand-written set of files with committed canonical-JSON IR dumps
   (SC-000 §6), so that an unintended IR change shows up as a reviewable diff.

The corpus is fetched, never vendored (constitution rule 10). Tests skip with a clear message when
it is absent, so a clean clone still builds offline.

## 12. Open questions

- `TODO(SC-110)`: whether `structures` (`.mcstructure`, little-endian NBT with a palette/index
  layout) belongs in the IR at all, or is better modelled as an opaque asset resolved at placement
  time. Leaning opaque; decide before SC-190 leaves outline.
- `TODO(SC-110)`: JSON UI's `modifications[]` patching of vanilla screens has no obvious IR shape
  that is not just "the JSON". Deferred with the rest of SC-180's UI section.
