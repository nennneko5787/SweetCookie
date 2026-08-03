# 0006 — Bedrock のバニラ名 ↔ Java のバニラ名

対応する normative: SC-120 §2（識別子空間）、SC-180（`rp-lang-font` / `rp-textures`）。

対象カバレッジエントリ: `rp-lang-font / texts/lang_files`（既存・`stub`）、
`rp-textures / textures/vanilla_replacement`（**新設**。ユーザー承認済み）。
対応表そのものは Bedrock の機能ではなく我々の基盤なので、台帳の項目を持たない
（`IdMapper` と同じ扱い — あれも `SC-120` に属し、台帳には出てこない）。

## 何を作るのか

**アドオンがバニラのアイテムの名前とアイコンを差し替えられるようにする。**
0005 で「バニラのアイテムに attachable を描く」が入ったが、実物のパックが
バニラのトーテムに対してやっていることは 3 つあり、入ったのは 1 つだけだった:

| | パックが書くもの | Java 側 | 状態 |
|---|---|---|---|
| モデル | `attachables/hoshino_totem.json`（`minecraft:totem_of_undying`） | — | 0005 で完了 |
| 名前 | `texts/ja_JP.lang` に `item.totem.name=ホシのトーテム` | `item.minecraft.totem_of_undying` | **これ** |
| アイコン | `textures/items/totem.png` | `item/totem_of_undying.png` | **これ** |

## 壁は上書きの仕組みではない

上書きの経路は 0005 で作った — 生成パックは `minecraft` 名前空間に書ける。

**足りないのは「Bedrock の短い名前 `totem` が指すものは何か」を知る手段。**
`item.totem.name` も `textures/items/totem.png` も、Bedrock がバニラの中で使っている
内部名で書かれていて、Java の `totem_of_undying` とは綴りが違う。

そして**アイテムの識別子そのものは一致している**（`minecraft:totem_of_undying`。
0005 で `BuiltInRegistries.ITEM.containsKey` が答えたので実測済み）。
ずれているのは**言語キーとテクスチャパスだけ**。

## 突き合わせの鍵は英語の表示名

```
Bedrock  resource_pack/texts/en_US.lang        item.totem.name=Totem of Undying
Java     assets/minecraft/lang/en_us.json      item.minecraft.totem_of_undying=Totem of Undying
```

**この 1 本しか橋が無い。** Bedrock 側のバニラアイテムには JSON 定義が無く（エンジン内蔵）、
`item_texture.json` のキー `totem` とアイテム識別子を結ぶ行はどこにも書かれていない。

生成するのは **ID の対応だけ**（`totem` → `totem_of_undying`）。表示名そのものは 1 文字も
commit しない — 対応は事実であって Mojang のコンテンツではない、という線引き（憲章 10・ADR-0006 の
「生成コードだけ commit」）。

**合わなかったものは対応させない。** 同名衝突・Bedrock にしか無いもの・Java にしか無いものは
必ず出る。当てはめで埋めると、0005 で 6 つ死んだのと同じ種類の定数を作ることになる。

## ビルド側に穴が 2 つある（着手前に判明）

**① `generateBedrockConstants` が存在しない。** `spec/upstream/fetch.md` は
4 つのタスクを挙げているが、`build-logic` にあるのは 3 つで、コード生成タスクだけ無い。
文書が実装より先に書かれていた。

**② codegen 用にファイルを固定する経路が無い。** `UpdateUpstreamLockTask` は取得するファイルの
集合を**カバレッジシャードの `upstream.source` からのみ**導出する。`LockedFile.usedBy` の
`kind` は `codegen` / `corpus` を想定しているのに、タスクは `"coverage"` しか書かない。

今回要る 2 ファイルは `resource_pack/**` にあり、どのカバレッジシャードの upstream でもない
（feature の一覧ではないので、そこに繋ぐと `specUpstreamDiff` が散文から項目を作る —
`fetch.md` が「無いより悪い」と警告している形）。**宣言の場所を別に作る。**

## スナップショットは動かさない

`updateUpstreamLock` は既定で `main` を解決し直すので、走らせると**固定コミットが動き**、
全ファイルのハッシュが変わり、`specUpstreamDiff` が新機能の一覧で落ちる（それが本来の使い道）。

今回はファイルを足したいだけなので **`--ref <いまの SHA>`** で走らせる。
`921fafb05c93abeae56c2f2868cd8f942bdbcc0f` のまま、追加分だけが lock に増える。

## 段階

- **A**: lock に codegen 用の宣言口を開ける（`spec/upstream/codegen.yaml`）。
  `UpdateUpstreamLockTask` が coverage と codegen の両方から集める
- **B**: `--ref` 据え置きで 2 ファイルを固定（要ネットワーク）
- **C**: `generateBedrockConstants` — Bedrock の `en_US.lang` と Java の `en_us.json` を
  表示名で突き合わせ、`core/registry` に対応表を生成する。
  **Java 側の lang は Stonecutter ノードから取る** — loom が解決済みの `minecraft-client.jar` を
  Gradle に訊く。パスを推測しない
- **D**: 消費側 1 — 名前。パックの `.lang` の `item.<短い名前>.name` を Java の
  `item.minecraft.<path>` に写す
- **E**: 消費側 2 — アイコン。パックの `textures/items/<短い名前>.png` を Java の
  `assets/minecraft/textures/item/<path>.png` に載せる

## 計測した（段階 B のあと・実データ）

固定コミット `921fafb` の `resource_pack/texts/en_US.lang` と、26.2 の
`assets/minecraft/lang/en_us.json` を突き合わせた実測:

| | |
|---|---|
| Bedrock 側の名前（`item.*` ＋ `tile.*`） | **1,848** |
| Java 側（`item.minecraft.*` ＋ `block.minecraft.*`） | **2,637** |
| **両側で表示名が一意 ＝ 安全に 1:1 と言えるもの** | **1,494** |
| うち**短い名前と Java のパスが違う**もの | **564** |
| 対応させないもの | 354（曖昧 ＋ 片側にしか無い） |

**「だいたい同じ綴りだろう」は 564 件で外れる。** 恒等写像で済ませる設計は最初から成立しない。

**曖昧なものは対応させない。** 例: Bedrock の `banner_pattern` は 1 つだが、Java には
`creeper_banner_pattern` 以下 9 個が同じ表示名 `Banner Pattern` を持つ。ここで 1 つ選ぶのは
当てはめなので、**まとめて落とす**。落ちたアイテムはバニラの名前とテクスチャのまま — 安全な側。

対応した例（検算）: `totem` → `totem_of_undying`（違う）、`acacia_door` → `acacia_door`
（同じ。`tile.*` ↔ `block.minecraft.*` の経路で拾えている）。

**ブロックのアイテム形も同じ表で拾えることが分かった** — Bedrock の `tile.` と Java の
`block.minecraft.` を同じ突き合わせに入れるだけでよく、別の機構は要らなかった。

## まだ確かめていないこと

- **Bedrock の `en_US.lang` の書式**。`key=value\t#comment` の行があり（実パックの
  `ja_JP.lang` がそう）、素朴な `split('=')` では落ちる。**バニラ側にもある**ので生成器で必須
- `item_texture.json` の側はまだ読んでいない。テクスチャのパスがキーから素直に出るのか、
  1 キーが複数テクスチャを持つ（`variants`）のかは未確認
- **エンティティ**には別の表が要る（`entity.*` ↔ `entity.minecraft.*`）。今回はやらない
