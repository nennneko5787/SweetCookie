# 0004 — 計画

## 境界

**判断は `core`、適用は `src/main/java`。** Java のデータコンポーネントは `net.minecraft.*` なので
`core` には置けない（規約: `core/**` に `net.minecraft.*` を書かない）。そこで:

```
core:  Map<BedrockId, JsonValue>  ->  ItemProfile        （Minecraft を知らない値の束）
main:  ItemProfile                ->  DataComponents 群  （薄い。判断を含まない）
```

こうすると、間違えうる部分（どのキーを読むか、既定値、単位、Bedrock の綴りの揺れ）が全部
`core` のミリ秒テストに入る。ブロックで `BlockPhysics` / `BlockGeometry` が取った形と同じ。

## 変更

| ファイル | 変更 |
|---|---|
| `core/.../ir/item/ItemProfile.java` | 新規。record + `of(components)` + `READS` |
| `core/.../ir/item/ItemProfileTest.java` | 新規 |
| `runtime/registry/AddonItem.java` | `of(...)` が profile を受け取り、コンポーネントを書く |
| `runtime/registry/BoundItems.java` | `Bound` に profile を持たせる |
| `runtime/registry/BlockBinding.java` | 束縛時に profile を解決 |
| `core/testkit/AddonSurvey.java` | `ItemProfile.READS` を渡し、アイテム側も x を出す |

## 判断したこと

**スタックに焼き込む。** Java は `ItemStack.getMaxStackSize()` などをコンポーネントから読み、
アイテムクラスには聞かない。だから per-stack しか手段が無い。陳腐化は `spec.md` のとおり許容。

**耐久と重ね置きの衝突は重ね置きを 1 に落とす。** Java は両立できない。逆（耐久を捨てる）だと
防具が壊れなくなり、ヘイローが 62 個とも無敵になる。

**`protection` は属性修飾子で。** `minecraft:equippable` は装備できるようにするだけで防御力を
持たない。armor 属性を別に足す必要がある。

**アイコンと lang は既にある**（0002 の周辺で実装済み）ので触らない。

## テスト

- `ItemProfileTest`: 実パックの形（ヘイロー）をそのまま入力に使う。既定値、単位、
  `foil`/`glint` の両綴り、耐久と重ね置きの衝突
- 適合ケース `item/components`: パック → IR → profile の投影
- 実クライアント: ヘイローを装備できること、耐久バーが出ること、光ること
