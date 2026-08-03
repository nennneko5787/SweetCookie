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

## 段階 D — 名前の消費側 ✅

- [x] **表に 1 列足りなかった。** Java はブロックのアイテム形を `block.minecraft.`、それ以外を
      `item.minecraft.` で呼ぶ。**どちらかはパスから導けない**ので、生成する 2 列目を
      **鍵の全体**にした。`item.` と決め打つとブロックは 1 つも改名できない
- [x] `BedrockVanillaNames.javaLangKeyOfBedrockKey` — パックの鍵をまるごと解決する
- [x] `BlockBinding.languages` にバニラ改名の走査を追加
- [x] **これは `lepus` 名前空間に出して勝つ。** 言語は名前空間もパックも横断して 1 つに
      マージされるので、`item.minecraft.*` をこちらに書けばバニラの分を上書きできる。
      **テクスチャ側にはこの手が無い**（本物の差し替えが要る）

**触らなかったもの**: パックの行末は `ホシのトーテム\t#` で、`LangFile` には
「統合版は末尾の `#` を落とさない」と記録がある。Mojang 自身の `en_US.lang` には
タブ末尾コメントが**1 件も無い**ので、この資料では確かめも否定もできない。
**記録を推測で覆さない。** 統合版の画面 1 枚で決まる話なので、そちらを待つ。

## 段階 E — アイコンの消費側 ✅

- [x] `item_texture.json` を読む。**先頭に `//` コメントがある**ので、パーサに許可が要る
- [x] **2 通りの解決。どちらも照合であって推測ではない**:
      1. キーが言語の短い名前でもあるとき、その項目が指す Java のアイテム
      2. **配列のとき、要素のファイル名を Java のアイテム名の集合と突き合わせて完全一致だけ採る**
- [x] **配列が肝だった。** Bedrock は剣 7 本を `sword` 1 キーに並べて aux 値で選ぶので、
      主要な道具と武器は全部そこにいて、キーはどの言語項目とも一致しない。
      `diamond_sword` / `diamond_axe` はここで拾える
- [x] **惜しいものは落とす。** `gold_axe` は Java が `golden_axe` なので不一致 ⇒ 採らない。
      間違ったテクスチャは、変わらないテクスチャより悪い
- [x] **Java のアイテムのスプライトだけ。** ブロックのアイコンはモデルから描かれるので、
      `item/<x>.png` を差し替えても誰も読まない。表から外してある
- [x] `.tga` は `SCE-2043`。Bedrock は読めて Java は読めない
- [x] `BedrockVanillaTextures` ＋ `TableResource`（2 つの表で読み方が食い違わないよう共通化）
- [x] テスト 5 件。全項目が `item/` を指すことも主張している

```
262 テクスチャパス
textures/items/totem        -> item/totem_of_undying
textures/items/diamond_sword -> item/diamond_sword   （配列から）
textures/items/gold_axe     -> 無し                   （Java は golden_axe）
```

## 残り

- [ ] **実機確認**。トーテムの名前とアイコンが変わるはず
- [ ] **未対応の手当て**（ユーザー方針: あとで手動）。作業リストは
      `build/upstream/vanilla-names.unmatched.txt`。多くは Bedrock 側で同じ表示名を複数の
      短い名前が持つもの（`anvil` と `anvil.intact` など）
- [ ] ブロックのテクスチャ差し替え（モデル経由なので別の設計が要る）
- [ ] エンティティの表（`entity.*` ↔ `entity.minecraft.*`）
