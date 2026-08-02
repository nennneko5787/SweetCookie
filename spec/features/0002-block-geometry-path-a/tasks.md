# 0002 — チェックリスト

- [x] SC-150 §5 を §5.1〜§5.5 に展開し、TODO 2 件を解消
- [x] SC-240 のコード表に SC-150 (2030–2031) を追加
- [x] `core`: `BlockGeometry`（述語 + Java モデル生成）
- [x] `core`: `BlockModels.geometryOf`
- [x] `core`: `AddonIrJson` に状態ごとの `model` 投影（テクスチャは Bedrock のキー名で）
- [x] `BlockGeometryTest` 13 件
- [x] 適合ケース `block/geometry_transpile` と golden
- [x] `BoundBlocks.Appearance` / `BlockBinding.appearanceOf` / `publishResources`
- [x] `BlockPool` に `noOcclusion()`
- [x] アセット名をスロット単位に修正（下記）
- [x] カバレッジ 3 件を `partial` に
- [x] `specAll` 8/8 green、4 ノード compile、Fabric 2 ノードのテスト 15 件ずつ
- [x] 実クライアント起動: 2,012 スロット登録、`lepus:addons` パック認識、
      モデル欠落の警告なし、クラッシュなし
- [x] デモアドオン（`lepus_demo`）を run ディレクトリに配置

## 見た目そのものは検証していない

クライアントは起動して落ちないところまで確認したが、**ランタンが正しい向き・正しい UV で
描かれているかは人間が見るまで分からない。** SC-180 §10 のレンダリング golden が無いため。

とくに未確認:

- X 軸のミラー（`spec.md` 参照）。デモのテクスチャは左上に白い L 字を入れてあるので、
  ブロックを回りながら見れば分かる
- Path A の UV の上下（Bedrock の V が上からか下からか）

## この単位でやっていないこと

- **`render_method` を見ていない。** すべてが不透明レイヤーで描かれるので、
  `alpha_test` を宣言したテクスチャの透明部分が黒くなる。カバレッジの
  `minecraft:material_instances` に忠実度として書いた。**次に直すならここ。**
- `minecraft:transformation`
- Path B（回転した bone、poly_mesh、Molang の bone_visibility）
- 遮蔽と光。`noOcclusion()` の代償として立方体ブロックが光を遮らない
