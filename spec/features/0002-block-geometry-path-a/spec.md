# 0002 — `minecraft:geometry`（Path A）

対象カバレッジエントリ:

- `minecraft:geometry`
- `minecraft:unit_cube`
- `minecraft:material_instances`（テクスチャ解決の部分のみ）

対応する normative: SC-150 §5（§5.1〜§5.5 を新設）、SC-180 §3（既存のパース）。

## Bedrock 側の挙動

ブロックの `minecraft:geometry` は **リソースパック側のモデル識別子** を指す。モデルは
エンティティと同じ bone / cube 形式で、面ごとに UV と material instance を持つ。

```jsonc
"minecraft:geometry": "geometry.my_pack.lantern",
"minecraft:material_instances": {
  "*":   { "texture": "my_lantern_post" },
  "cap": { "texture": "my_lantern_cap" }
}
```

- 面が `material_instance` を書かなければ `*` を使う。
- `minecraft:geometry.full_block` と、コンポーネント自体の省略は、どちらも単なる立方体。
- テクスチャは **キー** であってパスではない。`terrain_texture.json` を 1 段挟む。

## Java 側で何が違うか

| | Bedrock | Java |
|---|---|---|
| 構造 | bone の階層 + cube | フラットな `elements[]` |
| 位置 | `origin` + `size`、X/Z はブロック中心から | `from` / `to`、角から |
| UV | テクセル単位、モデル自身の `texture_width` で割る | テクスチャ全体を 0〜16 に正規化 |
| 回転 | bone・cube ともに任意の 3 軸 | element は **1 軸・±22.5/±45 のみ** |
| `inflate` / `mirror` | あり | なし |

**回転がすべてゼロなら bone 階層は無視できる。** Bedrock の cube の `origin` はモデル空間の
絶対座標であり、bone の pivot は回転のためだけに存在するため。ここが Path A が成立する理由で、
SC-180 §3.2 が「親が見つからない bone がありうる」と書いている問題を回避できるのもこれによる。

## 観測していないこと

**X 軸のミラーリング — 観測済み。反転する。**

当初ここには「ブロックについては確認していない」と書き、`collision_box` と同じ `+8` だけを
適用していた。**それは間違いだった。** 統合版と Java 版で同じブロック（非対称なトロフィー）を
並べたところ、ホットバーのアイコン・手持ち・設置の 3 つすべてが左右反転していた。
アイコンは回転ゼロの状態を描いているので、回転処理では説明がつかず基礎変換に絞られた。

規範文書（SC-110 §6.1、SC-180 §3.4）は最初から「Bedrock と Java は handedness が異なる」と
書いていた。**実装がそれを読んでいなかった**だけで、未知の事実ではなかった。

当時の論拠「パック作者は当たり判定とモデルを 1 つの座標系で書く」は正しく、結論だけが誤り。
正しい帰結は「両方に同じ**ミラー**を適用する」であって「両方にミラーを適用しない」ではない。

見つかるまで時間がかかった理由も記録しておく: **corpus の座標アサーションが 1 件を除き
すべて X 対称な箱**で、east と west を区別するテストが 1 つも無かった。だから何も落ちなかった。
現在は非対称な箱と、6 面に別々の UV を与えた立方体の 2 件がその穴を塞いでいる。
