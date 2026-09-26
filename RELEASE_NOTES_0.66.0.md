## [0.66.0] - 2026-09-26 - Beautiful Discord webhook embeds

### Added
Discord webhook embeds are now fully redesigned as polished, card-style notifications:

- **Author header** showing the server name (with an optional icon) at the top of every embed
- **Event-tinted colors**: green shop sales, teal player shops, purple auctions, gold buyouts, pink bounties, purple milestones, blue transfers, green/red server start & stop
- **Summary line** below the title, e.g. `**Steve** bought **5 × Diamond** from **Global Shop**`
- **Bold-labelled fields** arranged in tidy rows of three
- **Divider rows** visually separating groups like sale details vs. tax collected
- **Normalized server version** (`Paper 1.26.2` instead of `git-Paper-432 (MC: 1.26.2)`)
- **Player count** on server start embeds
- New `discord.embed.author-icon-url` option to set the small icon beside the server name

### Changed
- Footer now automatically shows the server icon when `show-server-icon` is enabled and an author icon is configured.

---
- 0.65.2 → 0.66.0 (minor - embed redesign feature)