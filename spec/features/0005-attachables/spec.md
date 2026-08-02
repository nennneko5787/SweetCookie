# 0005 — attachable（手に持つ 3D モデル）、アニメーション込み

対象カバレッジエントリ（`spec/coverage/rp-attachables.yaml` の 9 件と
`spec/coverage/rp-animations.yaml` の 22 件、`molang-*` の一部）。

対応する normative: SC-170 §5、SC-180 §4・§5・§6、SC-130。

## 何を作るのか

「肩車シロコ」を手に持つと、一人称でも三人称でもキャラクターが**動いた状態で**見える。
Java にこれに当たる仕組みは無い。**SC-180 §1 が「実質もう 1 つのレンダラで、クライアント側
最大の作業」と書いているもの**に正面から入る単位。

## 実物（`kivotos:shiroko_onbu`）

```jsonc
"minecraft:attachable": {
  "description": {
    "identifier": "kivotos:shiroko_onbu",
    "materials": { "default": "enderman" },
    "textures":  { "default": "textures/abydos/sunaookami_shiroko" },
    "geometry":  { "default": "geometry.shiroko_onbu" },
    "animations": {
      "default_controller": "controller.animation.elytra.default",
      "sleeping": "animation.shiroko_onbu.sleeping",
      "hoshino":  "animation.shiroko_onbu.idle",
      "main_hand":"animation.shiroko_onbu.hand"
    },
    "scripts": {
      "animate": [ {"main_hand": "v.main_hand && c.is_first_person"}, "hoshino", "default_controller" ],
      "pre_animation": [ "v.main_hand = c.item_slot == 'main_hand';" ]
    },
    "render_controllers": [ "controller.render.blue_archive" ],
    "particle_effects": { "barrel": "kivotos:ar_particle" }
  }
}
```

ジオメトリは 30 以上のボーンが階層をなし、**回転が 22.5° / −17.5° / 35° / −45° / 5° / 0.5°**
と自由角度。ブロックで使った Path A（Java のモデル JSON に変換）は使えない —
Java の `elements` は 1 軸・±22.5/±45 しか表現できない。**Path B、つまり自前で頂点を出す**しかない。

## 既にあるもの / 無いもの

| | 状態 |
|---|---|
| Molang エンジン | **ある。** `core/molang` にパーサとクロージャ生成、math、スコープ付きコンテキスト。カバレッジが `stub` なのは実行時に誰も評価していないからで、言語自体は書かなくてよい |
| ジオメトリの IR | **ある。** `GeometryIr` がボーン階層・自由回転・per-face UV を保持している |
| 立方体のサブミット | **試作がある。** `BedrockCubeSubmitter` が 4 ノードでコンパイルでき、`submitCustomGeometry` の署名が両バージョンで同一なことを確認済み |
| attachable の IR | 無い |
| アニメーションの IR と評価 | 無い |
| アニメーションコントローラ | 無い |
| ボーン行列の合成 | 無い |
| 手に持ったときに呼ばれる場所 | 無い |

## 段階

**1 段階ずつ動くものを出す。** 途中で止めても前の段階は使える形にする。

| 段階 | 入るもの | 見えるようになること |
|---|---|---|
| **A** | attachable の IR、ジオメトリ解決、ボーン行列（静止）、三人称で描く | 持つとモデルが出る。**動かない** |
| **B** | 一人称 | 自分の手にも出る |
| **C** | `animations/*.animation.json` の解析と再生（キーフレーム、補間、ループ） | 動く |
| **D** | `scripts.animate` と animation controller、Molang 条件 | 一人称と三人称で違うアニメーションになる |
| **E** | render controller | パックがフレームごとにジオメトリ/テクスチャを選べる |

パーティクル（`particle_effects`）はこの単位に入れない。SC-180 §7 の Snowstorm が要る。

## 観測していないこと

**巻き順と UV の向き。** `BedrockCubeSubmitter` の docstring が自分で書いているとおり、
コンパイラでは決まらず、描画された画像でしか分からない。段階 A の最初の実行で人間が見る。

**材質名 `enderman`。** Bedrock の material 名であって Java の RenderType ではない。
半透明・カットアウト・両面のどれに対応させるかは実物を見て決める。
