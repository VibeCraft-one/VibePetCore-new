# VibePetCore Resource Pack Asset Inventory

Version: 2.6.37  
Pack SHA1: `996118a5a85f33292f85157780201ec8cc1381d4`  
Zipped size: ~31 KiB (unpacked source ~22 KiB)

## Summary

| Category | Count | Unpacked size | In plugin jar | In resource pack |
|----------|------:|--------------:|:-------------:|:----------------:|
| Textures (PNG) | 34 | ~9.3 KiB | No | Yes |
| Item models (JSON) | 34 | ~3.7 KiB | No | Yes |
| Spawn-egg overrides (JSON) | 17 | ~9.5 KiB | No | Yes |
| `pack.mcmeta` | 1 | 94 B | No | Yes |
| Custom sounds (`.ogg`) | 0 | 0 B | No | No |
| **Total visual assets** | **86** | **~22 KiB** | **No** | **Yes** |

The plugin jar contains only server logic, YAML configs, and embedded libraries (Gson, SQLite JDBC). All client-facing visuals live exclusively in `resource-pack/VibePetCore/` and the published zip (`dist/VibePetCore-resource-pack.zip`).

## Textures

All textures are 16×16 PNG placeholders generated for pet egg miniatures.

### Egg state (`assets/vibepetcore/textures/item/egg/`)

| File | Size |
|------|-----:|
| allay.png | 270 B |
| armadillo.png | 269 B |
| axolotl.png | 270 B |
| bat.png | 269 B |
| bee.png | 270 B |
| blaze.png | 270 B |
| breeze.png | 270 B |
| cat.png | 270 B |
| fox.png | 270 B |
| frog.png | 269 B |
| ghast.png | 271 B |
| panda.png | 271 B |
| parrot.png | 270 B |
| phantom.png | 269 B |
| rabbit.png | 270 B |
| vex.png | 271 B |
| wolf.png | 271 B |

### Empty-slot state (`assets/vibepetcore/textures/item/empty/`)

| File | Size |
|------|-----:|
| allay.png | 264 B |
| armadillo.png | 264 B |
| axolotl.png | 264 B |
| bat.png | 264 B |
| bee.png | 264 B |
| blaze.png | 264 B |
| breeze.png | 264 B |
| cat.png | 264 B |
| fox.png | 264 B |
| frog.png | 264 B |
| ghast.png | 264 B |
| panda.png | 263 B |
| parrot.png | 264 B |
| phantom.png | 264 B |
| rabbit.png | 264 B |
| vex.png | 264 B |
| wolf.png | 264 B |

**Decision:** Textures are the heaviest visual assets (~9 KiB total). They are fully offloaded to the resource pack and are not shipped in the plugin jar.

## Item Models

### Egg models (`assets/vibepetcore/models/item/egg/`)

17 files, ~104–110 B each. Standard `minecraft:item/generated` parent pointing at `vibepetcore:item/egg/<pet>`.

### Empty-slot models (`assets/vibepetcore/models/item/empty/`)

17 files, ~106–112 B each. Same pattern for empty GUI slots.

**Decision:** Models stay in the resource pack only. Server code references them indirectly via `custom_model_data` thresholds on vanilla spawn eggs.

## Spawn Egg Overrides

17 files under `assets/minecraft/items/*_spawn_egg.json` (~551–569 B each).

Each file uses `minecraft:range_dispatch` on `custom_model_data` with two thresholds per pet type:
- `egg` group (active pet slot miniature)
- `empty` group (empty slot miniature)

Example thresholds for wolf: `200117` (egg), `200217` (empty). Full mapping is defined in `tools/generate_resource_pack.ps1` and mirrored in `BalanceConfig.eggCustomModelData()`.

**Decision:** These JSON overrides require a client resource pack (Minecraft 1.21.4+ item model format). They are not bundled in the jar.

## Pack Metadata

| File | Size | Notes |
|------|-----:|-------|
| `pack.mcmeta` | 94 B | `pack_format: 75` |

## Sounds

### Custom sounds in resource pack

**None.** There is no `assets/vibepetcore/sounds.json` and no `.ogg` files in the repository.

### Server-side sound usage (vanilla `Sound` enum only)

All gameplay audio uses built-in Minecraft sounds. No custom sound assets are required.

| Source class | Purpose | Sound examples |
|--------------|---------|----------------|
| `PetAttackEffects` | Combat impact/miss | `ENTITY_PLAYER_ATTACK_SWEEP`, `ENTITY_BLAZE_SHOOT`, `ENTITY_BREEZE_SHOOT`, `ENTITY_GHAST_SHOOT` |
| `PetEmotionProfile` | Ambient pet mood | Per-type vanilla ambient (`ENTITY_WOLF_AMBIENT`, `ENTITY_CAT_AMBIENT`, …) |
| `PetInteractionEffects` | Pet interaction feedback | `ENTITY_PLAYER_ATTACK_WEAK` |
| `PetSpawnSupport` | Spawn feedback | `ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM` |
| `PetPlayerControlSupport` | Control feedback | `ENTITY_ALLAY_AMBIENT_WITH_ITEM` |
| `PetAbilityService` | Ability effects | `ENTITY_BEE_LOOP_AGGRESSIVE`, `ENTITY_GHAST_SHOOT`, `ENTITY_PARROT_AMBIENT` |
| `PetEngineManager` | Level-up, death, milestones | `ENTITY_EXPERIENCE_ORB_PICKUP`, `BLOCK_RESPAWN_ANCHOR_DEPLETE`, `UI_TOAST_CHALLENGE_COMPLETE` |
| `PetEggController` | Egg GUI interactions | `ENTITY_ITEM_PICKUP`, `BLOCK_AMETHYST_BLOCK_CHIME`, `UI_TOAST_CHALLENGE_COMPLETE` |
| `PetGuiService` / GUI pages | Menu open/close | `UI_BUTTON_CLICK`, `BLOCK_ENDER_CHEST_OPEN`, `BLOCK_ANVIL_USE` |
| `PetMasterManager` | Master abilities | `BLOCK_BEACON_POWER_SELECT`, `BLOCK_CONDUIT_ACTIVATE` |
| `LootBoxManager` | Box open results | `UI_TOAST_CHALLENGE_COMPLETE`, `ENTITY_PLAYER_LEVELUP` |

**Decision:** Custom sounds are not needed for current functionality. Vanilla sounds are a compatible solution: they work without any resource pack and do not add jar weight. If branded pet sounds are required in a future release, add `assets/vibepetcore/sounds.json` and `.ogg` files to `resource-pack/VibePetCore/` and reference them from server code via `Sound` keys `vibepetcore:<name>`.

## What Stays in the Plugin Jar

| Content | Approx. role |
|---------|--------------|
| Java bytecode | Server logic |
| `config.yml`, `plugin.yml`, messages | Configuration |
| Gson + SQLite JDBC | Runtime libraries |
| SQLite natives (Linux x86_64, Linux aarch64) | Database on supported Linux hosts |

No PNG, OGG, or client model JSON files are packaged in the jar.

## Build and Deployment Paths

| Artifact | Produced by | Used by |
|----------|-------------|---------|
| `build/resource-pack/VibePetCore-resource-pack.zip` | `./gradlew buildResourcePack` | Local testing |
| `dist/VibePetCore-resource-pack.zip` | `./gradlew publishResourcePack` | Git/CDN distribution |
| `plugins/VibePetCore/resource-pack/VibePetCore-resource-pack.zip` | Manual copy by server admin | `ResourcePackManager` auto-host |

## ТЗ Checklist

| Requirement | Status |
|-------------|--------|
| Heavy assets identified and inventoried | Done (this document) |
| Models/textures offloaded from jar | Done |
| Sounds decision documented | Done — vanilla only, no custom pack sounds |
| Resource pack published separately | Done — `dist/` and `build/resource-pack/` |
| SHA1 pinned in migration/config | Done — `996118a5a85f33292f85157780201ec8cc1381d4` |
