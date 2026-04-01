# Havoc

Spigot **1.8.8** plugin: procedurally placed Havoc bases (WorldEdit schematics + SaberFactions `Havoc` system faction), breach flow with Salvage currency, shop GUI (`/havoc shop`), and timed terrain / claim reset.

## Setup

1. Install **SaberFactions** (plugin name `Factions`) and **WorldEdit** on the server.
2. Create a system-style faction tagged **`Havoc`** (match `havoc-faction-tag` in `config.yml`).
3. Put `EasyBase.schematic`, `MediumBase.schematic`, and `HardBase.schematic` under `plugins/Havoc/schematics/`.
4. Tune `config.yml` (world name, border half-size, counts, schematic names, shop rows/items).

## Commands

- `/havoc shop` — Salvage shop (inventory GUI).
- `/havoc salvage` — Show your Salvage balance.
- `/havoc admin spawn <EASY|MEDIUM|HARD>` — Spawn one base (`havoc.admin`).
- `/havoc admin reload` — Reload config and data files.

## Build

```bash
mvn clean package
```

Havoc links against **WorldEdit 6.1.x** for compile; SaberFactions is used at runtime via a small **reflection** layer (`FactionsBridge`) so the project does not depend on Saber’s broken Maven graph.

## MVP limitations (TODO later)

- **Persistence**: restore timers, active bases, and raid state are **not** saved across restarts; a reload mid-restore loses in-memory state.
- **Satellite reset**: entire watch ring is snapshotted and reset (not only “dirty” chunks); pistons, liquids, and cross-chunk edge cases are not fully modeled.
- **Breach rule**: only **center chunk** obsidian / water breaks count; outer-shell-only detection is not implemented.
- **NPC shop**, richer raid attribution, **Factions events** API instead of reflection, schematic per-difficulty paste offsets, and anti-abuse (alt cycling, offline rewards) are not done.
- **SaberFactions JAR** must be present at runtime; reflection targets `com.massivecraft.factions` as in SaberFactions 1.8-era builds—verify on your exact fork.
