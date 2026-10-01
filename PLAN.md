# MALM (Mine and Slash Area Layers): implementation plan

Target: Forge 1.20.1 (47.3.5), Mine and Slash 6.4.13 and Library of Exile 2.1.14. All hook points below were
checked against both the source (`D:\MNS Breakdown\reference\mine-and-slash-rework`) and the bytecode of
the 6.4.13 jar you play with.

## 1. How Mine and Slash does it today

Dimension configs are `DimensionConfig` entries (registry `mmorpg_dimension`, datapack folder
`data/<ns>/mmorpg_dimension/*.json`, keyed by `dimension_id`, with a `default` fallback).
The only way to look one up is `ExileDB.getDimensionConfig(LevelAccessor world)`, **which takes a world and
no position**. That is why there is no finer granularity. Every caller:

| Caller | What it reads | Position available |
|---|---|---|
| `LevelUtils.determineLevel(en, world, pos, nearestPlayer, variance)` | `min_lvl`, `max_lvl`, `min_lvl_area`, `mob_lvl_per_distance`, `scale_to_nearest_player`, `secondary_lvl_range` | `pos` |
| `OnMobDeathDrops.GiveExp(victim, …)` | `exp_multi` | `victim` |
| `LootInfo.gatherLootMultipliers()` | `all_drop_multi` | `this.pos` |
| `MobStatUtils.getWorldMultiplierStats(en)` | `mob_strength_multi` | `en` |
| `MobStatUtils.getMobConfigStats(entity, data)` | `stats` (extra mob stats) | `entity` |

`determineLevel` runs for mob levels at spawn (`EntityData.SetMobLevelAtSpawn`), for the player's HUD "area level"
(`OnServerTick`), for the player level-info command (`PlayerCommands`), and for chest and spawner loot levels (`LootInfo.setLevel`).
Mine and Slash already has an unused `LevelInfo.LevelSource.BIOME` enum value, but nothing sets it.
There is no event on the Library of Exile or Mine and Slash bus for changing level or area multipliers, so
**this needs Mixins**.

## 2. Core idea: a position-aware `DimensionConfig`

Don't reimplement the level math. Give Mine and Slash a *different `DimensionConfig` object* for each position:

```
effective = merge( dimensionConfig(world),        // base: Mine and Slash's own entry, untouched
                   matching biome layers,          // lowest priority first
                   matching structure layers )     // innermost/highest priority last
```

Each Mixin swaps the `ExileDB.getDimensionConfig(world)` call at the five call sites above for
`AreaResolver.resolve(world, pos)`, using `@Redirect` or MixinExtras `@WrapOperation`, both with `remap = false` since the
targets are Mine and Slash methods. Everything downstream (the min-level area, distance scaling,
scale-to-nearest-player, secondary range, the final clamp) keeps working and now honours the layers.
This touches the fewest things and survives most Mine and Slash updates, because it only depends on
those five `invokestatic` sites.

The resolved config is a fresh `DimensionConfig` instance, built by copying the base, and cached (see §5). The
registry object is never mutated.

## 3. Data format

This is a new datapack registry, loaded by our own `SimpleJsonResourceReloadListener` (server-side only, no client
sync needed, since the area level reaches the client through Mine and Slash's own player-data sync).

There are two folders, and the folder decides the layer type, so there's no `type` field:
* `data/<namespace>/malm_biomes/*.json` for biome layers
* `data/<namespace>/malm_structures/*.json` for structure layers

```jsonc
// data/mypack/malm_biomes/lukewarm_ocean.json
{
  "targets": ["minecraft:lukewarm_ocean", "#minecraft:is_deep_ocean"],   // biome ids or #tags
  "dimensions": ["minecraft:overworld"], // optional filter; empty = any dimension
  "priority": 0,                         // highest wins when several biome layers match
  "config": {                            // same field names as Mine and Slash's DimensionConfig
    "min_lvl": 20,
    "max_lvl": 35,
    "exp_multi": 1.5
  }
}
```

```jsonc
// data/mypack/malm_structures/ocean_monument.json
{
  "targets": ["minecraft:monument"],     // structure ids or #tags
  "dimensions": ["minecraft:overworld"], // optional condition
  "biomes": ["minecraft:deep_lukewarm_ocean", "#minecraft:is_ocean"], // optional condition, structure layers only:
                                         // the biome AT THE POSITION must match (a structure can span several biomes)
  "match": "pieces",                     // "pieces" (precise, default) | "bounding_box"
  "priority": 10,
  "config": {
    "min_lvl": 40, "max_lvl": 45,
    "exp_multi": 2.0, "all_drop_multi": 1.5, "mob_strength_multi": 1.3,
    "stats": { "stats": [ /* same shape as DimensionConfig.stats */ ] }
  }
}
```

**`config` accepts every `DimensionConfig` field**, including `min_lvl`, `max_lvl`, `min_lvl_area`, `mob_lvl_per_distance`,
`scale_to_nearest_player`, `secondary_lvl_range`, `exp_multi`, `all_drop_multi`, `mob_strength_multi` and `stats`. Only fields
you actually write are applied. That is why it's parsed as a raw `JsonObject` and not with Gson defaults: with Gson,
a missing field would come back as `1`, `100` and so on, and silently overwrite the lower layer.

### Merge rules (decided): Structure > Biome > Dimension

At any position, exactly one layer is **active**: the highest-`priority` matching structure layer, else the
highest-`priority` matching biome layer, else the dimension config.

**Multipliers and stats never stack or pass down.** `exp_multi`, `all_drop_multi`, `mob_strength_multi` and `stats`
come *only* from the active layer. If the active layer leaves one out, it's `1.0` (or no extra stats). It is never
the biome's or dimension's value. A structure's ×2.0 exp inside a ×1.5 ocean is ×2.0, not ×3.0 and not ×1.5.

**Level fields** (`min_lvl`, `max_lvl`, `min_lvl_area`, `mob_lvl_per_distance`, `scale_to_nearest_player`,
`secondary_lvl_range`) come from the active layer too. The one exception: if the active layer leaves one out, it's
taken from the next layer down. This way a structure layer that only sets `exp_multi` doesn't reset mob levels to 1–max.

Example: the dimension is 1–50 with ×1.2 exp. The ocean is 20–35 with ×1.5 exp. A monument inside it sets 40–45 and
×2.0 exp but no `all_drop_multi`. In the monument: level 40–45, ×2.0 exp, ×1.0 loot. In the rest of the ocean: 20–35 and ×1.5 exp.

### What "level range" means inside a layer

Mine and Slash computes the level from distance to world spawn and then clamps it to `[min_lvl, max_lvl]`, and a layer
reuses that exact pipeline. So:
* `min_lvl` + `max_lvl` alone: the distance-based level clamped into the layer's range. A narrow range such as
  40–45 means "this area is about level 40–45", with ±variance from the Mine and Slash config.
* Also set `scale_to_nearest_player: true` to get "scale to player, but only within 40–45".
* Also set `min_lvl_area` to a huge number to get "always exactly `min_lvl`", since the distance pipeline then always returns `min_lvl`.

A possible later extension is a `"level_mode": "random_in_range"` that picks uniformly in `[min,max]`. It would need a small
extra injection in `determineLevel`, so it's out of the MVP.

## 4. Mixins (MVP)

| Mixin target | Injection | Resolve at |
|---|---|---|
| `LevelUtils.determineLevel` | wrap `ExileDB.getDimensionConfig` | `pos` argument |
| `OnMobDeathDrops.GiveExp` | wrap `ExileDB.getDimensionConfig` | the mob's **stored spawn layer** (§6) |
| `LootInfo.gatherLootMultipliers` | wrap `ExileDB.getDimensionConfig` | mob kill: stored spawn layer; otherwise `this.pos` |
| `MobStatUtils.getWorldMultiplierStats` | wrap `ExileDB.getDimensionConfig` | stored spawn layer |
| `MobStatUtils.getMobConfigStats` | wrap `ExileDB.getDimensionConfig` | stored spawn layer |

Plus one `@Inject` at the `RETURN` of `determineLevel` that appends a `BIOME` (or new) `LevelSource` entry, so
Mine and Slash's existing level-debug tooltip shows *which layer* applied. This is only cosmetic.

Instanced map dimensions (Dungeon Realm, Harvest, Obelisks) return before the dimension config is read, or
are forced to scale to the player, so layers never affect them. We also skip resolution entirely for any dimension that has no
layers, so the hot path costs nothing there.

## 5. Resolution and performance

`AreaResolver.resolve(ServerLevel, BlockPos)`:
1. If there are no layers for this dimension, return Mine and Slash's own config (no allocation).
2. Biome: `level.getBiome(pos)` gives a `Holder<Biome>`. Match by key or `holder.is(tag)`. It's cheap and already loaded.
3. Structure: `level.structureManager().getAllStructuresAt(pos)` reads only the chunk's structure
   *references*, which is cheap. Filter to structures that some layer targets, then confirm with
   `getStructureWithPieceAt(pos, structure)` (or `getStructureAt` for `bounding_box`). Guard with
   `level.hasChunkAt(pos)` so we never force-load chunks, which matters because mob spawn can happen inside chunk loading.
4. Pick the active layer and build its effective config (active layer, plus fallback level fields from the next layers down). Cache it by `(dimension, active structure layer, active biome layer)`: the result depends only on *which* layers are active,
   not the exact position, so we compute only a handful of configs per world and reuse them. The cache is cleared on
   datapack reload.

`OnServerTick`'s area-level update per player then costs one biome lookup and one chunk-reference lookup, each
typically an empty or small set.

## 6. Keep a mob tied to the layer it spawned in

A mob's level is fixed at spawn, but exp, loot and stat multipliers are read later from its *current* position.
A monument guardian that wanders out would otherwise have level-45 stats but pay out ocean exp. So:
* At `SetMobLevelAtSpawn`, which calls `determineLevel`, record the active layer ids (structure layer + biome layer) on the entity
  in `getPersistentData()` under `malm:layers`. This is cheap, persists with the entity, and needs no capability.
* The exp, loot and stat mixins resolve from those stored ids when they are present, and fall back to the current
  position when they are not (old mobs, chest loot, block drops).

## 7. Tooling

* `/malm here`: prints the dimension, biome, the matched structures and layers, and the active layer and effective
  config at your position. Doing this by hand is how you'd otherwise debug overlapping layers.
* `/malm reload` is just `/reload`. Layers are a normal datapack reload listener.
* A log line on reload per layer file, plus warnings for unknown biome or structure ids.

## 8. Build order

1. **Data layer**: the `AreaLayer` model, JSON parsing (raw `JsonObject` in §3), the reload listener and id/tag validation.
2. **Resolver**: biome and structure matching, the merge and the cache. Unit-testable without the game (merge rules).
3. **Level mixin** (`determineLevel`): the first visible result, since mobs and the HUD area level follow layers.
4. **Multiplier mixins** (exp, loot, strength and stats) plus the spawn-layer tag on entities (§6).
5. **Debug command** and the level-tooltip source entry.
6. An example datapack in `src/main/resources/data/malm_examples/`, disabled by default, or shipped as docs only.
7. Test in the dev client (`./gradlew runClient`) using an ocean monument, a village and a lukewarm ocean.

## 9. Risks

* **Mine and Slash updates** can move or rename these five call sites. With `defaultRequire: 1`, a mixin that no longer
  applies fails loudly at startup instead of silently doing nothing. We re-check with `javap` on each Mine and Slash bump.
* **Craft to Exile 2 config overrides**: `SCALE_MOB_LEVEL_TO_NEAREST_PLAYER` (server config) short-circuits the
  distance and area logic *before* the dimension config's min-level area is consulted, but the final clamp to the
  layer's `[min,max]` still applies, so layers still bound the level.
* **Structure piece checks during chunk load**: mitigated by `hasChunkAt` and by only reading references of
  the entity's own chunk.

## 9b. Area titles (added 2026-10-01)

When the active layer at a player's position changes, the server sends a vanilla title, `Name [min-max]`. Nothing
is needed client side.
* Triggers on a change of *active layer* (or dimension), not on every area-level change: distance scaling moves the
  level constantly, and that shouldn't spam titles.
* The range is the effective config's `getLevelRangeFor(player)`, capped at Mine and Slash's `MAX_LEVEL`, so it respects
  `secondary_lvl_range`.
* Name: the layer's `display_name` (a string or text component) if set, otherwise the prettified file name
  (`ocean_monument` → "Ocean Monument"). Leaving every layer shows the dimension name with its own range.
* Layer field `"announce": false` silences one layer.
* Debounced: a new area must hold for two checks. Each area also has a re-announce cooldown, so walking along a border
  doesn't spam titles. Logging in sets a silent baseline.
* Server config `serverconfig/malm-server.toml`: `show_area_titles`, `show_title_on_leave`, `level_range_as_subtitle`,
  `check_interval_ticks` (10), `reannounce_cooldown_seconds` (30), and the fade-in/stay/fade-out timings.

## 9c. Random level ranges per structure instance (added 2026-10-01)

These are `config` fields, for structure layers only. Setting `random_range_increments` turns the mode on:
```json
"random_range_min": 10, "random_range_max": 45, "random_range_increments": 4
```
* Each structure *instance* rolls a window `increments` levels wide whose low end is uniform in `[min, max - increments]`,
  so the window never crosses min or max. Example: [34-38], read as "area level 36, ±2". If the bounds are narrower
  than the increments, the window is the whole bounded range.
* A missing `random_range_min` or `random_range_max` falls back to the resolved `min_lvl` / `max_lvl` chain (this layer, then biome, then
  dimension), capped at Mine and Slash's `MAX_LEVEL`.
* The roll is deterministic: seeded from world seed + layer id + structure id + the structure start's chunk. A given
  monument keeps its range across restarts, and every player sees the same one. Editing the bounds re-rolls everything.
* Mobs spawned inside get a uniform random level across the window, not Mine and Slash's distance level clamped to an edge. Level lookups
  without a mob (HUD area level, chest loot) use the centre. This overrides scale-to-nearest-player for that structure.
  The level-debug tooltip lists it as the `BIOME` source, which is Mine and Slash's only unused one.
* The secondary level range is disabled inside a rolled window. Mobs remember their instance (`malm:layers.instance`), so exp, loot and
  stats follow it. Area titles treat each instance as its own area.

## 10. Decisions (settled 2026-09-30)

1. **Priority**: Structure > Biome > Dimension, and one active layer per position. Multipliers and stats never
   stack and never pass down from lower layers. Only missing level fields fall back (§3).
2. **Mobs keep the layer they spawned in** (§6).
3. **Identity**: mod id `malm` ("Mine and Slash Area Layers Mod"), package `com.feelingowl.malm`, author `feelingowl`.
4. **Target version**: Mine and Slash **6.4.13**, the newest released build (the Mahjerion repo's `1.20-Forge` is at an
   untagged, unreleased 6.4.14), and Library of Exile 2.1.14. All five hook sites are identical in the 6.4.14 source, so
   the next release should need no mixin changes.
5. **Folders**: `data/<ns>/malm_biomes/` and `data/<ns>/malm_structures/`, one per layer type (no `type` field).
6. **Conditions**: every layer takes `dimensions`, and structure layers also take `biomes`. All conditions must hold,
   so you can write "this structure, in this biome, in this dimension". To give the same structure different values
   per biome or dimension, use several files with different conditions and priorities.
