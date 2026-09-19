# Changelog

All notable changes to Minted are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Entries are added
after each milestone passes review.

## [0.21.0] - 2026-09-18 - Bank tellers (self-built, no Citizens)

### Added

- **Bank tellers**: place a fake player in your world that, when right-clicked,
  opens the player's own bank menu - fully self-built on ProtocolLib, no
  Citizens, no hidden real entity. Works on every supported Minecraft version
  from 1.8 to 1.26 from a single jar.
- **`/minted npc`** (admin): `create <name> [skinPlayer]`, `remove <name>`,
  `here <name>` (move a teller to where you stand) and `list`. Teller
  definitions are persisted in `npcs.yml` inside the plugin folder.
- **Good skins, never Steve/Alex**: tellers can take a real player's Mojang
  skin (signed, so it always renders) or fall back to a built-in banker skin.
  Tellers stay in the tab list for a few seconds so the client can download the
  skin, then are hidden - which is also why the fake player never flashes.
- Tellers follow nearby players with their head and vanish from a player's
  view the moment they change world or leave.
- `integrations.npcs.enabled` and `integrations.npcs.tab-hide-seconds` config
  options.

### Notes

- The teller feature is entirely optional and degrades cleanly: without
  ProtocolLib installed, `integrations.npcs.enabled` off, or a hook failure,
  Minted runs exactly as before and `/minted report` explains the state.
- ProtocolLib is only a soft dependency; Minted compiles against it but never
  bundles it.

## [0.20.0] - 2026-09-18 - EssentialsX interop & status report

### Added

- **EssentialsX awareness**: Minted detects a live EssentialsX economy at
  startup. When both economies are registered with Vault, Minted wins by
  priority, and Minted logs one guided message telling the admin how to disable
  Essentials' built-in economy and run it entirely on Minted.
- **`/minted report`** (admin): live status of every integration - API/events,
  Vault provider resolution, PlaceholderAPI expansion and Essentials.
- `integrations.essentials.warn` config option to silence the startup message.

### Notes

- EssentialsX needs no per-plugin hook: its `/bal`, `/pay` and `/eco` run on
  the Vault economy provider Minted registers, so they follow Minted balances
  automatically once its built-in economy is disabled.

## [0.19.0] - 2026-09-18 - PlaceholderAPI expansion

### Added

- **`%minted_*%` PlaceholderAPI placeholders**: `balance` /
  `balance_formatted`, `wallet` / `wallet_formatted`, `bank` / `bank_formatted`,
  `currency_name`, `currency_singular`, and the server totals `banked`,
  `burned` and `accounts`. Works in chat, scoreboards, TAB and holograms.
- `integrations.placeholders.register` config option; the expansion is
  registered with `persist()` so `/papi reload` keeps it, and a placeholder
  that errors degrades to nothing instead of breaking its line.

## [0.18.0] - 2026-09-18 - Vault economy provider

### Added

- **Vault economy provider**: with Vault installed, Minted registers itself so
  every Vault-aware plugin (EssentialsX, shops, Towny, mcMMO, Jobs, ...) reads
  and moves money through Minted. Balances route through the public API, so
  Vault and Minted always agree.
- `integrations.vault.register` config option, and `softdepend` on Vault,
  PlaceholderAPI and Essentials in `plugin.yml`.
- Vault named bank accounts return a clean `NOT_IMPLEMENTED` (Minted banks are
  per-player, not shared named accounts).

### Notes

- VaultAPI is a `provided` dependency and is never shaded; the hook is fully
  runtime-guarded and a failure only disables the Vault integration.

## [0.17.0] - 2026-09-18 - Public API & balance events

### Added

- **Public `dev.minted.api` package**: `MintedEconomy` (read/set wallet and
  bank balances, deposits, withdrawals, formatting) registered with Bukkit's
  service manager and reachable in one line via `MintedAPI.economy()`, so any
  plugin can integrate with Minted without depending on its internals.
- **`MintedBalanceChangeEvent`**: fires after every wallet or bank change, on
  the main thread, regardless of which subsystem caused it (menus, shops,
  interest, loans or an external API call).
- `integrations.events` and `integrations.primary-balance` config options.

## [0.16.0] - 2026-09-18 - Classic Vault theme

### Changed

- **Classic Vault / Bank look**: menu borders and headings are re-tinted
  (BANK borders are now black-tinted glass, COMMUNITY borders orange) and the
  common furniture and menu tiles use textured head icons -- arrows, coins,
  wallet, pouch, crown, flame, check mark, book and no-entry disc -- instead of
  plain materials.
- Textured heads are built reflectively and wrapped in a full fallback, so any
  server that cannot resolve a skin silently shows the previous plain material
  rather than breaking a menu.

## [0.15.0] - 2026-09-18 - Bounties

### Added

- **Player bounty board** (`/bounty`, permission `minted.bounty`): post a bounty
  drawing the reward from the placer's bank, view every active target on a
  paginated board, and claim the reward on a player kill.
- **Bounty refunds**: the bounty board is interactive -- click a row you placed
  money on (or any row as an admin) to open a per-bounty refund page that pays
  the escrowed reward straight back into your bank.
- **Bounty config**: `bounty.enabled`, `bounty.min` and `bounty.max` in
  `config.yml`, fully documented with a master switch and inline comments.
- `sounds.bounty-collected` override in the `sounds:` block and `minted.bounty`
  / `minted.admin` permissions.
- `BountyRefundMenu` confirms and performs the refund, matching the rest of the
  design language.

### Changed

- `GuiContext` now carries `BountyService` so any menu can reach it.
- `BountyCommand` reuses `GuiContext` and checks the `minted.bounty` permission
  before opening the board or posting.

## [0.14.0] - 2026-09-18 - Wallet Item & Resource Pack

### Added

- **Physical wallet item** (`wallet.material`, default LEATHER): a holdable
  leather pouch that stores banknotes, shows its balance on open, and lets you
  pay shops directly from it.
- **Wallet commands and GUI**: `/wallet` gives an empty pouch; right-click opens
  the wallet menu with deposit, withdraw and empty actions.
- **Resource-pack prompt**: `resource-pack.enabled` / `resource-pack.url` /
  `resource-pack.hash` sends a server-side resource-pack download on join so
  1.14+ clients can show custom banknote textures.
- **Heads compatibility**: `Heads.skull()` resolves player-head items across
  the 1.8 -> 1.26 server range with data-panic fallbacks.
- Wallet-aware `Trade` path: shops and `/pay` route through
  `WalletService.purse()` so holding a wallet is as valid as having loose notes
  in your pocket.

## [0.13.0] - 2026-09-18 - Interest, Loans & Rankings

### Added

- **Bank interest**: configurable `bank.interest.rate` paid on every stored
  balance at a configurable `bank.interest.interval-minutes` interval. Newly
  minted money grows the economy.
- **Loans**: take a loan up to `bank.loan.max` via the Loans menu, repay in
  full from the bank, and incur a one-time `bank.loan.fee-percent` plus a
  compounding `bank.loan.late-fee-percent` after `bank.loan.term-minutes`.
- **Economy leaderboard**: the Richest Players menu reads
  `EconomyService.topBalances()` and displays the top eight bank balances with
  name resolution.
- **Server-wide sales log**: the Sales menu lists the most recent shop and
  marketplace trades via `SaleLog.recent()`, including what changed hands and
  when.

## [0.12.4] - 2026-09-18 - Economy Stats & Burned / Lost

### Added

- **`/mstats` command and Economy Menu**: total money in every bank account
  plus the amount burned out of the economy by shop purchases.
- `EconomyStats` refreshed periodically, driven by `StatsDao` and
  `LostCashListener` which tracks dropped-and-despawned notes.

### Fixed

- Banknote stacking and exact-change rounding when minting physical notes.

## [0.10.1] - 2026-09-18 - Glass Fix

### Fixed

- `Glass.pane()` crashed on 1.13+ in legacy mode due to data-value fallback
  logic. Replaced with the version-aware material resolver.

## [0.10.0] - 2026-09-16 - Two-Tier Marketplace

### Added

- **Community marketplace**: one shared player-run shop alongside the admin
  global shops. Players list real stock (deposited items), set the per-unit buy
  price and an optional buy-back price, buy from other players' listings, and
  collect their earnings as minted notes. All money moves through the existing
  wallet purses and caps. Own-listing trades and banknote listings are refused;
  stock is capped at 999 per listing. Sold-out listings vanish from browsing but
  keep their pending earnings until collected. New `Market` service and
  `Sell items` / `My sales` / listing-builder / confirmation menus.
- **Categories, search and sort**: a canonical 12-category list drives a
  category-home screen; a search button filters by name/material and a sort
  button cycles price low/high and name, shown in the header. Works in both the
  global shop and the community market.
- **Version-aware catalog**: `dev.minted.shop.catalog.Catalog` seeds the global
  shop from a wide preset list tagged with the earliest version each item
  exists, and `dev.minted.compat.MaterialLookup` resolves a stable key to the
  right `Material` for the running server (or null, skipped) - a 1.8 server never
  seeds elytra/shulker/totem, a 1.13+ one does, and an absent material never throws.
- `/eshop` opens a Global / Community choose screen; admin management is
  unchanged and reached through the Global flow.

### Changed

- Shop schema gains `shops.type` and per-item `owner`, `stock`, `buy_back` and
  `earnings`. A database from before 0.10.0 is upgraded in place: the columns are
  added with guarded `ALTER TABLE`, old rows read as `global`, and the community
  market is seeded once. Global-shop economics and `/sell` are unchanged.

## [0.9.0] - 2026-09-16 - Sound & Show

### Added

- `SoundFX` plus a version-aware `dev.minted.sound.Sounds` resolver: a distinct
  sound on each money moment - a low note when you pay, a bright pickup when you
  are paid, a pling when a money request arrives - played only to the one player
  who should hear it. Names are 1.8-style and auto-translate to the modern
  spelling on 1.13+; an unknown name is a silent no-op. New `sounds:` config
  block (master switch, volume, and a per-event name override).
- `dev.minted.gui.theme.Design`: one shared design language - fixed palette
  (gold money, green in, red out, gray hints), a standard border frame, standard
  furniture icons (arrows, page info, home, search, sort, confirm, close) and a
  single lore shape. Every menu draws from it instead of hand-picking icons.
- `dev.minted.compat.Glass`: version-safe tinted glass panes (data-valued on
  1.12.2 and below, per-colour materials on 1.13+) for menu borders and fillers.

### Changed

- Every menu (personal bank, pay/request, amount and quantity pickers, and all
  shop screens) redrawn on the theme: consistent border, palette, balance strip
  and lore. Behaviour is unchanged.

## [0.6.0] - 2026-09-16 - Cash is King

### Added

- Physical-money economy (`economy.physical`, on by default). A player's wallet
  is now the signed banknotes in their inventory: the balance is recomputed from
  what they hold every time it is read, so money that is dropped, thrown, or lost
  on death without keepInventory is gone for real. Set `economy.physical: false`
  for the classic digital wallet balance.
- `NoteInventory`: the single money-in / money-out surface for physical cash -
  read the wallet value, charge a purchase (consume notes largest-first and mint
  the change back), pay a seller in freshly minted notes, and bank notes from the
  inventory or the held stack honouring the bank cap.
- `WalletService` and `Purse`: one balance/charge/credit path shared by shops,
  `/balance`, `/pay`, requests, and the menus, so the physical/digital choice
  lives in exactly one place.
- Right-click a banknote (physical mode) banks the whole held stack to your bank;
  the bank menu Deposit button and `/bank deposit` bank every note in your
  inventory. Off-hand right-clicks no longer double-fire (reflection-based hand
  filter now covers `PlayerInteractEvent`).

### Changed

- Shops buy and sell in physical cash: selling puts minted notes in your pocket
  (overflow drops at your feet), buying charges your held notes and mints change.
  Bank-currency shops still use the digital bank silo.
- Bank deposits that would exceed the cap now bank what fits and leave the rest
  as items, reporting both amounts, instead of silently dropping the overflow.

### Fixed

- `money.denominations` containing `0` (or a negative) no longer risks a
  divide-by-zero in minting: invalid values are dropped at load with a warning.

## [0.2.0] - 2026-09-16 - Economy Core

### Added

- Async SQL storage layer (`dev.minted.backend`): `StorageProvider`,
  `SqlStorageProvider`, `AccountDao` (prepared statements only), `SqlDialect`
  for sqlite and mysql, and `HikariPool` with HikariCP 2.7.9 shaded and
  relocated to `dev.minted.libs.hikari`.
- `EconomyService` with a concurrent in-memory account cache, transfers that
  lock both accounts in UUID order (deadlock-safe), and a hard max-balance cap
  per account.
- `BankAccount` model with atomic deposit/withdraw and dirty tracking, plus
  `AccountSaveTask`: a batched periodic flush that re-flags accounts on save
  failure so nothing is silently lost.
- Account lifecycle handling: load on join, flush on quit.
- Paper banknotes: per-note serial, signed lore payload (keyed SHA-256) so
  edited or forged notes are rejected on redeem, and a cross-version
  custom-model-data hook for resource-pack servers.
- Right-click banknote redemption on the main thread with the actual balance
  write deferred to the async saver.
- `/balance [player]` and `/pay <player> <amount>` with instant feedback,
  background persistence, and online-only target resolution.
- `MoneyFormat` driven by the currency config (name, singular, symbol).
- Storage is opened off the main thread, so a slow database never stalls
  server startup; commands report a graceful "still starting up" state.

### Fixed

- Custom model data on banknotes was dead code: `Reflection.getMethod` used
  `getDeclaredMethod` (misses inherited methods) and the model-data overload
  was passed `Integer.class` instead of `int.class`. It now tries the public
  method first and falls back to the declared one.
- `/balance [player]` could throw a `NullPointerException` when called while
  an account was still loading: both the self and the shown-player paths now
  return the "account is still loading" message instead.
- Accounts never loaded in minute one: an async join could fire before the
  storage layer finished opening (`dao` was still null). The join listener now
  waits for readiness, online players are (re)loaded the moment storage opens,
  and `SqlStorageProvider` guards its DAO with an explicit exception.
- No JDBC driver was actually usable on any server: sqlite-jdbc was bundled
  but relocated, which breaks its native loader (`org.sqlite.core.NativeDB`),
  and mysql-connector-j was missing entirely. The jar now ships both drivers
  unrelocated (sqlite) and relocated to `dev.minted.libs` (mysql), and Hikari
  is told the exact driver class with the thread-context-classloader workaround
  Hikari 2.x requires on Bukkit.
- `ServerVersion.toString()` showed a wrong version string (e.g. "1.1.13 (2)"
  for 1.13.2); it now formats `major.minor.patch` correctly.

## [0.1.0] - 2026-09-16 - Foundation

### Added

- Maven build system with wrapper pinned to Maven 3.9.9 (`mvnw`, `mvnw.cmd`).
- Compilation against `spigot-api:1.8.8-R0.1-SNAPSHOT` with `<release>8</release>`,
  guaranteeing a single jar that runs on Minecraft 1.8 through 1.26.
- `plugin.yml` without `api-version` so legacy behaviour is identical on every
  supported server version; `/minted` command; `minted.use` and `minted.admin`
  permissions.
- `config.yml` skeleton: currency name/symbol, sqlite/mysql database options,
  starting balance and max-balance cap.
- `MintedPlugin` lifecycle wiring with a clean self-disable below 1.8.
- `ServerVersion` compat layer: parses the running server version and derives
  the correct NMS/craftbukkit package names from the server's own classpath.
- `Reflection` utility: cached, checked-exception-free access to NMS and
  craftbukkit classes, methods and fields.
- `/minted` command surface: version info and configuration reload with
  permission checks.
- README with the project's design rules ("Commandments") for contributors.