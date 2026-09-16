# Minted Free

A GUI-first economy plugin for premium survival and roleplay servers.
**Status:** `v0.1.0` - Foundation (build skeleton + cross-version layer).

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
| `dev.minted.compat` | `ServerVersion` - version parsing, NMS package detection |
| `dev.minted.util` | Version-safe reflection helpers for NMS access |

## Design rules

- **Compile against 1.8.8 API.** The `pom.xml` pins `spigot-api:1.8.8` as its
  only dependency, so everything referenced here is guaranteed to exist at
  runtime from 1.8 upward. Newer-server material is reached through the compat
  layer, never through direct API calls.
- **No `api-version` in `plugin.yml`.** Deliberate: keeps legacy behaviour
  identical across the supported range.
- **Fully async storage** is a hard requirement from the start; the backend
  milestone must never touch the main thread on disk or on the wire.

## Commandments for contributors

1. No new Bukkit API call resulting from a later version than 1.8.8 in *core*
   code paths - if it is not available on 1.8, it goes behind an adapter.
2. Never couple feature code to `org.bukkit.craftbukkit` or `net.minecraft.*`
   directly. Use `dev.minted.util.Reflection`.
3. Keep every database operation off the main thread.