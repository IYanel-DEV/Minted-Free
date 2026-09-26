# Changelog

All notable changes to Minted are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Entries are added
after each milestone passes review.

## [0.65.2] - 2026-09-26 - Fix Discord webhook embed color

### Fixed
- Discord webhooks now work — embed `color` sent as a signed 32-bit int (`0xFF4CAF50` = `-11751600`) was rejected by Discord with `{"embeds": ["0"]}`. Color is now masked to 24 bits (`& 0xFFFFFF`), which Discord accepts (0–16777215).

## [0.65.0] - 2026-09-25 - Discord webhooks + Tax system

### Added
- **Discord webhooks with beautiful embeds** for all economy events (shop sales, auctions, bounties, large transfers, balance milestones, server start/stop)
- **Configurable tax system** — per-shop-type buy/sell taxes (global shop, player shop, auctions) with minimum amount threshold
- Tax amounts are burned from economy and logged in ledger
- Per-event webhook URLs with ping support for important events

### Fixed
- INK_SACK compatibility fix finalized (uses COAL/GOLD_INGOT, no MaterialLookup needed)
- Tax amounts properly burned from economy and logged in ledger

## [0.64.0] - 2026-09-25 - Persistent price sort preference

### Added
- **Price sort preference now persists per player per shop** across category changes, page navigation, menu close/reopen, and server restarts.
- When you set "Low → High" or "High → Low" in one category, it's remembered when you switch categories, go back to the home menu, or reopen the shop later.
- Saved via existing `BrowseHistory` (in-memory per session, survives `/sh` and `/psh` shortcuts).

### Fixed
- INK_SACK compatibility fix finalized (uses COAL/GOLD_INGOT, no MaterialLookup needed).

## [0.63.1] - 2026-09-25 - Fix INK_SACK compatibility

### Fixed
- **Price sort toggle NoSuchFieldError on 1.13+** — `INK_SACK` renamed to `BLACK_DYE`.
- Used `MaterialLookup.get("ink_sack")` for cross-version compatibility (1.8-1.26).

## [0.63.0] - 2026-09-25 - Single price sort toggle with highlighting

### Added
- **Single toggle button for price sort** in shop grids (slot 50).
- Cycles: Default (shop order) → Low→High → High→Low → Default.
- Visual feedback: **Yellow (Gold Ingot)** when active, **Gray (Ink Sack)** when default.
- Lore shows current mode and next mode on click; active shows "► ENABLED ◄".
- Resets to page 1 when toggling.

### Fixed
- Creative-only items removed from shops: all spawn eggs, KNOWLEDGE_BOOK, DEBUG_STICK.
- Added to `isSellable` denylist across all versions.

## [0.62.0] - 2026-09-25 - Every item of your version is for sale

### Added

- **Global shops now live in `global-shops.yml`** inside the plugin folder
  instead of the database. Items are stored as version-safe data (canonical
  material keys, optional legacy data values, plain-text name/lore/enchant
  lines), so one file stocks a 1.8 and a 1.26 server alike and is what a shop
  website would generate or download in place of base64 item blobs. Community
  and player shops keep their real stock and ownership in the database.
- On a fresh install Minted writes a default file seeded with the full catalog
  for the running server version. Existing installs upgrade in place: any
  global shop still in the database is exported to the file exactly once.
- The in-game `/eshop` editor keeps working unchanged - every global-shop edit
  now rewrites the file instead of the database, so what you change in game is
  what the file says.
- **`catalog: true`** on a shop entry makes Minted append any new preset
  catalog item on load (the old auto-grow behaviour, now opt-in). Hand-written
  or website-generated files without the flag are never touched.

### Changed

- `/eshop create`, `delete`, `edit`, `rename` and `setcurrency` operate on
  file-managed global shops only; their inventory is no longer stored as
  base64 blobs. A broken entry in the file is skipped with a warning, never
  fatal, and the damaged shop is regenerated from the catalog instead.

## [0.61.1] - 2026-09-25 - The auction house actually opens now

### Changed

- **Fixed: `/ah` reported the auction house was "still loading" forever.** The
  auction service was constructed at startup but its loader (which flips it
  ready by reading the auction tables) was never started, so every command
  answered `auction.not_ready` regardless of config or restarts. It now
  initialises when the rest of the database-backed services come up.
- **`auction.enabled` is now honoured.** Like bounties, the house is only
  built, and `/ah` only registered, when the flag is true (default true). It
  was previously ignored entirely, so the config switch did nothing.

## [0.62.0] - 2026-09-25 - Every item of your version is for sale

### Added

- **A `catalog: true` shop now sells every single item its Minecraft version
  has, not just the curated preset.** Alongside the curated entries (which keep
  the best display names, categories and prices), the plugin enumerates the
  running server's whole `Material` set on a fresh install and on every growth
  pass, and shelves anything the curated catalog missed - each with a prettified
  name, a best-guess category and that category's default price. Curated rows
  still win on name/category/price; the every-material pass only fills gaps, so
  the two never fight over the same slot. On 1.8 the default shop stocks
  roughly 380 materials; on 1.26 the same mechanism stocks the full 1000+-item
  enum, so no server ever has an unsold item it can represent.
- Technical placeholder blocks are never sold: air, water/lava states, pistons
  mid-extend, portals, fire, bedrock, barriers, spawners, command/structure
  blocks, light, frost, and the legacy glowing-ore/burning-furnace/lit-* states
  stay out of the shop.
- Growth stays idempotent and cheap: each material is added at most once, the
  self-test now asserts the fresh file sells every sellable material, and a
  second growth pass changes nothing.

## [0.61.4] - 2026-09-25 - Site exports are version-growing catalogs

### Changed

- **Website-generated files now carry `catalog: true`** so the shop they
  define is a version-growing catalog: whatever Minecraft version loads the
  file, the plugin appends every preset item that version supports and the
  shop does not yet sell, and skips what it cannot. One download now works on
  any server - a file edited on 1.8 auto-fills up to 1.26.2 when moved there,
  and stays 1.8-only on 1.8. The site keeps a `catalog: true` toggle (checked
  by default) and preserves an imported file's flag through the editor.

## [0.61.3] - 2026-09-25 - Site import reads real shop files

### Fixed

- **The shop website's ".yml" import found nothing in a real
  `global-shops.yml`.** The plugin writes SnakeYAML's "indentless list" style
  (`- name: Spawn` at column 0, shop/item fields indented, dash items at the
  parent key's column), but the site's old parser only matched its own
  fully-indented export format - so the browser reported "No shops found" for
  an otherwise valid, 500+-item file. The site now ships a small
  indentation-based YAML reader that handles both styles and both directions
  (import and download round-trip cleanly), and item materials are normalized
  to canonical keys (`lapis` -> `LAPIS_LAZULI`, `melon` -> `MELON_SLICE`,
  `totem` -> `TOTEM_OF_UNDYING`, `cooked_beef` -> `STEAK`, ...) so every
  imported item resolves to a real Minecraft icon.

### Added

- **Subtle particle-free animations across the site** - pixel-styled panel
  slide/fade-ins, slot pop and hover lifts, tab pops, button press feedback,
  picker zoom, and a toast that slides in and out. Pure CSS `steps()` timing,
  no glow or neon, so the editor still feels like Minecraft rather than a
  web app.

## [0.61.2] - 2026-09-25 - Auction creation keeps state and always banks the item

### Changed

- **Fixed: creating an auction made you pick the item twice.** Picking an item
  in the inventory selector - and entering the starting bid, buyout and
  duration afterward - rebuilt the create menu from scratch each time, which
  silently threw away the item you had just chosen and asked for it again. The
  whole flow now reuses the same menu, so the chosen item and every entered
  price stick as you move through the steps. `/ah create` also jumps straight
  to the create screen, `/ah my` opens your listings and `/ah bids` your bids,
  instead of all three dumping you in the browser.
- **Fixed: the "next page" button in the auction browser and the item picker
  never worked** - it shared an inventory slot with the close button, so close
  overwrote it whenever a second page existed.
- **Hardened: an auction can never be created while the player keeps the
  item.** The item stack is now the source of truth: the listing fee is only
  kept once the item actually leaves the seller's inventory, and if the stack
  cannot be removed the fee is refunded and no auction is registered - the
  seller never both holds the item and has it up for sale. The menu also
  stores a copy of the chosen stack instead of a live handle on inventory
  contents.

## [0.60.0] - 2026-09-25 - Remember where you left off: /sh and /psh

### Added

- **`/sh` reopens your last view in the global shop** - the category, search
  result or page of items you were looking at, instead of making you walk from
  the front door again. With nothing bookmarked (or the shop it pointed at
  gone), it opens the normal shop exactly like `/shop`.
- **`/psh` reopens the last player shop you were looking at** - the same store
  and item view, whether you reached it through `/pshop browse` or by name.
  With nothing bookmarked it opens your own storefront like a bare `/pshop`.
- Browsing anywhere in `HomeMenu` or a `GridMenu` now bookmarks that spot in
  memory, per player, and the shortcut commands re-open the exact grid state
  (page, sort and search included).

## [0.59.0] - 2026-09-24 - The pshop browser shows sellers, not emeralds

### Changed

- **Every storefront tile in the player-shop browser now shows the seller's
  own head** instead of the shop's generic icon (usually an emerald), so
  `/pshop browse` reads as a wall of players. The head is built through the
  version-safe `Heads.player` path - it works from 1.8 to 1.26 - and the tile
  keeps the owner's name in gold underneath.
- When the owner cannot be named - an administrative shop with no owner, or a
  UUID the server has never seen - the tile keeps the shop's own icon, so a
  slot is never a nameless head.

## [0.58.1] - 2026-09-24 - README tells the truth about the version

### Fixed

- **README status line caught up** - it still advertised `v0.53.0` five
  releases behind the jar it describes, so the front page of the repo
  understated what servers were actually downloading. It now reads `v0.58.1`.

## [0.58.0] - 2026-09-24 - Global opens items, Community opens players

### Changed

- **Global Shop now opens the shop's items immediately** - never a list of
  shops. Clicking Global lands in the server catalog's item grid, and only
  server shops (no owner) are eligible, so a player's storefront can never be
  reached from there. When a server really does run several global shops, the
  others stay reachable by name (`/eshop <name>`).
- **Community Shops now lists every shop a player owns**, deciding by the
  **owner** rather than only the type column, so a storefront written before
  types existed still appears there instead of hiding in Global.

### Notes

- Together with the loader guards (an owned shop is always a player shop; a
  global-typed shop whose listings name a seller is adopted by that seller),
  a player shop is now reachable in exactly one place - Community Shops - and
  Global shows only the server's own catalog.

## [0.57.0] - 2026-09-24 - Hand a shop to a player (or back to the server)

### Added

- **`/minted shops own <shop> <player|none>`** - the decisive repair. Giving a
  shop to an online player rewrites its owner and type, in memory and in
  storage, so it **leaves the global catalog immediately and appears in
  Community Shops** (no restart, no database editing). `none` hands it back to
  the server. This exists because a storefront stored before shops recorded an
  owner carries no evidence of who owns it - the type column, the owner column
  and its listings can all be empty, and only an admin can say whose it is.
- `/minted shops` now prints the exact command to fix a shop, so the output is
  self-explaining.

### Notes

- The automatic guards stay in place: a shop with an owner is always served as
  a player shop, and a global-typed shop whose listings name a seller is
  adopted by that seller on load. The command is the manual override for the
  case where nothing in the row says who owns it.

## [0.56.1] - 2026-09-24 - Ownerless storefronts are repaired on load

### Fixed

- **A player shop with no owner recorded can no longer hide in the global
  catalog.** If a storefront was opened before shops stored an owner, its row
  says `type = global` and `owner = null`, so no type check can tell it apart
  from a server shop - which is why it appeared in Global and was missing from
  the community directory. The loader now inspects the shop's **own listings**:
  a global-typed shop whose items name a seller is served as that seller's
  player shop, so it moves into the community directory by itself, with a log
  line explaining what it did. No database editing needed.
- The earlier guards remain: a shop with an owner is always a player shop
  (0.55.1), and each browser lists only its own type (0.55.0).

### Added

- **`/minted shops`** (admin): prints every loaded shop with its name, type,
  owner and item count - the fastest way to see exactly what the server thinks
  each shop is, and what to report if anything still looks wrong.

## [0.56.0] - 2026-09-24 - A two-tile shop front door

### Changed

- **`/eshop` now opens exactly two tiles**, as asked:
  - **Global Shop** - the server's own shops, and only those. If there is just
    one, it opens straight into it.
  - **Community Shops** - the player storefronts. It lists every shop a player
    runs, **by owner name**, and clicking one opens that shop showing what they
    are selling.
- The separate "Player shops" tile and the separate "Community Market" tile are
  gone from the front door, so there is one obvious way in to each kind of
  shop and the two can never be confused again. The shared community market is
  still reachable by name (`/eshop <market>`) for anyone who needs it.
- Player-shop tiles now show the **owner's name** as the item name, with the
  shop name and how many items they are selling underneath, so a directory
  reads as "who is selling what" at a glance.
- A VIP's shop remains reachable by typing `/<playername>` - the same template
  as before, now the companion to the community directory.

## [0.55.1] - 2026-09-24 - Owned shops can never be served as global

### Fixed

- **A player shop that was stored with the wrong type can no longer show up in
  the global catalog.** Shops carry a `type` column that only arrived with the
  player-shop feature, and a row written before that (or by a version that
  defaulted it to `global`) was served as a server shop even though it had an
  owner. The loader now treats **any shop that has an owner as a player
  storefront**, whatever the stored type says, so the global browser, the
  player-shop directory and the community market always disagree on nothing.
  This is on top of the 0.55.0 fix that made each browser list only its own
  shop type.

### Notes

- Existing databases need no manual repair: restart the server and the shop
  loads as the player shop it actually is.

## [0.55.0] - 2026-09-24 - Separate shop types and real bank deliveries

### Fixed

- **Player shops no longer appear in the global shop list.** The global
  browser skipped only the community market, so every player storefront was
  listed next to the server's own shops. The three shop types are now fully
  separate: **Global shops** (admin catalog), **Player shops** (their own
  directory) and the **Community Market**, and each browser only ever lists its
  own kind - including the row count used to size the menu.
- **A bank purchase is now actually delivered after the wait.** Previously
  the money was taken instantly and the item was handed over immediately, so
  the "7 second cooldown" only blocked the *next* bank purchase without the
  goods ever arriving late. A bank-funded purchase now queues a delivery: the
  player is told what is coming and when ("Your <item> arrives in 7s"), the
  goods are handed over when the wait is up, and a purchase made while the
  player is offline waits in the queue and is delivered the moment they rejoin
  - so nothing paid for can be lost. Set
  `payments.bank-cooldown-seconds: 0` for instant handover.

## [0.54.0] - 2026-09-24 - Payment sources, bank delivery cooldown, and three fixes

### Added

- **Player-chosen payment source**: `/minted source [wallet|bank|both]`
  decides where a shop purchase is paid from. `both` (the default) tries the
  wallet first and, when the wallet cannot cover the whole price, takes the
  **full amount** from the bank - never a split payment. The choice persists
  per player in `payment-sources.yml`, so it survives restarts.
- **Bank delivery cooldown** (`payments.bank-cooldown-seconds`, default 7):
  after a purchase paid from the bank, further bank-funded purchases are
  refused with the exact seconds remaining ("Your bank delivery is
  recharging - 5 more second(s)"), while wallet-funded purchases are never
  blocked. The cooldown is in-memory, so a restart never penalises a player.
- The same rule now drives **both** purchase paths (global/player shops and
  the community marketplace), so a bank purchase behaves identically
  everywhere, and the "balance" line always shows the purse that was charged.
- `payments.default` config option sets the source new players start with.

### Fixed

- **VIP granted mid-session never registered its `/<username>` command**:
  the `minted.vip` permission was only read on join and quit, so granting it
  to an online player (LuckPerms group change, or after they created their
  shop) did nothing until they reconnected. A 5-second observer now re-checks
  everyone online and re-syncs only when a player's VIP state actually
  changes.
- **Player-shop listings could duplicate items**: the listing recorded the
  number of units *requested* instead of the number that actually left the
  inventory, so if the inventory shifted in between (creative mode, moving
  items while the price prompt was open, another plugin) the shop was
  credited with goods the owner still held. Stock is now exactly what was
  taken, and a listing is refused outright when nothing was taken.
- **The shop icon could not be chosen**: the editor's icon button demanded
  the item already be in the player's hand. It now opens an inventory picker
  - click the button, click the item you want as the icon.

## [0.53.2] - 2026-09-24 - Self-test: the legacy data-value rule only where it exists

### Fixed

- The self-test asserted the legacy data-value rule (`sameStock` treating a
  differing data value on a non-durable item as different goods) on every
  server. That rule only exists for 1.8-1.12: from 1.13 on, item data lives in
  components and `setDurability` on a non-damageable item is silently
  discarded, so the assertion was wrong on 1.13+ (it reported a failure for
  behaviour the platform cannot even express). The check now probes the
  platform first and **skips with a reason** where the rule cannot apply,
  while still enforcing it on legacy servers, where it matters.
- The material/durability part of the check was split out so the version-
  independent rules (two apples match, apple never matches dirt, tool damage
  never changes identity) are asserted everywhere.

### Notes

- No production behaviour changed: `sameStock` was, and remains, correct on
  both eras. Only the test's assumption was wrong.

## [0.53.1] - 2026-09-24 - Self-test version-proofing

### Fixed

- The self-test's shop-matching check no longer references the legacy
  `Material.WOOL` constant. A compiled material constant is a direct field
  read, so on 1.13+ it threw `NoSuchFieldError` and failed the check there
  (it passed in the build, which compiles against 1.8.8). The suite now
  resolves every material it needs **by name** through the same resolution
  path as the rest of the plugin, so a name that does not exist on the
  running version skips with a readable reason instead of crashing the check.

### Added

- New live check `catalog: every category icon resolves on this server`, which
  runs the real `MaterialLookup` over every shop category and reports any
  icon key this server version cannot resolve - the same class of version
  drift the suite just proved is worth catching.

## [0.53.0] - 2026-09-24 - Self-tests (build-time and in-game)

### Added

- **One self-test catalogue, two runners** (`dev.minted.selftest`), so
  "correct" is defined once and can never drift between them:
  - `.\mvnw.cmd test` (also run automatically by `clean package`) asserts the
    money rules headlessly: amount tokens, deposit/withdraw and the cap, the
    absolute admin-set path, the remote-sync path, shop item matching, custom
    item degrade, VIP name validation, and a **two-server simulation** proving
    concurrent balance changes compose, an overdraft is refused, and a stale
    cache can never push a balance past the cap.
  - `/minted selftest` (admin) runs the same checks on the live server and
    adds what only a real server can prove: a guarded-delta round-trip against
    the configured database (the row is deleted afterwards), the public
    economy API, the loaded shop model, every command executor, the VIP list,
    detected custom-item providers and the Redis announcement channel.
    Results are printed in chat and logged to the console.
- Every check is total: it reports pass, skip (with a reason) or a short
  failure, and can never throw into the caller. The suite includes guard
  tests proving the harness can actually fail and skip.
- `StorageProvider.deleteBalance` (a small storage addition the self-test uses
  to clean up after itself) and read-only accessors for the account save state,
  used by the suite and by tooling.

### Notes

- Test scope only: JUnit 5 never ships inside the jar, and the checks live in
  the plugin so the in-game runner and the build runner cannot diverge.

## [0.52.0] - 2026-09-24 - Public documentation and onboarding

### Added

- **README rebuilt as the plugin's storefront**: every feature that shipped in
  0.47-0.51 is now documented (VIP `/username` shops, VaultUnlocked, custom
  items, multi-server), plus a quick start, a configuration highlights block,
  full **command** and **permission** tables, a **compatibility matrix**
  (1.8-1.26, SQLite/MySQL, Bungee/Velocity, optional plugins), a support
  section and the previously unused commands/permissions banner.
- **Startup summary line** in the server log: storage backend, multi-server
  state and which hooks (Vault, VaultUnlocked, PlaceholderAPI) came up - the
  same facts as `/minted report`, visible at boot.

### Notes

- Documentation and onboarding only; no gameplay or storage behaviour changed.

## [0.51.0] - 2026-09-24 - Multi-server networks

### Added

- **Multi-server mode** (`multi-server.enabled`): point every server at the
  same MySQL database and balances stay correct across BungeeCord/Velocity
  switches. Off by default - a single server behaves exactly as before.
- **Atomic delta writes**: every account remembers the balance storage is
  known to hold, and the saver writes the difference as a guarded
  `balance = balance + delta` that only fires when the result stays in
  range. Two servers changing the same player therefore *compose* instead of
  overwriting each other, and an overdraft is refused by the database itself -
  the cached value is then corrected from the authoritative row, so money can
  never be duplicated or lost. Admin `/eco set` writes stay absolute.
- **Fresh read on join**: a player arriving from another server is re-read
  from the shared database instead of this server's cache.
- **Cross-server announcements (optional)**: a dependency-free Redis pub/sub
  client (`multi-server.redis`, e.g. `redis://localhost:6379`) tells the
  other servers the moment a balance is committed, and they re-read it. With
  no Redis configured the database guarantees still hold; servers simply
  notice a change within `multi-server.refresh-seconds`.
- `multi-server.save-seconds` (default 2) bounds how long a change can sit
  unsaved, and `/minted report` shows a `Multi-server` line with the live
  state (Redis connected, connecting, or database-only).

### Notes

- The saver, quit flush and shutdown flush all go through the same delta
  path; the 60-second batch saver delegates to it while networked, so a
  shared database is never fed an absolute overwrite.
- Physical (banknote) wallets stay physical across servers - the notes
  travel with the player's inventory, which is up to the proxy's transfer.
- Long-running background writers such as bank interest still write whole
  balances; run them on one server only while networked.

## [0.50.0] - 2026-09-24 - Custom items (ItemsAdder, Nexo, Oraxen)

### Added

- **Custom item identity in every trade path**: shops, player shops, the
  community marketplace, `/sell` and sell-all now tell ItemsAdder, Nexo and
  Oraxen items apart from the vanilla material they are built on. A custom
  "Ruby Sword" (diamond-sword base) can no longer merge with, pay out as, or
  match an ordinary diamond sword - both sides of a match are resolved
  through the owning plugin's own item id
  (`CustomStack.byItemStack`, `NexoItems.idFromItem`, `OraxenItems.getIdByItem`).
- All three providers are auto-detected reflectively at first use: **no
  compile-time dependency, nothing bundled**, and if a plugin is absent (or
  its API moves) that provider is skipped and matching falls back to the
  exact pre-integration material rule. Custom item names, lore and model
  data were already stored losslessly by the shop codec, so listings render
  as the real item end to end.
- `integrations.customitems.enabled` config option (default true), the three
  plugins added to `softdepend` for deterministic load order, and a
  **Custom items** line in `/minted report` showing which providers were
  detected.

### Notes

- Detection keys off each plugin's public API and a null/empty id reads as
  "vanilla", so an unknown or newly-added item type degrades to today's
  behaviour instead of ever blocking a trade. The check runs on the main
  thread where all trade paths already live.

## [0.49.0] - 2026-09-24 - VaultUnlocked (vault2 API) support

### Added

- **VaultUnlocked support**: Minted can now register on the `vault2` economy
  API served by the VaultUnlocked plugin
  (<https://github.com/TheNewEconomy/VaultUnlockedAPI>), next to the classic
  Vault provider. Every VaultUnlocked-aware consumer reads and moves money
  through the same `MintedEconomy` balances as Vault, `/balance` and the
  menus, so all APIs always agree.
- `VaultUnlockedEconomy` implements the full vault2 surface honestly: world
  parameters collapse onto Minted's single balance, a call in a currency
  Minted does not have fails with a clear `NOT_IMPLEMENTED` message instead of
  touching the wrong pot, and shared accounts / account rename / account
  delete answer `false` rather than faking them.
- `integrations.vaultunlocked.register` config option (ships `false`, same
  opt-in stance as `integrations.vault.register`; set `true` if you run
  VaultUnlocked), `VaultUnlocked` added to `softdepend`, and a new line in
  `/minted report` showing whether Minted is registered on the vault2 API.
- Compiles against `net.milkbowl.vault:VaultUnlockedAPI:2.20` from the CodeMC
  repository, `provided` scope - never shaded.

### Notes

- The hook is class-presence-guarded: on a server without VaultUnlocked
  neither the hook nor the adapter class is ever loaded, and everything else
  keeps working exactly as before. The VaultUnlockedAPI jar also carries the
  classic `net.milkbowl.vault` API (the two hooks stay independent, so either
  side can be present alone), and both hooks register at highest priority so
  Minted wins the provider selection consistently on both APIs.

## [0.48.0] - 2026-09-24 - Permission-based VIPs

### Added

- **`minted.vip` permission**: players granted this node (LuckPerms,
  PermissionsEx, any permission plugin) now count as VIPs alongside the
  stored list, and the automatic `/<username>` shop command works for them
  exactly the same way. The node is declared in `plugin.yml` (default: false).
- The dashboard VIP page and `/minted vip list` show **both sources** in one
  roster. Permission-based VIPs are marked (`[permission]` in the list, a
  "Granted by the minted.vip permission" line in the menu) and are not
  removable from the menu - `/minted vip remove` and a menu click explain that
  the node must be revoked in the permission plugin instead.
- Removing a list entry that also has the permission prints a note that the
  player remains a VIP, and adding someone who already has the node reports
  they are already covered.
- Because Bukkit cannot read an offline player's permissions, Minted records
  the state seen on join and quit in `vips.yml` (its own `permission`
  section); restarts resume from it and it self-corrects the moment the
  player is online again.

### Notes

- A player on both sources stays a VIP until both are taken away. The
  `/<username>` command, shop lifecycle hooks and the `vip.enabled` switch
  treat both sources identically.

## [0.47.0] - 2026-09-24 - VIPs and automatic /<username> shop commands

### Added

- **VIP list**: admins can mark players as VIP with `/minted vip add <player>`,
  take it back with `/minted vip remove <player>`, and see everyone with
  `/minted vip list`. The list lives in `vips.yml` inside the plugin folder,
  records when and by whom each VIP was added, and follows name changes
  automatically.
- **Automatic `/<username>` shop command**: the moment a VIP creates their
  player shop (`/pshop create ...`), a command named after them goes live -
  anyone typing `/TheirName` opens that shop. It appears with the shop, is
  removed when the shop or the VIP is, survives restarts and `/eshop reload`,
  and is registered in the server's own command map, so it behaves like any
  other command. A name that is already a command is never overridden; the
  shop stays reachable as `/minted:<name>` and Minted logs it.
- **Dashboard "VIP list" tile**: `/minted dashboard` now shows the VIP count
  and opens a paginated page listing each VIP - their head, their
  `/<username>` command, and when they were added and by whom - with
  click-to-remove on every entry and an **Add VIP** button that prompts for a
  name in chat.
- `/minted help` lists the new `vip` command in the admin section, and
  `vip.enabled` in `config.yml` switches the `/<username>` commands off while
  keeping the list itself.

### Notes

- The command registration is reflection-guarded: on a server where the
  command map cannot be reached, Minted logs one warning and everything else
  keeps working - players just use `/pshop open <name>` instead. No other
  plugin is hard-depended on, and the commands re-check VIP status and shop
  ownership on every use, so a stale registration can never serve a removed
  VIP.

## [0.46.1] - 2026-09-24 - Item variant repair and player-shop frame

- Fixed: on modern servers (Spigot/Paper 1.13+) colored catalog items seeded by
  an older install showed their base variant - every wool looked white, every
  head a skeleton skull, and planks, beds, glass, and terracotta lost their
  color. A one-shot repair pass now re-resolves any global-shop row whose stored
  display name matches a catalog entry and whose material does not, rewriting
  it to the correct variant and persisting the fix. Run once, it is idempotent
  and never touches admin-priced or player-owned items.
- Fixed: the "Player shops" directory menu framed itself in the green shop
  accent; it now uses the same neutral gray frame as the shop chooser it opens
  from, so the border matches the gray filler instead of clashing.

## [0.46.0] - 2026-09-23 - Beautiful /minted help

- `/minted help` (and bare `/minted`) now shows a bordered, palette-styled
  command page instead of the old one-line tip.
- The list is filtered by permissions: players see exactly the player commands
  they may run, and staff-only pages (`admin`, `reload`, `report`, `npc`,
  shop/eco/language administration) appear only when the sender has the
  matching permission and are hidden from everyone else.
- Commands are grouped into player and admin sections with per-section counts,
  so the page reads like a proper manual rather than a wall of text.

## [0.45.1] - 2026-09-23 - Slimmed jar for resource upload

- The fat jar dropped from ~11 MB to ~3.5 MB so it fits SpigotMC's ~4 MB
  resource-upload limit, without removing any feature:
  - sqlite-jdbc natives are now shipped only for the platforms Spigot/Paper
    actually run on (Linux x86_64, Linux aarch64, Windows x86_64); the SQLite
    backend behaves exactly as before on every supported host.
  - MySQL is now served by MariaDB Connector/J 2.7 instead of the upstream
    driver: same `jdbc:mysql://` URL, full MySQL 8 (incl.
    `caching_sha2_password`) and MariaDB support, no protobuf dependency.
    The relocated driver class is now `dev.minted.libs.mysql.jdbc.Driver`.

## [0.45.0] - 2026-09-23 - Anonymous usage stats (bStats)

- bStats metrics (plugin id 34228) now report anonymous server/player counts
  and server-version distribution to <https://bstats.org>.
- Bundled and relocated into `dev.minted.libs.bstats` (official bStats
  integration), so it never collides with other plugins carrying their own
  copy; server owners can opt out via their bStats config file.
- Custom charts: economy mode (physical/digital), storage backend
  (sqlite/mysql) and whether the bank teller NPCs are enabled.

## [0.44.0] - 2026-09-23 - Configurable bank tellers

- New tellers default to the yellow `Banker` name and a mustachioed
  bank-man-in-a-suit skin, both editable via `integrations.npcs` in
  `config.yml`.
- `integrations.npcs.name` sets the on-screen display name (`&` colour
  codes work; the fake-player username is the name with colours stripped).
- `integrations.npcs.skin-value`/`skin-signature` set the default skin
  textures (signature empty unless required), and `skin-player` fetches a
  guaranteed Mojang-signed skin at placement time.
- `/minted npc create` now defaults the name from the config, so
  `/minted npc create` alone places a teller named `Banker`.

## [0.43.0] - 2026-09-23 - Player shops and sell-all

### Added

- **Player-owned shops (`/pshop`)** - every player can open their own storefront
  and sell real items at their own prices. One shop per account by default
  (config `shops.max-player-shops`). Built on the same engine as the community
  market: listings hold real stock, buyers pay the owner's wallet purse,
  earnings accrue per listing and are collected with `my`, stock can be
  withdrawn, and a listing cannot close while units or earnings remain.
  - `/pshop create <name>` opens the shop and stocks nothing yet; the shop icon
    is the item in hand when given.
  - `/pshop add <price>` lists the held stack (that slot's amount);
    `/pshop addstack <price>` lists every unit of that item carried.
    `/pshop sell` is the visual picker (price, buy-back, category).
  - `/pshop my` is the owner's view - collect earnings, withdraw stock.
  - `/pshop browse` lists every storefront; `/pshop open <name>` visits one;
    `/pshop` opens your own.
  - `/pshop delete` closes the shop, refused while anything is still stocked or
    earning.
  - Buyers see player listings through the normal grid and listing screens;
    a "Player shops" tile was added to the `/eshop` front door.
  - Under the hood: `shops` gained a nullable `owner` column (migrated
    idempotently), a new `ShopType.PLAYER`, and `ShopService.createPlayerShop/
    shopsFor/playerShopOf`. The community `Market` was generalized to any
    owner-backed shop and speaks player-shop text via a new `pshop.*` language
    section (English fallback for every other language).
  - Permissions: `minted.shop.own` (default true) for running a shop;
    `minted.shop.use` is still the entry to browse and buy.
- **Sell everything (`/sell menu`)** - a new grid of every item in your
  inventory the global shop buys, with the held amount, per-unit price and the
  worth of a full clear. Clicking a stack sells every unit you hold; a
  "Sell everything" button clears the whole sellable inventory. Valuation still
  runs through `Trade` (discount/sell-multiplier nodes, balance cap, all sell
  messages), and `/sell` gained a shared `Shop.matchSellable` so hand-sells and
  the menu agree exactly. `usage: /sell [amount|all|menu]`.

## [0.42.4] - 2026-09-23 - Chain resolves again after the 1.21.9 rename

### Fixed

- **`chain` was the only catalog item still skipped on 1.26**: Mojang renamed
  the plain `CHAIN` (chain) material to `IRON_CHAIN` in 1.21.9 (copper got its
  own chain variants), so Paper 1.21.9+ had no `CHAIN` constant and the key
  resolved to nothing. The registry row now carries the modern `IRON_CHAIN`
  name with `CHAIN` as the pre-rename fallback; on 1.21.9+ it resolves as
  iron chain, on 1.16-1.21.8 as chain, and it stays absent before 1.16.

## [0.42.3] - 2026-09-23 - Modern materials resolve again: api-version set

### Fixed

- **Coloured variants collapse to their base item (white wool, plain beds,
  single wood, white terracotta, missing netherite/trident/crossbow/...)**:
  Minted shipped without `api-version`, which silently marks it as a *legacy*
  plugin. Paper/Spigot then enable CraftLegacy legacy-material mode for it, and
  in that mode `Material.getMaterial()`/`matchMaterial()` resolve legacy names
  only - every modern constant (`OAK_PLANKS`, `RED_WOOL`, `RED_BED`, `TRIDENT`,
  `SPYGLASS`, ...) comes back null even though it exists in the 1.13+ enum, and
  the resolver falls back to the deprecated base names (`WOOL`, `BED`, `WOOD`)
  which is exactly the collapse seen since the flattening. Setting
  `api-version: 1.13` opts the plugin out of legacy-material mode: on 1.13+
  the modern constants resolve normally, while on 1.12 and below the value is
  ignored so the pre-flattening behaviour is unchanged.

> After updating, the shop gains the correctly-resolved variants on reload. Any
> already-stored collapsed duplicates (e.g. a red wool row held as white wool)
> can be removed through `/eshop edit`.

## [0.42.2] - 2026-09-23 - Material enum diagnostic at startup

### Added

- **Startup "Material probe" line**: Minted now logs once at startup how many
  modern constants this server's `Material` enum actually carries and lists the
  missing ones (e.g. `RED_WOOL`, `OAK_PLANKS`, `TRIDENT`). Across the Paper
  builds around the ItemType/BlockType release, whole swathes of constants were
  dropped for a couple of builds and restored later; this line makes it visible
  in five seconds whether "coloured variants collapse" is a Paper build problem
  (constants missing) or a Minted resolution problem (constants present).

## [0.42.1] - 2026-09-23 - Tellers use the modern spawn and player-list packets

### Fixed

- **Bank tellers stopped spawning on 1.20.2+**: Minecraft removed the classic
  \`NAMED_ENTITY_SPAWN\` packet in 1.20.2 in favour of the generic
  \`SPAWN_ENTITY\` (AddEntity) packet, so ProtocolLib threw "Could not find
  packet for type NAMED_ENTITY_SPAWN" and every teller failed to appear. On
  1.19.4+ tellers are now spawned through \`SPAWN_ENTITY\` with the player
  entity type.
- **Player-list entries broke on 1.19.3+**: the 1.19.3 rework of the player
  list requires the newer \`PlayerInfoData\` shape (profile UUID, the
  \`listed\` flag) through the \`PLAYER_INFO_UPDATE\` packet; the old
  four-argument construction threw \`Failed to construct PlayerInfoData\` on
  the newest servers. The modern entry is now built and sent correctly, so a
  teller's skin can register before the spawn reaches the client.

> Note: on servers that still report "you are X builds behind" from Paper, the
> shop catalog may skip items even though they exist on the latest stable
> build - update Paper and the catalog will resolve them again.

## [0.42.0] - 2026-09-23 - Distinct variants, per-version item API, resilient tellers

### Added

- **In-plugin item registry API (`dev.minted.api.MintedItems`)**: any plugin can
  now ask "which items exist on which Minecraft version, and does this item
  exist on version X" for every release from 1.8 up. Pure knowledge queries
  (`isAvailable(key, minor)`, `atVersion(key, minor)`, `itemsFor(minor)`,
  `since(key)`) are deterministic from the registry alone; live queries
  (`item(key)`, `material(key)`, `isAvailable(key)`) resolve against the running
  server. The facade is registered as a Bukkit service (`MintedItems.class`)
  and reachable through `MintedAPI.items()`, matching how `MintedAPI.economy()`
  works.

### Fixed

- **Coloured variants collapsed to their base item on modern servers**: red wool
  showed as white, every bed looked identical, wood types matched, and all
  skulls rendered as skeleton heads. On 1.13+ the old pre-rename names (WOOL,
  BED, SKULL_ITEM, ...) that still exist in the modern enum are deprecated
  aliases for the family's base variant, and the resolver walked into them
  whenever the modern constant was missing - turning every variant into one
  item. Resolution now only uses the modern name on 1.13+; a key whose modern
  constant is missing stays unresolved (it is skipped with the existing
  warning) instead of silently collapsing. Pre-1.13 variants keep their legacy
  durability (dye colour, wood type, skull type), which the seeder now stores
  on the seeded item so red wool is actually red and a creeper head is actually
  a creeper on old servers too.
- **A failing player-list packet hid the teller entirely**: on the newest
  servers ProtocolLib can throw `Failed to construct PlayerInfoData` while
  building the 1.19.3+ tab packet, and one exception aborted the whole spawn -
  the teller never appeared. The tab entry and the named-entity spawn are now
  sent independently: if the player list rejects the entry, the teller still
  spawns in the world and only the tab entry is skipped.

## [0.41.3] - 2026-09-22 - /minted npc wired to the teller manager

### Fixed

- **`/minted npc` always said "Bank tellers are not available" even though
  ProtocolLib hooked successfully**: the command manager captured the teller
  manager reference while it was still null (before the ProtocolLib hook ran),
  so it held null forever and answered every `npc` command with the red
  not-available error. The hook now runs before the command manager is built,
  so `/minted npc create <name>` works right after the "Hooked ProtocolLib"
  startup line.

## [0.41.2] - 2026-09-22 - Fixed version detection on post-1.26 servers

### Fixed

- **Everything version-gated returned nothing on servers that report the new
  version scheme**: after Minecraft stopped prefixing versions with "1." (from
  1.26 on), Bukkit reports e.g. `26.2-R0.1-SNAPSHOT` and the parser read it as
  major 26 / minor 2. The item registry then gated every material as "newer
  than the server" (2 < 8), so no catalog item resolved: category tiles
  collapsed to placeholders, the shop never grew, and every version-aware
  lookup came up empty even though the raw stored items still rendered. Version
  detection now re-maps the reported scheme onto the 1.x line - "26.2" is
  really 1.26.2 - so the full catalog resolves and every gate works again. The
  startup line now reports `server 1.26.2` instead of `server 26.2`. The same
  re-mapping was applied to ViaVersion's client-version parsing.

## [0.41.1] - 2026-09-22 - Category tiles always show a real item + growth on reload

### Fixed

- **Category tiles could show a placeholder**: if the version-aware icon could
  not resolve on a server, the tile fell back to a decorative skull (and before
  that, a bare chest). Category tiles now fall back to a material that exists on
  every supported server (stone, iron sword, iron chestplate, bread, iron ore,
  redstone, painting, minecart, chest, brewing stand, enchanted book), so every
  tile always shows a real category item.
- **The catalog did not grow on `/minted reload`**: catalog growth only ran on a
  full server start, so replacing the jar and reloading left the global shop
  unchanged. Growth now also runs on every reload - it is idempotent, so a fresh
  jar grows every global shop the moment it is loaded, with `Added N new
  item(s)` reported per shop.

### Changed

- **Catalog growth covers every global shop**, not only the first one found, so
  a server with several admin shops gets them all in step with the catalog.
- **A skipped row is no longer silent**: if the server cannot resolve some
  catalog items (a renamed material, a fork with a trimmed enum, ...), a
  startup/reload warning names the first affected keys so the cause is visible.

## [0.41.0] - 2026-09-22 - A truly big shop + heads that render on modern servers

### Added

- **The catalog is now a big shop**: on top of the classic stock, every global
  shop gains the full color families (all 16 dyes of wool, carpet, terracotta,
  concrete, concrete powder, stained glass, glass panes, beds and shulker
  boxes), every wood's stairs/slabs/fences/gates/doors/trapdoors for all nine
  tree types, quartz/purpur/deepslate/blackstone masonry, metal storage blocks,
  vanilla decorative heads (skeleton, wither skeleton, zombie, creeper, dragon,
  piglin), flowers, mob drops, brewing pickups and more food. The 1.8 -> 1.26
  spread is now ~three hundred sellable rows, version-gated as always.
- **Bank-teller hook failures are visible**: when ProtocolLib is present but the
  teller hook fails to start, the actual reason (usually a ProtocolLib build
  that does not match the server version) is shown in `/minted report` and
  pointed to from `/minted npc`.

### Changed

- **Category tiles can no longer degrade to a bare chest**: a material key that
  a server cannot resolve first falls back to looking the key up by its own
  lowercase name, and if that still fails the tile renders a decorative head
  instead of a plain chest - the shop front page always looks intentional.

### Fixed

- **Textured head icons now render on 1.18.2+ servers**: the reflective authlib
  path can be blocked by Paper's obfuscation, so every textured head is now
  first applied through the official `org.bukkit.profile.PlayerProfile` /
  `SkullMeta#setOwnerProfile` API with the authlib path kept as the fallback for
  older servers. Back arrows, coins, chests and page glyphs load their skins
  reliably on modern forks again.

## [0.40.0] - 2026-09-22 - Full catalog in every global shop + heads load their icons

### Added

- **Existing global shops grow with the catalog**: before, the big version-aware
  catalog only filled the Spawn shop on a fresh install, so an older database
  kept only its small starter stock. The global shop now syncs on every load:
  any item the running Minecraft version supports that the shop does not already
  sell is appended (existing items and admin price edits are never touched, and
  nothing is ever re-added twice). Upgraded servers now have the full catalog.

### Fixed

- **Menu heads showed the default "spawn skin" instead of their icon**: the
  baked-in Minecraft-Heads textures pointed at `http://textures.minecraft.net`,
  which modern clients refuse to fetch (HTTP 451) and render as a default
  Steve/Alex skin - so back arrows, coins, chests and page glyphs looked like
  blank player heads. All texture URLs are now `https://`, which Mojang serves
  reliably, and the intended icons load on every client.

## [0.39.1] - 2026-09-22 - Fixed shop GUI crash on item lookup

### Fixed

- **Shop and dashboard menus crashed with an NPE**: opening any global shop
  (`/shop`) threw on the first item lookup because the version-aware registry
  cached unresolved keys as `null` in a `ConcurrentHashMap`, which rejects null
  values (`MaterialLookup#resolve`). Unresolvable keys are no longer cached, so
  a key the server version cannot carry simply resolves to nothing and the tile
  falls back to its placeholder instead of crashing the render loop.

## [0.39.0] - 2026-09-22 - Version-aware item catalog + ViaVersion handler

### Added

- **Big version-aware item registry**: every material key Minted can hand out
  now records the earliest Minecraft release that introduced it (1.8 baseline
  up to 1.21+). Resolution is by name with legacy fallbacks, so on a 1.8-1.12
  server the pre-rename enum names are used and on 1.13+ the modern ones.
- **Own items per server version**: the global shop catalog derives each entry's
  first supported version straight from the registry, so the seeded shop grows
  with the server - 1.8 gets the classics, 1.13+ copper and amethyst, 1.16
  netherite, 1.19 sculk and mangrove, 1.20 cherry and bamboo, 1.21 crafter,
  copper bulb and mace, and nothing the server cannot represent is ever seeded.
- **ViaVersion handler**: on a server with ViaVersion (plus ViaBackwards /
  ViaRewind) Minted reads each client's protocol version and exposes
  client-aware material resolution (`ViaVersionHook#clientMaterial`, capped at
  both the client's own version and the server's). All ViaVersion access is
  reflection-guarded; without the plugin the catalog simply uses the server
  version. ViaVersion was added to `softdepend` and shows in `/minted report`.

## [0.38.0] - 2026-09-22 - Console-only balance administration + admin dashboard

### Added

- **Admin dashboard "Inspect player"**: `/minted dashboard` can now drill into
  any player, online or offline, and shows a read-only profile - bank balance,
  wallet, net worth, any open loan, open bounties on them, and their recent
  transaction history. The dashboard and its profile never edit money.

### Changed

- **`/eco` is console-only**: balance administration (`give`, `take`, `set`,
  `reset`) and the migration commands (`export`, `import`) can no longer be run
  by players in game, even with op or `minted.eco`. They run from the server
  console only, so an in-game admin can never create or destroy money.

## [0.37.2] - 2026-09-22 - Language selection now actually applies

### Fixed

- **`/language set` changed nothing**: the per-player preference was saved by
  `LanguageManager`, but every feature (bank, pay, shops, community market,
  auction house, bounties, wallet) read text from the old startup-time `Messages`
  snapshot, which ignored it. `Messages` is now a per-player view over
  `LanguageManager`, so `/language set <code>` immediately translates chat,
  broadcast, and menu messages for that player.
- **`/language global` and `/language reload` did nothing**: the runtime change
  never reached the startup-loaded bundles. Lookups without a player now resolve
  against the live global language, and `/language global <code>` persists
  `lang.code` to `config.yml`.
- **`/language` command output was hard-coded English**; it now uses the bundle
  keys (`language.*`), so it switches to the chosen language the moment a player
  selects one.
- **Shop GUI text stayed in the default language**: shop menus now resolve
  titles, buttons, and lore per viewer (`Menu.opener` + player-aware `Messages`).

## [0.37.1] - 2026-09-22 - Language enable crash fix

### Fixed

- **Startup crash** (`no such table: player_languages`): preferences were
  loaded before the languages table was created. The table is now created
  first in `LanguageManager.initialize()`.
- **Bundled packs not extracted**: only `lang/en.yml` was copied from the jar;
  all 12 packs (`ar`, `de`, `en`, `es`, `fr`, `it`, `ja`, `ko`, `pl`, `pt`,
  `ru`, `zh`) are now extracted on first start and discovered before the scan.
- **Saving a player language twice** would fail with a constraint error; the
  insert now uses a proper per-dialect upsert on the `uuid` primary key.

## [0.37.0] - 2026-09-22 - Arabic language pack

### Added

- **Arabic (`ar`) translation** bundled under `lang/ar.yml` with full key
  parity against `lang/en.yml`, covering shops, bank, bounties, wallets,
  community market, auction house, editors, and the `/language` command.

## [0.36.0] - 2026-09-22 - Out-of-the-box language packs

### Added

- **10 bundled translations** alongside English: Spanish (`es`), French (`fr`),
  German (`de`), Italian (`it`), Portuguese (`pt`), Russian (`ru`), Simplified
  Chinese (`zh`), Japanese (`ja`), Korean (`ko`), and Polish (`pl`).
- Every pack covers the full message key set (verified key parity with
  `lang/en.yml`) including shops, bank, bounties, wallets, community market,
  auction house, editors, and the `/language` command itself.
- Packs ship in the jar under `lang/` and are auto-discovered on first start;
  admins can override or extend them by dropping files in the plugin's `lang/`
  folder.

## [0.35.0] - 2026-09-22 - Multi-Language System

### Added

- **Multi-language support** with `/language` command
- Per-player language preference (saved to database, persists across restarts)
- Global server language (config: lang.code)
- Auto-discovers languages from lang/ folder - just drop .yml files
- English built-in as fallback, never leaves blank messages
- Admin commands: `/language global <code>`, `/language reload`
- Player commands: `/language set <code>`, `/language reset`, `/language list`
- Easy to add new languages: copy lang/en.yml to lang/xx.yml and translate

## [0.34.0] - 2026-09-22 - Easiest Config Ever

### Added

- **Auction House** (`/ah`): Player-to-player auctions with bidding, optional buyout, listing fees, and expiration. Includes full GUI browser, create auction flow, my-auctions and my-bids management menus.
- **Multi-Currency system**: Support for multiple currencies with exchange rates (disabled by default). Base currency stored internally, additional currencies configured via `/currency` commands. Config section `multi-currency` with base currency definition.

### Changed

- Default `integrations.vault.register` to `false` in config.yml so Minted runs fully self-contained without Vault. Set to `true` if you want other plugins (EssentialsX, shops, Towny, etc.) to use Minted via Vault.

## [0.31.1] - 2026-09-20 - Sell identity fix

### Fixed

- `/sell` (and selling in the shop GUIs) could not recognize a plain, naturally
  gathered item against a stocked listing whose icon carried a display name:
  identity was checked with `ItemStack#isSimilar`, which compares the display
  name too, so a wild apple never matched the shop's "Apple" entry
  (`The global shop does not buy this item`). Identity is now the kind of
  goods - same material, and same data where data is identity rather than tool
  damage - so cosmetics no longer decide what can be sold.

## [0.31.0] - 2026-09-20 - Modern-seed hardening

### Fixed

- The shop seeder no longer crashes on 1.13+ servers when a lenient material
  lookup resolves a catalog key to a block-only material (the `CARROTS` crop on
  1.26 threw `CARROTS isn't an item`, aborting the seeding task and leaving the
  global shop half-stocked). Non-item resolutions are now skipped so the seed
  completes on every supported version.
- Bundled `slf4j-nop` (relocated alongside the API) so HikariCP's pool logging
  no longer makes slf4j print its unbound-binder error to stderr at every boot,
  which also silences Paper's `System.out/err.print` nag for Minted.

## [0.30.0] - 2026-09-20 - Roadmap complete

### Changed

- README revalidated: the status header now points at the released version and
  the package layout table covers the ledger history, the public economy API,
  the optional integration hooks and the admin-facing command surface.

## [0.29.0] - 2026-09-20 - Admin dashboard

### Added

- **`/minted dashboard`** (alias `/minted admin`, `minted.admin`): an in-game
  overview for operators. It shows live, memory-cached totals - money in every
  bank, money in every wallet, how much has been burned out of the economy, and
  the active sinks (interest rate, fee percentages, physical vs. digital mode) -
  plus quick links to the economy stats, the shop browser, the bounty board,
  recent sales and an in-menu config reload.

## [0.28.0] - 2026-09-20 - Bounty throttle

### Added

- **Per-player escrow cap** (`bounty.max-open`, default 2,000,000): a single
  player can no longer park an unlimited amount across open bounties. 0 disables
  the cap and falls back to the per-posting maximum.
- **Bounty expiry** (`bounty.expiry-days`, default 0 = off): bounties left
  unclaimed past the window are refunded to the placer automatically, freeing
  money that would otherwise be parked forever. When a placer is online they are
  notified as the refund lands.
- Bounty notes are now clamped to 64 characters so very long notes cannot blow
  up the board or the database.

## [0.27.0] - 2026-09-20 - Rich list paging

### Changed

- The richest-players leaderboard (the page behind `/mstats`) now loads the
  whole top list once and pages through it instead of showing a fixed top 8.
  Prev/next arrows and a page counter appear on every page past the first.

## [0.26.0] - 2026-09-20 - Bank sinks

### Added

- **Withdrawal fee** (`bank.fee.withdraw-percent`, default 0 = off): a
  percentage taken out of the bank on every cash withdrawal. The player still
  receives the full amount in notes; the fee itself is money removed from the
  economy. Applied in both `/bank withdraw` and the bank menu.
- **Transfer fee** (`bank.fee.transfer-percent`, default 0 = off): a percentage
  charged on top of every `/pay` - online and offline alike. The recipient always
  receives the full amount, and the fee vanishes from the sender's wallet.
- Sinks are counted as burned money in the economy stats, and fees never block a
  settlement: if money cannot reach the recipient the fee goes back with it.

## [0.25.0] - 2026-09-20 - Balance migration

### Added

- **`/eco export <file>`** (admin): writes every primary balance to a plain-text
  file in the plugin's `exports/` folder - one `uuid <space> balance` line per
  account, with a comment header - so a server can back up or move its economy.
  Cached accounts overlay stored values, so an unsaved deposit is never lost.
- **`/eco import <file> confirm`** (admin): sets every primary balance back from
  an export file. Importing requires the explicit `confirm` word, applies each
  line with the normal balance cap, and reports how many lines were skipped
  (invalid, unknown, or over the cap). Both commands run off the main thread.

## [0.24.0] - 2026-09-20 - Offline payments

### Added

- **Pay offline players**: `/pay <name> <amount>` now accepts a player who is
  not online, so long as that name has joined Minted before. Their money is
  banked for them and waiting when they next log in. The payer's wallet is
  debited up front and refunded the instant settlement fails, and deliveries
  are only confirmed after the money has landed.

## [0.23.0] - 2026-09-20 - Personal transaction history

### Added

- **Personal transaction history** (`/mhistory`, permission `minted.history`,
  plus a History button in `/bank`): every money movement on a player's balance
  is recorded - payments, money requests, bank deposits and withdrawals, shop
  buys and sells, bounties, loans, loan repayments, bank interest and admin
  (`/eco`) adjustments - and shown newest-first in a GUI. Rows are inserted off
  the main thread and pruned so the table stays bounded; a lost row never blocks
  the trade that caused it.

## [0.22.0] - 2026-09-20 - Admin economy command

### Added

- **`/eco give|take|set|reset <player> [amount]`** (permission `minted.eco`,
  default op): edits the primary balance - the one Vault and placeholders read,
  the bank by default. Online players are edited through their live account;
  offline players are resolved by name and read from storage, so admin tooling
  works while the player is away. The write runs off the main thread and the
  confirmation reports the new balance.

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