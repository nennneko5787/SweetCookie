# 0003 — チェックリスト

- [x] SC-180 §3.6 を新設（パース側）
- [x] SC-150 §5.6 を新設（変換側）
- [x] SC-150 §5.3 の「ミラー軸をどう決めたか」を訂正。**手持ちとアイコンの食い違いは
      ミラーの証拠ではなく、この機能が無いことの症状だった**
- [x] `core`: `ItemDisplay` / `GeometryIr` / `GeometryFiles` / `BlockGeometry` / `AddonIrJson`
- [x] `GeometryFilesTest` 2 件、`BlockGeometryTest` 2 件
- [x] 適合ケース `block/item_display_transforms` と golden
- [x] カバレッジ `geometry/item_display_transforms` を新設（`partial`）
- [x] `rp-geometry.yaml` の「全部 stub」ヘッダに例外を明記
- [x] `minecraft:geometry` の忠実度メモを更新（ミラーは X ではなく Z、回転の向きは観測済み）
- [x] `specAll` green
- [x] **実クライアントで確認済み。** ホットバーのアイコンも手持ちも統合版と一致。
      3 軸同時回転（トロフィーの `gui` は 3 軸すべて非ゼロ）が合ったので、合成順序の仮定は
      少なくともこの 1 例では正しい

## 26.2 の jar で確認したこと

- コンテキスト名 8 つは Java 側と完全一致（`ItemDisplayContext`）。26.2 は `on_shelf` を
  追加しているが Bedrock には無い
- `display` は **コンテキスト単位で親チェーンを遡る**（`ResolvedModel.findTopTransform`）。
  書かなかったコンテキストが `block/block` の答えを保つ、という前提は正しい
- 回転の適用は `Quaternionf().rotationXYZ(...)` = X → Y → Z
- `translation` は 1/16 倍したあと ±5 に、`scale` は ±4 にクランプされる。
  範囲外の値でも落ちない（規約 5）

## まだ確認していないこと

**3 軸同時回転の合成順序（Bedrock 側）。** Java 側は jar で確認済み。トロフィーの `gui` は
3 軸すべて非ゼロなので、Bedrock の順序が違えばまだ少しずれる。直すのは
`BlockGeometry.displayJson` の 1 箇所。

**左手。** Java は左手の 2 コンテキストを**自分で鏡像化する**
（`translation.x` / `rotation.y` / `rotation.z` を反転、`ItemTransform.apply`）。
モデルが `*_lefthand` を書いていても適用される。Bedrock が同じ規約かは不明なので、
パックの値は**そのまま**出している。Bedrock も同じなら正しく、違えばオフハンドだけ鏡像になる。

**`partial` から上げる条件。** ADR-0011 のとおり `implemented` は手書き + ビルド検証だが、
`fidelity` メモがある間は上げない。上のオーダー問題が観測で潰れるまでは `partial`。

## この単位でやっていないこと

- **アイテム（ブロックではない）の `item_display_transforms`。** Bedrock のアイテムは
  1 枚の絵で、モデルを持たない（`BlockBinding.items`）。attachable が入るまで置き場所がない
- Bedrock 側の既定値と `minecraft:block/block` の既定値の比較
