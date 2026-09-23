# Minted Free

A GUI-first economy plugin for premium survival and roleplay servers.
**Status:** `v0.44.0`

Minted ships **1.8 through 1.26 in a single jar**. No separate JARs per
version, no per-version shims duplicated in feature code. Everything
version-sensitive routes through the `compat` layer, and `api-version: 1.13`
in `plugin.yml` opts out of the server's legacy-material mode so modern item
names resolve everywhere. On startup Minted self-checks the whole alias table
(`Material probe: 20/20`).

## Downloads

Latest release (with jar attached): <https://github.com/IYanel-DEV/Minted-Free/releases>

## Features

- **Bank tellers** - self-built ProtocolLib NPCs (`/minted npc`). No Citizens
  dependency. New tellers default to a yellow *Banker* name and a
  mustachioed bank-man-in-a-suit skin, both configurable in `config.yml`
  under `integrations.npcs`.
- **Global + player shops** - community shops (`/eshop`) with a seeded catalog
  and player-owned shops (`/pshop`), each with own inventory pages, currency,
  buy/sell prices and a searchable grid.
- **Sell-all** - `/sell menu` sells every distinct stack you hold in one click.
- **Auction house** - `/ah`: timed listings, bids, buyouts, expire and collect.
- **Physical cash** - paper banknotes that mint, verify and redeem (`/wallet`).
- **Bounties** - players post bounties, killers claim them (`/bounty`).
- **Banking** - interest, loans, fees, transferable balances (`/bank`).
- **Transaction history** - per-player ledger (`/mhistory`).
- **Admin tools** - `/eco`, admin dashboard, account inspection.
- **Languages** - 12 packs out of the box (English, Arabic, German, Spanish,
  French, Italian, Japanese, Korean, Polish, Portuguese, Russian, Chinese)
  with `/language`.
- **Integrations** - Vault, PlaceholderAPI, EssentialsX detection, ViaVersion,
  and a public `MintedAPI`/`MintedEconomy` with balance events.

## Building

Requires JDK 8+ (JDK 17+ recommended). The wrapper pins Maven for you.

```
# Linux / macOS
./mvnw clean package

# Windows
.\mvnw.cmd clean package
```

Output: `target/Minted-<version>.jar`

## Layout

| Package | Purpose |
| --- | --- |
| `dev.minted` | Plugin entry point, lifecycle wiring |
| `dev.minted.command` | Command registration and dispatch |
| `dev.minted.backend` | Async SQL storage (sqlite/mysql) over a shaded Hikari pool |
| `dev.minted.bank` | Balances, transfers, loans, interest, fees and the batched save loop |
| `dev.minted.banknote` | Paper banknotes: mint, verify, redeem |
| `dev.minted.ledger` | Personal transaction history (`/mhistory`) |
| `dev.minted.auction` | Auction house: listings, bids, buyouts, expiry (`/ah`) |
| `dev.minted.currency` | Multi-currency definitions and conversion |
| `dev.minted.gui` | Reusable inventory-menu framework and chat prompts |
| `dev.minted.shop` | Shops: model, storage, `/eshop`, `/pshop`, menus, buy/sell trade, sell-all, sales feed |
| `dev.minted.bounty` | Player bounties: post, board, kill-claims, refunds and expiry |
| `dev.minted.wallet` | The wallet item: carry and pay directly from banknotes |
| `dev.minted.resourcepack` | Optional banknote custom-texture prompt (1.14+) |
| `dev.minted.sound` | Version-safe money moment sounds |
| `dev.minted.request` | Player-to-player money requests |
| `dev.minted.lang` | Language bundles (12 packs) with English fallback |
| `dev.minted.api` | Public economy API, events, MintedEconomy (Vault/PlaceholderAPI hook) |
| `dev.minted.integration` | Optional hooks (Vault, PlaceholderAPI, NPC tellers, ViaVersion) |
| `dev.minted.compat` | `ServerVersion`, `MaterialLookup` - version parsing, item/material resolution |
| `dev.minted.util` | Version-safe reflection helpers for NMS access |

## Design rules

- **Compile against 1.8.8 API.** The `pom.xml` pins `spigot-api:1.8.8` as its
  only dependency, so everything referenced here is guaranteed to exist at
  runtime from 1.8 upward. Newer-server material is reached through the compat
  layer, never through direct API calls.
- **`api-version: 1.13` in `plugin.yml`.** Opts out of the server's
  legacy-material mode, so modern `Material` constants (`OAK_PLANKS`, `RED_WOOL`,
  ...) resolve on the single jar. Paper/Spigot ignore the value on 1.12 and
  below, so the 1.8 -> 1.26 range is unaffected. `MaterialLookup` keeps a
  per-version rename map (e.g. `chain` -> `IRON_CHAIN`) and logs a diagnostic
  probe at startup.
- **Fully async storage** is a hard requirement from the start; the backend
  milestone must never touch the main thread on disk or on the wire.
- **No required dependencies.** Every third-party hook (ProtocolLib, Vault,
  PlaceholderAPI, ViaVersion) is optional, guarded, and degrades to a sane
  default when absent.

## Commandments for contributors

1. No new Bukkit API call resulting from a later version than 1.8.8 in *core*
   code paths - if it is not available on 1.8, it goes behind an adapter.
2. Never couple feature code to `org.bukkit.craftbukkit` or `net.minecraft.*`
   directly. Use `dev.minted.util.Reflection`.
3. Keep every database operation off the main thread.