# SweetCookie

**Run Minecraft Bedrock Edition Add-Ons on Minecraft Java Edition.**

SweetCookie loads `.mcaddon` / `.mcpack` files — behavior packs *and* resource packs — and executes
them inside Java Edition: custom blocks, items, entities with their component/AI-goal definitions,
Bedrock skeletal models and animations, Molang, Snowstorm particles, loot tables, recipes and
spawn rules.

| | |
|---|---|
| Loaders | Fabric, NeoForge |
| Minecraft | 26.2, 1.21.11 *(more to follow)* |
| Side | **Required on both client and server** |
| Status | **Pre-alpha — specification and scaffolding phase. Not usable yet.** |

## Why

Bedrock's add-on system is genuinely more expressive than Java's data packs in several areas:
data-driven entities with a 120-component / 171-AI-goal vocabulary, a real skeletal animation
system with a state machine on top, Molang, and the Snowstorm particle engine.

And on a [Geyser](https://geysermc.org/) server, Bedrock players are already connecting — so
running the add-on they were built for is the natural thing to do. **Geyser deliberately does not
support behavior packs**, because executing them requires changes on the Java server, which a proxy
cannot make. SweetCookie is the Java server side that Geyser is missing.

## Design highlights

- **Format parsing has no Minecraft dependency.** `core/` compiles without `net.minecraft.*` on the
  classpath, so ~40k lines of add-on parsing are unit-testable in seconds and shared verbatim across
  every Minecraft version.
- **Identifiers are derived, never allocated.** `wizardry:magic_wand` always becomes
  `sweetcookie:wizardry.magic_wand`. No allocation table, no ID negotiation, no split-brain between
  client and server.
- **Packs attach and detach at runtime, per world — like they do on Bedrock.** No Bedrock feature
  ever gets a Java registry entry of its own: items live entirely in a data component, entities in
  NBT, and blocks bind to a pre-reserved pool of anonymous slots whose assignment is persisted per
  world. Enable, disable, reorder or update an add-on mid-game and it takes effect at the next tick.
  The only thing that needs a restart is enlarging the block pool, and it tells you the exact number.
- **Detaching a pack does not destroy your world.** Blocks keep their slot and their state, item
  stacks keep their NBT, entities go inert — all of it clearly marked as "needs add-on X" and
  restored losslessly when the pack comes back. Changing a block's state list remaps existing blocks
  instead of scrambling them.
- **Custom content never occupies a vanilla network registry ID.** On the wire it travels as a
  vanilla carrier plus a name-based sideband, which makes it work identically through
  ViaVersion / ViaBackwards as it does natively.

See [`spec/`](spec/) for the normative specification and
[`docs/compatibility/`](docs/compatibility/) for the generated feature-coverage table.

## Compatibility

SweetCookie implements someone else's specification, so "what works" is tracked explicitly rather
than claimed. Every Bedrock feature ID has an entry in [`spec/coverage/`](spec/coverage/) with a
status, the implementing class, a fidelity note and a link to its conformance test — and CI fails if
any of those links are dishonest.

## Interoperability

- **Geyser** — SweetCookie registers translated content through the `geyser-api` custom item / block
  / entity events and serves the add-on's own resource pack half to Bedrock clients unmodified.
- **ViaVersion / ViaBackwards** — supported as a first-class case, not an afterthought. A client
  running SweetCookie behaves identically whether or not its Minecraft version matches the server's.

## Licensing and attribution

**MIT** — see [LICENSE](LICENSE).

SweetCookie ships no Mojang content. Bedrock schema metadata is fetched at build time and used only
to generate code; it is never redistributed. Third-party attributions and the read-only-reference
policy for GPL sources are in [NOTICE](NOTICE); the reasoning is
[ADR-0006](spec/adr/0006-licensing-and-attribution.md).

Minecraft is a trademark of Mojang Synergies AB. This project is not affiliated with Mojang or
Microsoft.

---

## 日本語

Minecraft 統合版のアドオン（`.mcaddon` / `.mcpack`）を Java 版で動かす MOD です。ビヘイビアパックと
リソースパックの両方を読み込み、カスタムブロック・アイテム・エンティティ（コンポーネントと AI ゴール
定義込み）・Bedrock のスケルタルモデルとアニメーション・Molang・Snowstorm パーティクル・ルート
テーブル・レシピ・湧き条件を Java 版の中で実行します。

Geyser はビヘイビアパックを原理的にサポートしません（実行には Java サーバー側の改変が必要で、
プロキシ構成では不可能だから）。SweetCookie はその欠けている「Java サーバー側」です。

**現在はプレアルファ（仕様策定と足場作りの段階）で、まだ動きません。**

詳細は [README.ja.md](README.ja.md) を参照してください。
