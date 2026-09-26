## [0.65.2] - 2026-09-26

### Fixed
- **Discord webhooks now actually work.** The embed `color` was sent as a signed 32-bit integer: `Color(0x4CAF50).getRGB()` produced `0xFF4CAF50`, whose signed value is `-11751600`. Discord only accepts `0`–`16777215`, so it rejected every embed with `400 {"embeds": ["0"]}`. Color is now masked to 24 bits (`& 0xFFFFFF`), producing the valid positive value Discord expects.
- Webhook embeds (server start/stop, shop sales, purchases, auctions, bounties, transfers) will now be delivered.

---
- 0.65.1 → 0.65.2 (patch - webhook embed color fix)