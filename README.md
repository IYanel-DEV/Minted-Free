# Minted Free

A GUI-first economy plugin for premium survival and roleplay servers.
**Status:** `v0.30.0` - Full roadmap complete (banknotes to admin dashboard).

Minted ships **1.8 through 1.26 in a single jar**. No separate JARs per
version, no `api-version` legacy switch, no per-version shims duplicated in
feature code. Everything version-sensitive routes through the `compat` layer.

## Building

Requires JDK 8+ (JDK 17+ recommended). The wrapper pins Maven for you.

```
./mvnw clean package
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
| `dev.minted.gui` | Reusable inventory-menu framework and chat prompts |
| `dev.minted.shop` | Shops: model, storage, `/eshop`, menus, buy/sell trade, sales feed |
| `dev.minted.bounty` | Player bounties: post, board, kill-claims, refunds and expiry |
| `dev.minted.wallet` | The wallet item: carry and pay directly from banknotes |
| `dev.minted.resourcepack` | Optional banknote custom-texture prompt (1.14+) |
| `dev.minted.sound` | Version-safe money moment sounds |
| `dev.minted.request` | Player-to-player money requests |
| `dev.minted.lang` | Language bundle lookup with English fallback |
| `dev.minted.api` | Public economy API, events, MintedEconomy (Vault/PlaceholderAPI hook) |
| `dev.minted.integration` | Optional integration hooks (Vault, PlaceholderAPI, NPC tellers) |
| `dev.minted.compat` | `ServerVersion` - version parsing, NMS package detection |
| `dev.minted.util` | Version-safe reflection helpers for NMS access |

## Design rules

- **Compile against 1.8.8 API.** The `pom.xml` pins `spigot-api:1.8.8` as its
  only dependency, so everything referenced here is guaranteed to exist at
  runtime from 1.8 upward. Newer-server material is reached through the compat
  layer, never through direct API calls.
- **`api-version: 1.13` in `plugin.yml`.** Opts out of the server's
  legacy-material mode, so modern `Material` constants (`OAK_PLANKS`, `RED_WOOL`,
  ...) resolve on the single jar. Paper/Spigot ignore the value on 1.12 and
  below, so the 1.8 -> 1.26 range is unaffected.
- **Fully async storage** is a hard requirement from the start; the backend
  milestone must never touch the main thread on disk or on the wire.

## Commandments for contributors

1. No new Bukkit API call resulting from a later version than 1.8.8 in *core*
   code paths - if it is not available on 1.8, it goes behind an adapter.
2. Never couple feature code to `org.bukkit.craftbukkit` or `net.minecraft.*`
   directly. Use `dev.minted.util.Reflection`.
3. Keep every database operation off the main thread.