# SC-170 — Items

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

Bedrock's 44 item components, mapped onto Java data components and Lepus behaviour hooks.
Registration is SC-120 §4: **one** carrier `Item`, identity in `minecraft:custom_data`.

---

## 1. Decisions already taken

**Everything that can be a Java data component, is one.** Since 1.20.5, stack size, durability,
food, tool behaviour, rarity, display name, model and equippability are per-stack components, which
is exactly why items need no registrations and hot-plug completely.

**Everything else is a behaviour hook** on the carrier item, dispatching through the IR by the
stack's Bedrock identity.

**Attachables are handled here, not in SC-180.** A Bedrock attachable is an item's third-person and
first-person 3D model with its own animations and render controllers, and it is the item's
appearance, so it belongs with items even though the file lives in the resource pack.

## 2. The 44 components

| Bedrock | Java |
|---|---|
| `max_stack_size` | `minecraft:max_stack_size` |
| `durability` | `minecraft:max_damage` + `minecraft:damage` |
| `damage` | `minecraft:attack_damage` via attribute modifiers |
| `food` | `minecraft:food` + `minecraft:consumable` |
| `display_name` | `minecraft:item_name` |
| `icon` | `minecraft:item_model` → a generated `items/` model definition |
| `rarity` | `minecraft:rarity` |
| `glint` | `minecraft:enchantment_glint_override` |
| `wearable` | `minecraft:equippable` |
| `enchantable` | `minecraft:enchantable` |
| `repairable` | `minecraft:repairable` |
| `fuel` | fuel registry |
| `cooldown` | `minecraft:use_cooldown` |
| `use_animation`, `use_modifiers` | `minecraft:consumable` fields plus a behaviour hook |
| `digger` | `minecraft:tool`; Bedrock's per-block destroy speeds do not map cleanly to Java's tool rules |
| `block_placer`, `entity_placer`, `projectile`, `shooter`, `throwable` | behaviour hooks |
| `storage_item`, `storage_weight_limit`, `storage_weight_modifier`, `bundle_interaction` | `minecraft:bundle_contents` where it fits |
| `allow_off_hand`, `hand_equipped`, `liquid_clipped`, `should_despawn`, `fire_resistant`, `can_destroy_in_creative`, `stacked_by_data`, `hover_text_color`, `interact_button`, `tags`, `record`, `compostable`, `dyeable`, `damage_absorption`, `durability_sensor`, `kinetic_weapon`, `piercing_weapon`, `swing_duration`, `swing_sounds` | to be assessed |

`TODO(SC-170)`: complete the table with a status per component and open the coverage entries.

## 3. `format_version` differences

Item definitions span `1.10` (legacy, with `description.category` and `render_offsets`) through
`1.26.30`. Mojang rewrote the shape at 1.20.50, moving all behaviour under `components`, and gates
individual components by `format_version` — `minecraft:compostable` needs ≥ 1.21.60,
`minecraft:cooldown` needs ≥ 1.20.10.

Real packs contain both shapes. `bedrock-samples` alone ships 42 files at `1.10` and 29 across the
modern versions. Normalisation is SC-110 §3's job; this document specifies the upgrade transforms.

`TODO(SC-170)`: the per-version upgrade ladder, derived from the versioned schema directories in
`bedrock-samples` (`server/item/1.20.50` … `1.26.30`).

## 4. Behaviour hooks

The carrier item overrides Java's item behaviour methods and dispatches to the IR for stacks
carrying `lepus` custom data:

| Java | Bedrock source |
|---|---|
| `use` | `minecraft:throwable`, `projectile`, `shooter`, `use_modifiers` |
| `useOn` | `minecraft:block_placer`, `entity_placer` |
| `finishUsingItem` | `minecraft:food`, `use_animation` |
| `hurtEnemy` | `minecraft:damage`, `durability_sensor` |
| `mineBlock`, `getDestroySpeed` | `minecraft:digger` |
| `inventoryTick` | `minecraft:cooldown`, custom components |
| `appendHoverText` | `minecraft:hover_text_color`, `display_name` |

`TODO(SC-170)`: whether these are overrides on the carrier `Item` class or loader events. Overrides
are simpler and version-stable; confirm both loaders permit everything needed.

## 5. Rendering

`minecraft:icon` names a key in `item_texture.json`, not a path. The atlas is translated into a Java
`items/` model definition plus a generated model, served from the virtual resource pack, and pointed
at by the `minecraft:item_model` component.

Attachables — 3D held and worn models with their own geometry, animations and render controllers —
have no Java equivalent and need the SC-180 render stack. This is the largest single piece of work
in this document.

**Attachables need their own path, and it is split by view rather than by kind.** The open question
above is answered, and the answer is not the one it assumed: first-person hand rendering is not
merely different, it is the wrong place entirely. An attachable is posed in **player space**
(SC-180 §3.4.2), so the item's own model draws it in neither view.

| view | seam |
|---|---|
| third person | a render layer on the player, as the elytra's is |
| first person | a hook on the hand render, which rebuilds player space against the camera |
| inventory, ground, item frame | the flat `minecraft:icon` sprite, as Bedrock shows there |

The item's model definition therefore **selects on the display context** and resolves to
`minecraft:empty` for **all four hand contexts** — the drawing happens beside it, not through it.
Making the whole item a special model instead removes its inventory icon, because a special renderer
draws in every context and the sprite is then never drawn at all.

Blanking only first person is the halfway state, and it is visibly wrong rather than merely
incomplete: the third-person hand goes on drawing the flat icon, which lands inside the character
the layer is drawing on the player. Bedrock shows the attachable and nothing else.

Neither loader offers one seam for both views. NeoForge has `RenderHandEvent`; Fabric API has no
first-person hook on either supported version — the class lists of both were read — so that half is
a mixin, delegating immediately to shared code as SC-230 §6 requires.

### 5.1 Where Bedrock's own account of this lives

<https://wiki.bedrock.dev/items/attachables> is the community reference and **should be consulted
before inferring an attachable's semantics from a pack's files**. It is not normative and it is not
complete, but it settles questions this project spent a day guessing at:

| | |
|---|---|
| the two views | a pack is expected to author **separate** first- and third-person animations |
| construction | *Method 1* rebuilds the player skeleton and parents to `rightItem`; *Method 2* sets `"binding": "q.item_slot_to_bone_name(context.item_slot)"` on the root |
| an undocumented engine offset | with Method 2, Minecraft applies **y −24 to bound bones**, which packs cancel by hand. The wiki says "we are unsure at this time why this happens" |

That last row is worth carrying: **Bedrock applies constants that no pack file states.** A model
that will not line up is not necessarily a parsing or composition fault.

`TODO(SC-170)`: `binding` is parsed into the unknown bag and not applied (SC-180 §3.5). Method 2
packs will not place correctly until it is, and the y −24 goes with it.

### 5.2 An attachable whose identifier is a vanilla item

An attachable is keyed by the identifier of the item it dresses, and **nothing requires that item to
come from the pack**. `minecraft:totem_of_undying` is an identifier like any other; a pack naming it
is saying how the totem is drawn on a player, and Bedrock draws it.

Nothing is registered for this and nothing may be. The vanilla item already exists, already has a
network id, and already appears in stacks that predate every pack — so the binding is keyed by the
**item's own registry name**, which is what such a stack carries, exactly as an add-on item's binding
is keyed by the logical identifier its stack carries (SC-120 §4). A stack is asked for its logical
identity first and for its registry name only when it has none, so a carrier item can never fall
through to a vanilla binding.

**Third person only, and that is measured rather than reasoned.** A probe (0005 `probe/`, v3) put an
attachable on `minecraft:stick` with an unconditional `scripts.animate`; a Bedrock client drew it in
third person and drew nothing in first. A later generation of the same probe (v8–v10) moved to a
custom item defined by its own behaviour pack, held in the same hand with the same bone structure,
and **was** drawn in first person. Vanilla-versus-custom is therefore the isolated variable, not
held-versus-worn and not the animation's conditions.

So a pack may write first-person animations for a vanilla item — the corpus's one such pack does,
four of them — and Bedrock plays none of them. This build plays none of them either. Implementing
what the pack file asks for here would be inventing behaviour its author never saw.

**The vanilla item goes on drawing itself in first person**, because something has to and Bedrock has
nothing else to draw there. Its model definition is therefore replaced with one that blanks the two
**third-person** hand contexts only — where the layer is drawing the character instead — against the
four an add-on item blanks in §5. The asymmetry is the same measurement stated twice.

That replacement is served in the **`minecraft` namespace**: `assets/minecraft/items/<path>.json`.
It is the first thing this project serves outside its own namespace, and it is a replacement rather
than an addition — which is what a Bedrock resource pack does to a vanilla item, and the mechanism
the same packs use to retexture one.

**Vanilla's own definition is wrapped, not rewritten, and that is normative.** The replacement reads
`assets/minecraft/items/<path>.json` out of the game's own jar and carries its `model` across
verbatim as the fallback, adding only the `select` that empties the two contexts. Writing a fresh
definition naming `minecraft:item/<path>` would reproduce the plain case and **silently destroy every
item that is more than one model** — a bow's `condition` over `using_item` and the `range_dispatch`
beneath it, a potion's tint, a compass's needle. An add-on may change how an item is drawn; it may
not cost the item its behaviour.

The read does **not** go through the resource manager: the generated pack sits above vanilla's, so
asking the manager while building that pack returns this project's own previous answer. It asks the
jar, through a Minecraft class so that whichever module or loader holds those assets is the one
asked.

**A definition that cannot be read is not replaced.** No override is written, the attachable still
binds and still draws, and the item's flat sprite goes on drawing inside the character — cosmetic,
and reported as `SCE-2042`. The alternative, replacing a file whose contents are unknown, trades a
visual defect for a functional one.

An attachable naming an identifier that is neither an enabled pack's item nor any registered item
resolves to nothing and reports `SCE-2041`.

## 6. Creative menu

Bedrock's `item_catalog/crafting_item_catalog.json` groups items for the creative menu. Java uses
`CreativeModeTab`. Tabs are Lepus-owned, built from the catalogue, and their **contents** are
hot-reloadable even though the tab objects are not (SC-120).

## 7. Geyser

Custom items are the best-supported case in Geyser's API (SC-210): `CustomItemDefinition` with
predicates that mirror Java's `items/` model-definition dispatch almost one to one, and
`NonVanillaCustomItemDefinition` for items with no vanilla base — which is our case, since the
carrier is `lepus:item`.

## 8. Testing contract

Per-component conformance cases; `format_version` upgrade goldens across the ladder; stack merging
tests proving two different Bedrock items never stack and two identical ones do; persistence of
custom data through containers, hoppers, shulker boxes, drops and death; placeholder behaviour when
the pack is detached; and render goldens for icons and attachables.
