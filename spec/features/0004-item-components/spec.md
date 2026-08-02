# 0004 — アイテムのコンポーネント（データコンポーネントに写るもの）

対象カバレッジエントリ:

- `minecraft:max_stack_size`
- `minecraft:durability`
- `minecraft:wearable`
- `minecraft:enchantable`
- `minecraft:glint` と `minecraft:foil`（後者は新設。**実パックが使っているのはこちら**）
- `minecraft:display_name`
- `minecraft:allow_off_hand`

対応する normative: SC-170 §1・§2（表を実測で埋める）、SC-120 §4。

## なぜこの順で、なぜこの 7 つか

`./gradlew --project-dir core :testkit:survey` を実インストール済みアドオン 6 本に当てた結果:

```
packs 6, blocks 4, items 106
  106  minecraft:max_stack_size
   81  minecraft:display_name / minecraft:icon
   62  minecraft:creative_category / durability / enchantable / wearable
   35  minecraft:foil
   13  minecraft:entity_placer / minecraft:projectile
    4  hand_equipped, can_destroy_in_creative
    2  food, use_animation, use_duration
```

ブロックは 4 個、アイテムは 106 個。そして **106 個のうち 62 個が `wearable`** — このアドオンの
「アイテム」と「防具」は同じ 62 個（ヘイロー）で、`durability` と `enchantable` も同じ 62 個に付く。

この単位は **1 個も振る舞いフックを含まない**。すべて Java のデータコンポーネントへの写像で
終わるものだけを取る。`entity_placer` と `projectile`（13 件ずつ）はエンティティが要るので、
この単位には入らない。

## Bedrock 側の形（実パックから採取、推測なし）

```jsonc
"minecraft:max_stack_size": 1,
"minecraft:durability":   { "max_durability": 363, "damage_chance": { "min": 1, "max": 1 } },
"minecraft:wearable":     { "slot": "slot.armor.head", "protection": 2, "dispensable": true },
"minecraft:enchantable":  { "slot": "armor_head", "value": 5 },
"minecraft:display_name": { "value": "item.kivotos:halo_white.name" },
"minecraft:foil": true
```

`display_name.value` は **翻訳キー**であってリテラルではない。パックの `texts/*.lang` に
その行がある。リテラルとして表示すると、画面に `item.kivotos:halo_white.name` と出る。

`minecraft:foil` は SC-170 §2 の表が `glint` と書いているものの旧名。**実パックが使うのは
`foil` だけ**（35 件）で、`glint` は 0 件。両方読む。

## Java 側で何が違うか

| Bedrock | Java | 注意 |
|---|---|---|
| `max_stack_size` | `minecraft:max_stack_size` | 1〜99 に収める |
| `durability.max_durability` | `minecraft:max_damage` + `minecraft:damage` 0 | **Java は「耐久あり」と「重ねられる」を両立できない**。両方書いたパックは重なりを 1 に落とす |
| `durability.damage_chance` | なし | Java に確率的な消耗はない。忠実度メモに書く |
| `wearable.slot` | `minecraft:equippable` の slot | `slot.armor.head` → `HEAD` など |
| `wearable.protection` | 防具値の属性修飾子 | |
| `enchantable.value` | `minecraft:enchantable` | Bedrock の `slot` は Java 側に対応物が無い |
| `foil` / `glint` | `minecraft:enchantment_glint_override` | |
| `display_name.value` | `minecraft:item_name`（翻訳可能テキスト） | キーはパックの lang が解決する |
| `allow_off_hand` | 無いと Java はどのアイテムもオフハンドに置ける | **既定が逆**。false のときに何ができるかは要調査 |

## 観測していないこと

**`wearable.protection` と Java の防具値の対応。** Bedrock の protection は独自の尺度で、Java の
armor 値と 1:1 ではない。まずは同値として写し、忠実度メモに書く。

**古いスタックの陳腐化。** コンポーネントはスタックに焼き込まれるので、チェストに入ったままの
スタックはパック更新前の値を保つ。SC-120 §5 が求めるのは「消えないこと」で、それは満たす。
毎 tick 書き直す方式は、コンポーネント等価性が変わってスタックが合体しなくなるため取らない。
