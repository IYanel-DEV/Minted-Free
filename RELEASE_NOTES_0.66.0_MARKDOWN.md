# Minted Free 0.66.0 — Beautiful Discord Webhook Embeds

[![Download](https://img.shields.io/badge/Download-Minted--0.66.0.jar-brightgreen?style=for-the-badge)](https://github.com/IYanel-DEV/Minted-Free/releases/tag/v0.66.0)
[![Version](https://img.shields.io/badge/Version-0.66.0-blue?style=for-the-badge)](https://github.com/IYanel-DEV/Minted-Free/releases/tag/v0.66.0)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.8%E2%80%931.26-green?style=for-the-badge)](https://github.com/IYanel-DEV/Minted-Free)
[![License](https://img.shields.io/badge/License-MIT-orange?style=for-the-badge)](LICENSE)

---

**One jar. Minecraft 1.8 → 1.26. No per-version downloads. No required dependencies.**

## 🎉 What's New in 0.66.0

### 🖼️ Discord Webhook Embeds — Fully Redesigned
- **Card-style notifications:** every embed is now a polished, readable card
- **Server author header:** server name with optional icon at the top of every embed
- **Event-tinted colors:** green shop sales, teal player shops, purple auctions, gold buyouts, pink bounties, purple milestones, blue transfers, green/red server start & stop
- **Summary line:** e.g. `**Steve** bought **5 × Diamond** from **Global Shop**` right under the title
- **Bold-labelled fields** laid out in tidy rows of three
- **Divider rows** visually separate logical groups (sale details vs. tax collected)
- **Normalized version string:** "Paper 1.26.2" instead of "git-Paper-432 (MC: 1.26.2)"
- **Player count** shown on server start embeds
- New `discord.embed.author-icon-url` config for the small icon beside the server name

### 📮 Discord Webhooks + Tax System (0.65.0)
- **Webhooks for all economy events:** shop sales, player shop sales, auctions, bounty placing/claiming, balance milestones, large transfers, server start/stop
- **Tax system:** per-shop-type buy/sell taxes (global shop, player shop, auctions) with minimum threshold
- Taxes are **burned from economy** and logged in the ledger
- **Per-event webhook URLs** with ping support for important events

### 🩺 Crash-Proofing & Version Checker (0.65.1)
- **CRITICAL: server crash fixed** — webhook called `Server.getServerName()` which doesn't exist on Paper 1.26.2; now uses reflection with fallback
- **Version Checker:** checks GitHub on startup, notifies ops/admins in-game + console when a new version is available
- **Crash protection:** defensive try-catch in `onEnable()` prevents init errors from killing the server

---

## 💰 Economy & Tax Configuration

```yaml
discord:
  enabled: true
  default-webhook-url: "https://discord.com/api/webhooks/..."
  embed:
    show-server-name: true
    show-server-icon: false
    color: "#4CAF50"
    thumbnail-url: ""
    author-icon-url: ""    # icon beside the server name at the top
    footer-text: "Minted Economy"
    ping-on:
      large-transfer: ""
      auction-buyout: ""

tax:
  enabled: true
  global-shop-buy: 1.0     # %
  global-shop-sell: 1.0
  player-shop-buy: 1.0
  player-shop-sell: 1.0
  auction-buy: 1.0
  auction-sell: 1.0
  minimum-amount: 1.0

update-checker:
  enabled: true            # notify admins when a new release is out
```

---

## ⚙️ How to Set Up Discord Notifications

**One-Time Setup (2 minutes):**
1. In Discord: Server Settings → Integrations → Webhooks → New Webhook
2. Copy the webhook URL
3. In `config.yml`: set `discord.enabled: true` and `discord.default-webhook-url`
4. (Optional) set individual URLs per event under `discord.webhooks.`
5. Restart the server — done!

---

## 👥 Credits & Acknowledgments

### Core Development
- **IYanel-DEV** — Lead developer, architecture, all systems

### Community Contributors
- **LlmDl** ([SpigotMC Profile](https://www.spigotmc.org/members/llmdl.33402/)) — **Suggested VaultUnlocked (vault2 API) support** implemented in 0.49.0
- **Iyouniss** — Contributor
- **iyanel01** — Contributor
- **vexx-rain** — Contributor

### Third-Party Libraries (Bundled & Relocated)
HikariCP · MariaDB Connector/J · sqlite-jdbc · bStats (id 34228) · JUnit 5 (test scope) · VaultUnlockedAPI (provided)

---

## 📥 Installation & Upgrade

### Fresh Install
1. Drop `Minted-0.66.0.jar` in `plugins/`
2. Start server — SQLite works with zero config
3. Try `/minted gui` — personal hub with balance, bank, wallet, stats, requests

### Upgrade from Any Version
1. Replace jar, restart
2. Existing global shops → auto-exported to `global-shops.yml` exactly once
3. Existing player shops → already in database, no action needed

---

## 🔗 Links

- **Releases & Downloads:** <https://github.com/IYanel-DEV/Minted-Free/releases>
- **Source & Issues:** <https://github.com/IYanel-DEV/Minted-Free>
- **Web Editor:** <https://IYanel-DEV.github.io/Minted-Free/>
- **Wiki:** <https://github.com/IYanel-DEV/Minted-Free/wiki>
- **In-Game Diagnostics:** `/minted report` — live state of every integration
- **bStats:** <https://bstats.org/plugin/bukkit/Minted/34228>

---

> **One jar, 1.8 → 1.26** • **Compile against 1.8.8 API** • **api-version: 1.13** • **Fully async storage** • **No required dependencies** • **Self-tests as source of truth**

---

**Enjoy Minted 0.66.0!** 🎉