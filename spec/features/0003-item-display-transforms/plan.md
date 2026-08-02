# 0003 — 計画

## どこに置くか

**すべて `core` に入る。** ランタイム側の変更はゼロ:

- ブロックのアイテム形は `items/<slot>.json`（アイテムモデル定義）が
  `lepus:block/<base>` を指し、それは transpile 済みのブロックモデルそのもの
  （`BlockBinding.publishResources`）
- そのモデルに `display` が入れば、ホットバー・手持ち・額縁すべてがそれを使う

つまり `BlockGeometry.modelJson` が 1 ブロック多く出力するだけで届く。

## 変更

| ファイル | 変更 |
|---|---|
| `ir/geometry/ItemDisplay.java` | 新規。3 ベクトルの record と 8 コンテキストの一覧 |
| `ir/geometry/GeometryIr.java` | `Map<String, ItemDisplay> itemDisplay` を追加 |
| `ir/geometry/GeometryFiles.java` | モデル直下から読む。両ファミリー。未知のコンテキストは `SCE-1035` で落とす |
| `ir/block/BlockGeometry.java` | `display` を出力。角度は既存の `javaAngle`、translation は Z 反転のみ |
| `ir/AddonIrJson.java` | `itemDisplay` を投影（空なら出さない = 既存 golden 不変） |

## 判断したこと

**未知のコンテキスト名は落として報告する。** Java のモデルローダーは知らない
コンテキストを黙って無視するので、素通しするとファイル上でもゲーム上でも見えない誤字になる。

**`scale` の既定値は `ONE`。** 他の 2 つと同じく `ZERO` にすると、`scale` を書かなかった
モデルが点に潰れて**アイテムが見えなくなる**。省略されたフィールドが原因の不可視は
最悪の部類なので、ここだけ既定値が違う。

**角度の変換は `javaAngle` を再利用する。** モデル自身の回転と同じ 2 つの補正がかかる。
別に書くと、0002 で pivot が古いミラーのまま取り残されたのと同じ事故になる。

**書かれていないコンテキストは出力しない。** 恒等変換を書くと `block/block` の答えを
潰してしまい、手持ちが今度は壊れる。

## テスト

- `GeometryFilesTest`: パース 2 件（既定値の違いと、未知コンテキストの診断）
- `BlockGeometryTest`: 変換 2 件（3 フィールドの符号と、書かれていないコンテキスト）
- 適合ケース `block/item_display_transforms`: パースと transpile の両方を 1 ケースで。
  非対称な数値だけを使い、符号を 1 つ間違えれば必ず落ちるようにする
