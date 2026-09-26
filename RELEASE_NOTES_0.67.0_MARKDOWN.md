# Minted Free 0.67.0 — /donate Command

[![Download](https://img.shields.io/badge/Download-Minted--0.67.0.jar-brightgreen?style=for-the-badge)](https://github.com/IYanel-DEV/Minted-Free/releases/tag/v0.67.0)
[![Version](https://img.shields.io/badge/Version-0.67.0-blue?style=for-the-badge)](https://github.com/IYanel-DEV/Minted-Free/releases/tag/v0.67.0)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.8%E2%80%931.26-green?style=for-the-badge)](https://github.com/IYanel-DEV/Minted-Free)
[![License](https://img.shields.io/badge/License-MIT-orange?style=for-the-badge)](LICENSE)

---

**One jar. Minecraft 1.8 → 1.26. No per-version downloads. No required dependencies.**

## 🎁 What's New in 0.67.0

### 💸 /donate — No-Fee Gifts with a Fanfare
New **`/donate <player> <amount>`** command (alias `/tip`):
- **No fee** — every coin lands with the receiver
- **Receiver must be online** so they see it land:
  - Money is added to their wallet (chattable person-to-person, wallet to wallet)
  - Chat line: `You were donated $500 by Steve!`
  - Text floats **above the health bar** (action bar) with the amount and donor
  - A **little fanfare melody** plays as the money arrives
- **Spam guard:** the same receiver won't hear the music again within `donate.music-cooldown-seconds` (default **5s**), so donation spam can't replay it over and over
- **Tab completion** lists online players
- New permissions:
  - `minted.donate` (default true) — use the command
  - `minted.donate.others` (default true) — tab-complete other players
- New config: `donate.music-cooldown-seconds` and `sounds.donate-sent`
- Ledger keeps both sides (kind `donate`) and it works across the full 1.8 → 1.26 range through the existing compat layer

---

## ⚙️ Configuration

```yaml
donate:
  # How long before the fanfare can play again for the same receiver (seconds)
  music-cooldown-seconds: 5

sounds:
  enabled: true
  volume: 1.0
  donate-sent: "NOTE_PLING"   # sound the donor hears
```

## 💻 In-Game

```
/donate Steve 500       # give $500 to the online player Steve
```

---

## 📦 Also in This Release Line (0.66.0 / 0.65.x)

- **Redesigned Discord webhook embeds** — card-style with event colors, summaries, dividers, server author header, normalized version
- **Webhooks + tax system** — every economy event hooked, configurable per-shop-type taxes
- **Crash-proofing** — fixed Paper 1.26.2 server-name crash, defensive `onEnable`, version checker

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
1. Drop `Minted-0.67.0.jar` in `plugins/`
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

**Enjoy Minted 0.67.0!** 🎉