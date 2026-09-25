# Minted Free

A GUI-first economy plugin for premium survival and roleplay servers.
**Status:** `v0.62.0`

<p align="center">
  <img src="assets/banner-header.jpg" alt="Minted - GUI-first economy plugin" />
</p>

Minted ships **1.8 through 1.26 in a single jar**. No separate JARs per
version, no per-version shims duplicated in feature code. Everything
version-sensitive routes through the `compat` layer, and `api-version: 1.13`
in `plugin.yml` opts out of the server's legacy-material mode so modern item
names resolve everywhere. On startup Minted self-checks the whole alias table
(`Material probe: 20/20`).

## Downloads

Latest release (with jar attached): <https://github.com/IYanel-DEV/Minted-Free/releases>

## Features

<p align="center">
  <img src="assets/banner-features.jpg" alt="Minted features" />
</p>

### Money

- **Two balances** - a bank and a wallet per player, with
  `integrations.primary-balance` deciding which one Vault, PlaceholderAPI and
  the public API see.
- **Physical cash** - paper banknotes you carry and spend (`/wallet`), with an
  optional resource pack for custom textures, *or* a classic digital balance
  (`economy.physical: false`). Notes are signed, so edited or forged notes are
  rejected on redeem.
- **Banking** - interest, loans with fees and late fees, withdrawal/transfer
  fees, and player-to-player requests (`/pay` and the clickable request
  message).
- **Payment source of your choice** - `/minted source wallet|bank|both`.
  `both` pays from the wallet and falls back to the **full amount** from the
  bank when the wallet cannot cover it. A bank-funded purchase starts a
  short delivery cooldown (`payments.bank-cooldown-seconds`, default 7),
  announced in chat; wallet purchases are never blocked.
- **Multi-currency** - an optional base currency with exchange rates for
  shops and auctions (`/currency`).
- **Stats** - server-wide totals, rich list and burn tracking (`/mstats`), and
  a personal ledger per player (`/mhistory`).

### Trading

- **Global shops** (`/eshop`) with a seed...

### Auctions

- **Auction house** (`/ah`) - list, bid, buyout, claim, cancel. Built-in
  listing fee, duration, and buyout. Listings persist across restarts.
- **/ah browse** - search, filter, sort. Items shown with hover preview.
- **/ah my** - your active listings and sales history.
- **/ah bids** - bids you placed, with claim button.
- **/ah create** - guided creation with item picker.

### Player shops

- **/pshop create** - spawn a storefront chest at your feet.
- **/pshop browse** - directory of player shops, by owner head.
- **/pshop buy/sell** - trade with players; bank/wallet/ both payments.
- **VIP shops** - custom icon, renamed, `/playername` shortcut.

### Bounties

- **/bounty add** - place a bounty on a player, funded from wallet or bank.
- **/bounty list** - open bounties, sorted by reward.
- **/bounty claim** - kill the target, auto-payout with death proof.

### Vault / PlaceholderAPI / ProtocolLib

- **Vault** - economy hook for other plugins.
- **PlaceholderAPI** - `%minted_balance%`, `%minted_bank%`, `%minted_wallet%`, `%minted_currency%`.
- **ProtocolLib** - optional, used for signed note NBT.

### Web editor

- **GitHub Pages** - <https://IYanel-DEV.github.io/Minted-Free/>
- Edit global shops visually, download `global-shops.yml`, import back.
- Per-version templates (1.8-1.26) from the plugin catalog.

## Config highlights

```yaml
integrations:
  primary-balance: wallet
  physical: true
economy:
  starting-balance: 1000
  max-balance: 1000000
payments:
  bank-cooldown-seconds: 7
  default: both
shops:
  global:
    catalog: true
```

## Commandments for contributors

1. No new Bukkit API call resulting from a later version than 1.8.8 in *core*
   code paths - if it is not available on 1.8, it goes behind an adapter.
2. Never couple feature code to `org.bukkit.craftbukkit` or `net.minecraft.*`
   directly. Use `dev.minted.util.Reflection`.
3. Keep every database operation off the main thread.

## Contributors

<p align="right">
  <a href="https://github.com/Iyouniss"><img src="https://github.com/Iyouniss.png?size=64" width="48" height="48" alt="Iyouniss" style="border-radius:50%;margin:4px;"></a>
  <a href="https://github.com/iyanel01"><img src="https://github.com/iyanel01.png?size=64" width="48" height="48" alt="iyanel01" style="border-radius:50%;margin:4px;"></a>
  <a href="https://github.com/vexx-rain"><img src="https://github.com/vexx-rain.png?size=64" width="48" height="48" alt="vexx-rain" style="border-radius:50%;margin:4px;"></a>
  <a href="https://github.com/IYanel-DEV"><img src="https://github.com/IYanel-DEV.png?size=64" width="48" height="48" alt="IYanel-DEV" style="border-radius:50%;margin:4px;"></a>
</p>

## Support and links

- Releases and downloads: <https://github.com/IYanel-DEV/Minted-Free/releases>
- Source and issues: <https://github.com/IYanel-DEV/Minted-Free>
- In game, `/minted report` prints the live state of every integration - the
  fastest way to diagnose "why isn't X hooked".
- Anonymous usage stats (bStats, id 34228) can be disabled in
  `plugins/bStats/config.yml`; no balance or player data is ever collected.