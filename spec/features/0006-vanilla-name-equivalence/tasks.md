# 0006 — チェックリスト

## 段階 A — lock に codegen 用の宣言口を開ける ✅

- [x] `spec/upstream/codegen.yaml`（新設）。ターゲット名・生成先・理由・ソース
- [x] `CodegenSources`（build-logic）
- [x] `UpdateUpstreamLockTask` が coverage と codegen の両方から集める。
      `usedBy.kind` は前から `codegen` を許していたのに**一度も書かれたことが無かった**
- [x] `spec/upstream/fetch.md` に「固定する理由が 2 通りある」表を追加

**なぜ別の口が要るのか**: coverage の `upstream.source` は「Bedrock の機能識別子の一覧」として
読まれる。言語ファイルはそれではない。ダウンロードさせたいだけでシャードに繋ぐと、
`fetch.md` 自身が「無いより悪い」と警告している「散文から台帳項目を作る」形になる。

## 段階 B — ファイルを固定する ✅

- [x] `resource_pack/texts/en_US.lang`（802 KB）と `resource_pack/textures/item_texture.json`（57 KB）
- [x] **`--ref 921fafb...` でコミットを据え置いた。** 既定は `main` を解決し直すので、
      走らせるとスナップショットが動き `specUpstreamDiff` が新機能一覧で落ちる（本来の使い道）。
      今回は追加だけなので動かさない

## 段階 C — `generateBedrockConstants` ✅

`fetch.md` が挙げていたのに存在しなかったタスク。文書が実装より先に書かれていた。

- [x] `GenerateBedrockConstantsTask`。英語の表示名で突き合わせ、**両側で一意なものだけ**採る
- [x] Java の `en_us.json` は**ノードのコンパイルクラスパスの jar を探して**取る。
      パスを推測しない — Minecraft の jar の在処はローダのプラグインの都合で、
      マシンごとにもバージョンごとにも違う
- [x] 出力は Java ソースではなく**コミットされる表**
      `core/registry/src/main/resources/lepus/vanilla-names.tsv`。
      1,443 行を `Map.ofEntries` に展開するとメソッドのバイトコード上限に近づく上、差分が読めない
- [x] 手書きの上書き口 `spec/upstream/vanilla-names.manual.yaml`（生成器がマージ、手が勝つ）
- [x] 対応しなかったものは `build/upstream/vanilla-names.unmatched.txt` に作業リストとして出す
- [x] `BedrockVanillaNames`（`core/registry`）— `IdMapper` の兄弟。
      あちらはパックが**持ち込む**ものの識別子を作り、こちらは**元からあった**ものを見分ける。
      どちらも台帳に項目を持たない（Bedrock の機能ではないので）
- [x] テスト 4 件。表が届いていること・綴りが違うもの・言語キー全体・曖昧なものを断ること

### 生成結果（実データ）

```
1,443 対応 / うち 525 は綴りが違う / 手書き 0 / 未対応 401
totem       -> totem_of_undying     （違う）
wool.white  -> white_wool           （旧来の tile.<ブロック>.<変種> 綴り）
stone.stone -> stone
banner_pattern -> 無し              （Java 側に同名 9 個。選ぶのは当てはめ）
```

**525 という数字が設計を決めた。** 「だいたい同じ綴りだろう」で済ませる案は 3 分の 1 以上で外れる。

## 残り

- [ ] **段階 D — 名前の消費側**。パックの `.lang` の `item.<短い名前>.name` を
      Java の `item.minecraft.<path>` として生成パックに出す。`BedrockVanillaNames` は用意できた
- [ ] **段階 E — アイコンの消費側**。`textures/items/<x>.png` → `assets/minecraft/textures/item/<path>.png`。
      **`item_texture.json` をまだ読んでいない** — テクスチャのパスの短い名前が言語キーの
      短い名前と一致するとは限らない（`stone` の言語キーは `stone.stone`、テクスチャは
      `textures/blocks/stone.png`）。ここは別の解決が要る可能性が高い
- [ ] **未対応 401 件の手当て**（ユーザー方針: あとで手動）。作業リストは生成器が出す。
      多くは Bedrock 側で同じ表示名を複数の短い名前が持つもの（`anvil` と `anvil.intact` など）
- [ ] エンティティの表（`entity.*` ↔ `entity.minecraft.*`）。今回はやらない
