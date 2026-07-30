# SC-170 — Items

**Status:** outline · **Since:** 0.1.0 · **Supersedes:** —

Bedrock's 44 item components, mapped onto Java data components and SweetCookie behaviour hooks.
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
carrying `sweetcookie` custom data:

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

`TODO(SC-170)`: whether attachables render through the same path as entities or need their own,
given first-person hand rendering differs substantially.

## 6. Creative menu

Bedrock's `item_catalog/crafting_item_catalog.json` groups items for the creative menu. Java uses
`CreativeModeTab`. Tabs are SweetCookie-owned, built from the catalogue, and their **contents** are
hot-reloadable even though the tab objects are not (SC-120).

## 7. Geyser

Custom items are the best-supported case in Geyser's API (SC-210): `CustomItemDefinition` with
predicates that mirror Java's `items/` model-definition dispatch almost one to one, and
`NonVanillaCustomItemDefinition` for items with no vanilla base — which is our case, since the
carrier is `sweetcookie:item`.

## 8. Testing contract

Per-component conformance cases; `format_version` upgrade goldens across the ladder; stack merging
tests proving two different Bedrock items never stack and two identical ones do; persistence of
custom data through containers, hoppers, shulker boxes, drops and death; placeholder behaviour when
the pack is detached; and render goldens for icons and attachables.
