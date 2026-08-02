# 0004 — チェックリスト

- [x] `core`: `ItemProfile`（判断の側。Minecraft を知らない）
- [x] `ItemProfileTest` 6 件。入力は実パックのヘイローをそのまま
- [x] ランタイム: `AddonItem.apply` がデータコンポーネントを書く（転記だけ、判断なし）
- [x] `BoundItems.Bound` に profile、`BlockBinding` で束縛時に解決
- [x] `AddonIrJson` にアイテムの投影（`components` と `profile`）
- [x] 適合ケース `item/components` と golden
- [x] カバレッジ 6 件を `partial` に。`minecraft:foil` は新設
- [x] 4 ノードのコンパイル（`Equippable` / `Enchantable` / `ItemAttributeModifiers` は
      1.21.11 と 26.2 で完全に同一。バージョン分割は不要だった）
- [x] `specAll` green
- [ ] **実クライアントで確認**（ヘイローが装備できること、耐久バー、光り方）

## 途中で見つけて直した 2 件

**modern 形式のアイテムがクリエイティブメニューに出ていなかった。** `inCreative` が
`register_to_creative_menu` だけを見ていた。それは `1.10` 系のフラグで、modern 形式は
**`menu_category` を書くこと自体が登録の意思表示**。実パックの 62 個（ヘイロー全部）が
「メニューに出ない」状態で束縛されていた。明示的な boolean はどちらの形式でも優先する。

**`Map.copyOf` は反復順が JVM 実行ごとにランダム。** `ItemDefIr` がこれを使っていたため、
golden の `components` 配列が生成のたびに並び替わった。適合ケースを 2 回続けて走らせて
初めて出た。`GeometryIr.itemDisplay`（0003 で自分が入れた分）も同じ形だったので一緒に直した。

## この単位でやっていないこと

- **`display_name`。** 読むだけで効果が無いので、この単位からは外した（カバレッジも `stub` のまま）。
  現状の lang 経路は `item.<id>.name` を見ており、`display_name.value` が別のキーを指す
  パックだけが取りこぼされる。ヘイローは既定キーと同じなので今は困らない
- **`damage_chance`。** Java に確率的な消耗が無い。忠実度メモに書いた
- **`enchantable.slot`。** Java はアイテムタグで決めるが、キャリアアイテムはどのタグにも
  入らない設計（SC-120 §4）。汎用の付与候補になる
- **振る舞いフック全部** — `entity_placer` / `projectile`（13 件ずつ）はエンティティが要る
- **防具の見た目。** 装備はできるが、プレイヤーには何も描かれない。attachable が要る
