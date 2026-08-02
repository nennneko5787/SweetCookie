# 0001 — チェックリスト

- [x] `core` に `BlockBox` — `origin`/`size`、真偽値の短縮形、上限の切り詰め、部分的な指定
- [x] `BlockPhysics` に `collision` / `selection` を独立した 2 フィールドとして追加
- [x] `AddonIrJson` に状態ごとの `physics` 投影（既定値は省略、`float` は `float` として綴る）
- [x] `BlockBoxTest` — 境界 11 件
- [x] 適合ケース `block/box_resolution` と golden
- [x] `BoundShapes` / `BoundBlocks` — バインド時に `VoxelShape` へ変換して保持
- [x] `PoolBlock#getShape` / `#getCollisionShape`
- [x] `BlockPool` に `Properties.dynamicShape()`
- [x] `BlockPoolRegistrationTest` に 3 件（うち 1 件は `initCache()` を明示的に呼んで
      `dynamicShape()` がないと落ちることを確認済み）
- [x] SC-150 §4.1 を追記
- [x] カバレッジ 2 件を `partial` に、`fidelity` 付き
- [x] `docs/compatibility/**` 再生成
- [x] `./gradlew specAll` / `chiseledCompile` / 4 ノードのテスト

## やっていないこと

- **光の遮蔽はまだ立方体のまま。** `plan.md` の判断どおり、`minecraft:geometry` と一緒に動かす。
- **1.875 ブロックの上限と原点の制限は実機で観測していない。** 切り詰めで実装し、
  `BlockBox.CAP` 1 箇所を直せば済むようにしてある。
- `status` は `partial` 止まり。`fidelity` がある以上 ADR-0011 の下で `implemented` にはならない。

## 途中で分かったこと（記録）

`Properties.dynamicShape()` は当初「キャッシュを避けるための保険」として入れたが、
最初に書いたテストは **フラグがなくても通っていた**。理由は `BlockState.initCache` を呼ぶのが
`Blocks` のクラス初期化子だけで、テストは `Bootstrap.bootStrap()` の後に登録するため
キャッシュがそもそも作られないから。テスト側で `initCache()` を明示的に呼ぶことで、
実機で起こりうる状態を再現し、フラグを外すと落ちることを確認した。

つまり **今の 2 ローダーではフラグがなくても動く可能性が高い**。それでも残すのは、
登録の順序がローダー側の都合で変わりうるものであり、変わったときに出る症状が
「当たり判定だけが永久に立方体」という気づきにくいものだからである。
