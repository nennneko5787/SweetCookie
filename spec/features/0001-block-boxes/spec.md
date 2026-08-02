# 0001 — `minecraft:collision_box` / `minecraft:selection_box`

対象カバレッジエントリ:

- `spec/coverage/block-components.yaml` → `minecraft:collision_box`
- `spec/coverage/block-components.yaml` → `minecraft:selection_box`

対応する normative: SC-150 §4。

## Bedrock 側の挙動

`minecraft:collision_box` は「そのブロックにぶつかる箱」、`minecraft:selection_box` は「カーソルを
合わせたときに枠が出る箱」。どちらも **同じ形** を取る。

```jsonc
"minecraft:collision_box": {
  "origin": [-8, 0, -8],   // ブロック空間の左下手前の角
  "size":   [16, 16, 16]   // そこからの大きさ
}
```

- `origin` はブロック中心を X/Z の 0、ブロック底面を Y の 0 とした 1/16 ブロック単位。
  つまり完全な立方体は `origin [-8, 0, -8]`, `size [16, 16, 16]`。
- 真偽値の短縮形がある。`false` は「その箱を持たない」、`true` は「既定の立方体」。
  実在のパックが植物や当たり判定のない装飾で使うのは `false` のほう。
- 省略時の既定は完全な立方体。

### 制限

Mojang のドキュメントが述べる制限は 2 つ:

1. `origin` はブロックの内側から始まらなければならない（X/Z は `-8 … 8`、Y は `0 … 16`）。
2. 箱は 1.875 ブロック（30/16 単位）を超えられない。

**この 2 つは観測で裏を取っていない。** カバレッジエントリの注記が出典で、Bedrock の実機で
`size: [64, 64, 64]` を書いたときに何が起きるか（切り詰められるのか、ブロックごと拒否されるのか）
は確認していない。ここでは **切り詰める** ほうを採る — 拒否は憲章ルール 5 が禁じる結果になるため。
実機で観測できたときに直す箇所は `BlockBox.CAP` ひとつ。

### ドキュメントが触れていないこと

- `size` が負のとき。負の大きさは箱ではないので 0 として扱い、空の箱にする。
- `origin` と `size` の片方だけがある場合。それぞれ独立に既定値へ落とす。
- パーミュテーションで箱が上書きされる場合。これは SC-150 §3 の一般則どおりで、
  コンポーネントキー単位で後勝ち。既存の `block/permutation_resolution` が
  `minecraft:collision_box` をまさにその例に使っている。

## Java 側で何が違うか

Java の `VoxelShape` は **箱の和集合** で、Bedrock は単一の AABB。つまり Bedrock で書けるものは
すべて Java で書ける（広いほうから狭いほうへの写像）。逆は書けないが、Bedrock 側に表現がない以上
問題にならない。

Java の `getShape` が選択枠、`getCollisionShape` が当たり判定。既定では後者が前者に落ちるため、
**両方を明示的に上書きしないと `selection_box: false` が当たり判定まで消す。**
