# 0001 — 実装計画

## どこに置くか

**箱の読み取りは `core/format` に置く。** Minecraft の型を一切使わずに済む計算であり、
`BlockPhysics` がすでに同じ場所で同じことをしている（コンポーネントを読んで型のある値にする）。
`VoxelShape` への変換だけが Minecraft 側に残る。

| 層 | 追加するもの |
|---|---|
| `core/format/ir/block` | `BlockBox` — `origin`/`size` を読み、Java のピクセル空間の箱にする |
| `core/format/ir/block` | `BlockPhysics` に `collision` と `selection` の 2 フィールド |
| `src/main/java` | `BoundBlocks.Bound` が状態インデックスごとに `VoxelShape` を 2 つ持つ |
| `src/main/java` | `PoolBlock#getShape` / `#getCollisionShape` |

## 座標

`BlockBox` は **Java のピクセル空間**（完全な立方体が `0,0,0 → 16,16,16`）で持つ。
Bedrock のブロック空間からの変換は読み取り時に 1 回だけ:

```
jx = origin.x + 8      jy = origin.y      jz = origin.z + 8
```

Bedrock 空間のまま持って Minecraft 側で足す案もあったが、それは変換を毎回の呼び出し側に配ることに
なる。`Block.box(...)` がピクセル単位を取るので、この形なら Minecraft 側は数値をそのまま渡す。

## いつ解決するか

**バインド時。** SC-150 §1 が要求するとおりで、`BlockPhysics` がすでにそうしている。
`VoxelShape` への変換もバインド時に済ませ、`Bound` が状態インデックスごとの
`VoxelShape` を保持する — 当たり判定はホットパスであり、1 tick に何度も呼ばれるところで
箱を組み立て直す理由がない。

## 忠実度の穴（先に決める）

1. **遮蔽（occlusion）は立方体のまま。** 当たり判定が小さいブロックでも、光は完全な立方体として
   遮る。いま描画は全ブロックが立方体なので **見た目とは一致している**。ジオメトリ
   （`minecraft:geometry`）が入るまで動かさない。動かすとブロックが縮んだのに影が立方体、という
   逆にわかりにくい状態になる。
2. **1.875 ブロックの上限は観測していない。** `spec.md` のとおり。
3. **`size` が 16 を超える箱は隣のチャンクにまたがる。** Java は 1 ブロックの `VoxelShape` が
   自分のブロック境界を越えることを許すが、レイキャストと衝突はブロックを跨いで探索しないため
   端で判定が抜ける。Bedrock 側も同じ制限を持つ（それが 1.875 の上限の理由）ので、
   ここは差にならないと判断する。

`fidelity` 注記は 1 のみ。2 と 3 は Bedrock 側にも同じ制限があるか未観測であり、
`fidelity` は「Java 側が Bedrock と違う」ことの記録なので、書くのは 1 だけ。
→ したがって **`status: implemented` にはならない。`partial` で止める。**

## 適合ケース

`spec/conformance/block/box_resolution/`。IR の投影に箱の数値が出ていないと golden が
「コンポーネントキーがある」以上のことを証明しない — `origin` の符号を間違えても
`size` の単位を取り違えても、キーの一覧は同じものが出る。

そこで `AddonIrJson` に **状態インデックスごとの解決済み物理値** を `physics` として足す。
`resolved`（キーの一覧）はそのまま残す: あれは「どのパーミュテーションが当たったか」を証明する
もので、値の投影とは別のことを言っている。既存 golden には `physics` が 1 つ増えるだけになる。

`core` 側の単体テストで境界（真偽値の短縮形、負の `size`、上限の切り詰め、部分的な
`origin`）を押さえ、適合ケースは「パックのファイルから状態ごとの箱まで通る」ことを見る。
