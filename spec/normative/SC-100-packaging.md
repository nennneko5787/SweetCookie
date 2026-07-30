# SC-100 — Packaging and manifests

**Status:** complete · **Since:** 0.1.0 · **Supersedes:** —

How a file on disk becomes a set of parsed, ordered, dependency-resolved packs ready for SC-110
parsing. Everything here lives in `core/format` and has no Minecraft dependency.

---

## 1. Scope

Covers: archive containers, safe extraction, `manifest.json` in all three format versions,
dependency resolution, subpack selection, pack ordering, and the virtual file system that later
stages read through.

Does not cover: the content of any file other than `manifest.json` (SC-110 and the domain
documents), or registration (SC-120).

## 2. Containers

| Extension | Contents |
|---|---|
| `.mcpack` | ZIP. One pack. `manifest.json` at the archive root. |
| `.mcaddon` | ZIP. Several packs, as nested `.mcpack`/`.mcworld` entries **or** as loose directories side by side. |
| `.mcworld`, `.mctemplate` | ZIP. A world: `level.dat`, `db/`, `behavior_packs/`, `resource_packs/`, `world_*_packs.json`. |
| `.zip` | Treated as `.mcpack` if a root `manifest.json` exists, else as `.mcaddon`. |
| *(directory)* | A pack or add-on unpacked on disk. Supported for development. |

### 2.1 Discovery

The implementation **MUST** locate packs by **recursively searching for `manifest.json` at any
depth**, not by assuming a layout. `.mcaddon` nesting is not normalised in practice: real add-ons
put packs at the root, in one subdirectory each, inside nested `.mcpack` files, or in a mixture.

Nesting depth **MUST** be bounded (§3.3). A `manifest.json` found inside a directory that already
belongs to a discovered pack **MUST NOT** start a second pack — the outermost `manifest.json` on any
path wins, and an inner one is reported as `SCE-1010` and ignored. Otherwise a pack that happens to
ship a sample `manifest.json` as documentation would be mis-detected.

### 2.2 World containers

`.mcworld` and `.mctemplate` are recognised, and their embedded `behavior_packs/` and
`resource_packs/` directories are ingested as packs. The world data itself — `level.dat` and the
LevelDB `db/` directory — is **out of scope for 0.x**; it is reported as `SCE-1011` (informational)
and skipped. Importing a Bedrock world is a separate project.

## 3. Safe extraction

An add-on is untrusted input (constitution rule 1, SC-260). Extraction **MUST** enforce every limit
below and **MUST** abort the offending *pack* — not the whole load — when one is exceeded.

| Limit | Default | Diagnostic |
|---|---|---|
| Path escaping the extraction root after normalisation (zip-slip) | forbidden | `SCE-1001` |
| Absolute paths, drive letters, or `..` segments in an entry name | forbidden | `SCE-1001` |
| Symlink or non-regular entries | forbidden | `SCE-1002` |
| Total uncompressed size | 512 MiB | `SCE-1003` |
| Compression ratio, any single entry | 200:1 | `SCE-1003` |
| Entry count | 65 536 | `SCE-1004` |
| Nesting depth (archive within archive) | 3 | `SCE-1005` |
| Single file size | 64 MiB | `SCE-1006` |
| Path length after normalisation | 512 chars | `SCE-1007` |

Entry names are decoded as UTF-8. Names that are not valid UTF-8, or that differ from their own
Unicode NFC normalisation, are rejected with `SCE-1008`: they are the standard vector for making two
distinct entries collide on a case-insensitive filesystem.

**Path comparison is case-insensitive and `/`-separated throughout.** Bedrock is authored mostly on
case-insensitive filesystems and real packs reference `Textures/Blocks/Foo.PNG` for
`textures/blocks/foo.png`. Two entries whose normalised lowercase paths collide are `SCE-1009` and
the **first in archive order** wins.

Limits are configurable; the defaults above are what the conformance corpus asserts.

## 4. `manifest.json`

### 4.1 Format versions

| `format_version` | Used by | Notes |
|---|---|---|
| `1` | skin packs | versions are `[major, minor, patch]` arrays |
| `2` | resource packs, behavior packs, world templates | the overwhelmingly common case |
| `3` | preview, since Bedrock 1.21.110 | versions become **SemVer strings**; adds pack `settings` |

The implementation **MUST** accept all three and normalise into one IR shape. An unrecognised
`format_version` is `SCE-1020` and the manifest is parsed **optimistically** as version 2, because
Mojang's history is of additive change.

### 4.2 Fields

`header` (required):

| Field | Required | IR |
|---|---|---|
| `uuid` | yes | `PackId` |
| `name` | yes | raw string; may be a `.lang` key (§8) |
| `description` | no | raw string; may be a `.lang` key |
| `version` | yes | `SemanticVersion`, normalised from array or string |
| `min_engine_version` | RP/BP: yes | `BedrockVersion`; drives §6 gating |
| `base_game_version` | no | world templates |
| `pack_scope` | no | `world` \| `global` \| `any`; default `any` |
| `lock_template_options`, `allow_random_seed` | no | world templates only; ignored |

`modules` (required, ≥ 1). Each has `type`, `uuid`, `version`, optional `description`:

| `type` | Meaning |
|---|---|
| `resources` | resource pack half |
| `data` | behavior pack half |
| `script` | JavaScript; carries `language: "javascript"` and `entry` |
| `client_data` | appears in Microsoft's own examples; treated as `data` with a `SCE-1021` note |
| `world_template` | world template |
| `skin_pack` | skins; recognised, `unsupported` |

A pack **MAY** declare several modules; a pack with both `resources` and `data` is a single pack
providing both halves and **MUST** be modelled as one `Pack` with two content roots, not two packs.

`entry` on a `script` module is not in Microsoft's published field table but is universal in
practice. It is parsed. A `script` module without `entry` defaults to `scripts/main.js` and emits
`SCE-1022`.

`dependencies` (optional) — two disjoint shapes in one array:

```jsonc
{ "uuid": "…", "version": [1, 0, 0] }              // another pack
{ "module_name": "@minecraft/server", "version": "2.8.0" }  // a built-in script module
```

Distinguished by which key is present. Both **MUST** be supported; an entry with neither is
`SCE-1023` and is dropped.

`capabilities` (optional): `chemistry`, `editorExtension`, `experimental_custom_ui`, `raytraced`,
`pbr`. Recorded in the IR; each is independently `unsupported` in 0.x and produces one `SCE-2001`
per capability at load, once per pack.

`metadata` (optional): `authors`, `license`, `url`, `product_type`, `generated_with`. Recorded
verbatim; `generated_with` is genuinely useful for diagnostics because it names the authoring tool.

`subpacks` (optional): see §7.

### 4.3 Version normalisation

Format 1 and 2 use `[major, minor, patch]`; format 3 uses a SemVer string. Both normalise to
`SemanticVersion(major, minor, patch, prerelease?, build?)`. An array of other than three elements
is `SCE-1024`; missing elements default to 0 and extra elements are dropped.

Comparison is SemVer precedence. Bedrock itself is looser than this, but no observed real-world pack
depends on the difference.

### 4.4 UUIDs

`header.uuid` identifies the pack; module UUIDs identify modules. Both are parsed leniently: a
malformed UUID emits `SCE-1025` and is replaced by `UUID.nameUUIDFromBytes` of the raw string, so
the pack still loads and still has a stable identity. Real packs ship malformed UUIDs often enough
that rejecting them would reject useful content.

**Two loaded packs with the same `header.uuid` and the same `version`** is `SCE-1026`: the one later
in load order (§5) wins entirely and the earlier is dropped. Same UUID with **different** versions
selects the highest version and emits `SCE-1027`.

## 5. Pack load order

Deterministic, and it decides override precedence for §9 and identifier-collision tiebreaks
(SC-120).

Order is, in decreasing precedence:

1. Explicit order recorded in the world's activation file, for packs that appear in it.
2. For the remainder: ascending by `(sanitised source path, header.uuid)` — a total order over
   strings, independent of filesystem enumeration.

Within a single `.mcaddon`, archive entry order is *not* used, because it is not stable across
re-zipping. The rule above uses the pack's path *inside* the container, which is.

The IR records the resolved order explicitly; nothing downstream may re-derive it.

## 6. `min_engine_version` gating

`min_engine_version` states the oldest Bedrock engine the pack claims to need. SweetCookie declares
a **target Bedrock engine version** (a single constant, currently `1.26.30`, recorded in
`spec/upstream/bedrock-samples.lock.json`).

- `min_engine_version` **greater** than the target: the pack is loaded anyway, with one `SCE-2002`
  naming both versions. Refusing would make the mod useless the day Bedrock ships an update.
- `min_engine_version` absent on an RP or BP: `SCE-1028`, treated as `1.16.0`.

`min_engine_version` **MUST NOT** be used to select parser behaviour. Per-file `format_version` does
that (SC-110 §3), and the two disagree constantly in real packs.

## 7. Subpacks

`header.subpacks[]` declares variants selected by the client's memory tier:

```jsonc
{ "folder_name": "hd", "name": "HD Textures", "memory_tier": 4 }
```

One `memory_tier` unit is 0.25 GiB. Files under `subpacks/<folder_name>/` **override** same-path
files in the pack root when that subpack is selected.

**Selection is a configuration decision, not an automatic one.** SweetCookie **MUST NOT** infer a
tier from the host's RAM: Java clients and servers have unrelated memory characteristics, and a
server picking a tier on the client's behalf is wrong in a multiplayer context. The default is to
select the **highest-tier subpack whose `memory_tier` does not exceed a configured ceiling**, with
the ceiling defaulting to the highest tier the pack offers.

Subpack selection is part of the virtual file system (§9), not a copy: files are resolved through an
overlay so that reloading with a different selection needs no re-extraction.

## 8. `texts/` and localisation

`texts/<locale>.lang` files are key/value with `##` comments, and `texts/languages.json` lists the
locales the pack ships. Manifest `name` and `description` are frequently `.lang` keys such as
`pack.name`.

The IR stores the raw string **and** the resolved value for each available locale. Resolution to a
Java translation key is SC-110's concern, not this document's.

`.lang` parsing: `key=value`, first `=` splits, keys and values are **not** trimmed of internal
whitespace but are trimmed of trailing `\r`. A line whose first non-space characters are `##` is a
comment. A trailing `#comment` on a value line is **not** stripped — Bedrock does not strip it, and
packs contain `#` in values.

## 9. The virtual file system

Everything after this document reads through one interface:

```java
public interface PackVfs {
    Optional<ByteSource> read(String path);       // case-insensitive, '/'-separated, root-relative
    List<String> list(String directory);          // non-recursive, normalised paths
    Stream<String> walk(String directory);        // recursive
    boolean exists(String path);
}
```

A pack's VFS is a **stack of layers**, highest precedence first:

1. the selected subpack (`subpacks/<folder>/…` remapped to root-relative), §7
2. the pack root
3. nothing — there is no cross-pack fallback at this level

Cross-*pack* override (a later pack in load order replacing an earlier pack's file) happens at the
IR merge stage in SC-110, **not** here, because Bedrock's merge semantics differ per content type:
a resource-pack texture is replaced wholesale, whereas two behavior packs defining the same entity
identifier is a conflict with its own rule. Flattening both into one file lookup would lose that
distinction.

The VFS is lazy: entries are read from the archive on demand and are not extracted to disk. This
matters because a large add-on is hundreds of megabytes and only a fraction is ever read.

## 10. Dependency resolution

Pack dependencies (`{uuid, version}`) form a directed graph over loaded packs.

- A dependency on a pack that is not loaded is `SCE-2003`. The dependent pack still loads: Bedrock
  itself only warns, many real packs list stale dependencies, and refusing would break them.
- A dependency on a *lower* version than is loaded is satisfied. On a *higher* version, `SCE-2004`,
  and it is still satisfied, for the same reason as §6.
- **Cycles are permitted.** Bedrock allows a BP and its paired RP to depend on each other, and that
  is the common case. The graph is used for ordering hints only; it **MUST NOT** be topologically
  sorted in a way that fails on a cycle. Where ordering matters, §5 governs.

Module dependencies (`{module_name, version}`) declare which `@minecraft/*` API a script module
needs. They are recorded and checked against SC-200's supported set; an unsupported module or a
version outside the supported range disables **that script module only**, with `SCE-2005`.

## 11. What the stage produces

```java
public record LoadedAddon(
    List<LoadedPack> packs,          // in resolved load order, §5
    List<Diagnostic> diagnostics
) {}

public record LoadedPack(
    PackId id,                       // header.uuid, normalised
    SemanticVersion version,
    PackHeader header,
    List<PackModule> modules,
    List<PackDependency> dependencies,
    Set<Capability> capabilities,
    SubpackSelection subpacks,
    Localisation texts,
    PackVfs vfs,
    PackSource source,               // where it came from, for diagnostics and reload
    int loadOrder
) {}
```

No file inside the pack other than `manifest.json` and `texts/**` has been read at this point.
Everything else is deferred to SC-110's parser dispatch, which reads through `vfs`.

## 12. Reload

A pack is re-ingested from `source` on `/sweetcookie reload`. The VFS is discarded and rebuilt; the
`PackId` is stable because it comes from the manifest. A pack whose manifest changed such that its
UUID or version differs is treated as a **different pack** for ledger purposes (SC-120), which is
what makes schema-drift detection possible.

Archives are not held open between reloads. Holding a `ZipFile` open across a reload prevents the
user from replacing the file on Windows, which is the platform most add-on authors use.

## 13. Diagnostics allocated here

`SCE-1001`–`SCE-1028` (parse, listed inline above) and `SCE-2001`–`SCE-2005` (semantic). The
authoritative table is SC-240; this document allocates the ranges and must be kept consistent with
it by `specValidate`.
