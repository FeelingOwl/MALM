# Manual areas: research and design

Status: **proposal, no code yet.** This adds command-made areas next to the datapack layers, in three kinds:

1. **Biome patch**: "turn *this* connected patch of biome into its own area".
2. **Structure instance**: "turn *this* one structure, not every structure of its type, into its own area".
3. **Box**: "x y z plus dx dy dz, with the level I choose".

All APIs below were checked against Forge 1.20.1-47.3.5 (`ServerChunkCache.randomState()`,
`BiomeSource.getNoiseBiome(x, y, z, Climate.Sampler)`, `DimensionDataStorage.computeIfAbsent(load, create, name)`,
`StructureStart.getChunkPos()/getBoundingBox()`, `ResourceKeyArgument.getStructure`).

## 1. What they share

A manual area is a layer that lives in **world save data instead of a datapack**:

* **Same values as datapack layers**: the `config` fields (`min_lvl`, `max_lvl`, multipliers, `stats`, `random_range_*`),
  plus `display_name`, `announce` and `priority`. That way the resolver, `EffectiveConfig`, titles, mob spawn-memory and `/malm here`
  all reuse the existing code.
* **Id**: `manual:<name>`, where the name is chosen in the command (`/malm area ... <name>`). Mobs store layer ids, so a mob that spawned in a manual area keeps
  its exp, loot and stats after you leave, as long as the area exists.
* **Storage**: one `SavedData` per dimension (`<world>/<dim>/data/malm_areas.dat`), server side only, saved with the
  world. It survives restarts and `/reload`, and is independent of datapacks.
* **Instanced maps excluded**: maps are excluded exactly like layers (`MapDimensions.isMap`).

### Priority (needs your decision, see §6)

Proposal: manual areas override **within their own tier**, and boxes go on top:

```
Box  >  Structure (manual instance > datapack structure layer)  >  Biome (manual patch > datapack biome layer)  >  Dimension
```

Your Structure > Biome > Dimension rule stays. A manual biome patch replaces the datapack biome layer there, a
manual structure replaces the datapack layer for that one instance, and a box beats everything, since it's the most
deliberate ("this exact cube"). Multipliers still never stack. Level fields fall back down this chain.

## 2. Case 1: biome patch

**The problem**: a biome patch isn't stored anywhere in Minecraft. Biomes are a 3D grid of 4×4×4 "quart" cells, and a
"patch" is just a connected run of cells with the same biome. That run can be huge (an ocean goes on for thousands of blocks)
and can reach into chunks that aren't generated yet.

**Approach: flood fill once, store a bitmap**
* `/malm area biome create <name>` takes the biome at your feet and flood-fills outward over the 2D quart grid
  (4-connected) at your Y level.
* **Sampled without loading chunks**: `generator.getBiomeSource().getNoiseBiome(qx, qy, qz, randomState.sampler())`
  is the same function worldgen uses, so it gives the generated biome for any spot, loaded or not, with no chunk loads.
  It's thread-safe, because worldgen runs it on worker threads, so the fill runs **off the server thread**, reports when it's done, and
  then installs the patch on the main thread.
* **Capped**: there's a max radius (default 768 blocks) and a max cell count. If the cap is hit, the patch is cut off at the edge and the
  command says so. You can raise the cap with an argument.
* **Stored compactly**: each chunk is 4×4 quart columns, so 16 bits. The patch is a map from chunk to a 16-bit mask. An 800×800-block
  ocean is about 2,500 chunks, roughly 25 KB.
* **Matching**: a position is in the patch if its column bit is set **and the biome at the position is still the patch biome**. Without
  the biome check, a forest patch would also cover the lush cave or deep dark underneath it. With it, the patch stays
  "this biome, here".

**Caveat**: noise sampling returns the *generated* biome. A biome changed afterwards (`/fillbiome`, or mods that repaint
biomes after generation) isn't reflected in the fill. The biome-equality check at query time does use the live biome,
so a repainted spot simply stops matching.

## 3. Case 2: structure instance

**This part is straightforward.** A structure instance is uniquely `(structure id, start chunk)`, the same key the
random-range roll already uses.
* `/malm area structure create <name> [structure]` uses the structure you're standing in. If several overlap, it takes
  the first valid one, or the one you name. It stores the structure id, the start chunk and the bounding box (the bounding box is only for `/malm area show`).
* **Matching**: `AreaResolver` already reads the chunk's structure references. For each reference, it checks the manual map
  (structure id → start chunks) before the datapack layers. This is a map lookup plus the containment check it already does.
* `match: pieces | bounding_box` works the same as in datapack layers.

## 4. Case 3: box (x y z + dx dy dz)

* `/malm area box create <name> <x y z> <dx dy dz>` uses vanilla `BlockPosArgument`, so `~ ~ ~` works, plus three integers. Like
  vanilla selectors, dx/dy/dz can be negative and the box is the span between the two corners, inclusive. An alternative
  form, `/malm area box create <name> from <x y z> to <x y z>`, takes two corners for `/fill`-style input.
* **Matching**: an index from chunk to the boxes touching it. A lookup is one map get plus a few AABB checks. Boxes can overlap. The
  highest `priority` wins, and ties go to the smallest box, so you can nest a small box inside a big one without touching priority.

## 5. Commands (op level 2)

```
/malm area box create <name> <x y z> <dx dy dz>
/malm area box create <name> from <x y z> to <x y z>
/malm area structure create <name> [structure]
/malm area biome create <name> [max_radius]

/malm area set <name> <field> <value>        e.g. min_lvl 30 | exp_multi 1.5 | display_name "Cursed Bay"
/malm area unset <name> <field>              back to "not set" (level fields fall back down the chain)
/malm area copy <name> <layer_id>            copy config from a datapack layer, e.g. malm_example:ocean_monument
/malm area info <name> | list | remove <name>
/malm area show <name>                       particle outline for ~10 s (box / structure bbox / patch edge)
```

Values are set one field per command because `config` has 15+ fields and Brigadier can't do optional named
arguments well. Combined with `copy`, the usual flow is two or three commands. Field names and validation are shared with
`LayerParser`, so a bad value fails the same way it does in a datapack file.

## 6. Decisions for you

1. **Priority**: is the order in §1 right (Box > manual structure > structure layer > manual patch > biome layer > dimension)?
   Or should manual areas sit above everything, including datapack structure layers?
2. **Biome patch extent**: should a patch match only where the live biome is still the patch biome (proposed), or the whole column
   top to bottom?
3. **Default flood-fill cap**: is a 768-block radius OK?
4. **Random ranges in manual areas**: should a box or patch with `random_range_*` roll once at creation and store
   the window (proposed)? A structure instance would roll exactly like a datapack layer does now.

## 7. Build order

1. Save data, the manual-area model, and the resolver integration for **boxes** (the simplest case, and it proves the storage, priority
   and spawn-memory path).
2. Structure instances: just a lookup inside the existing structure scan.
3. Biome patches: the async flood fill, the bitmap and the cap handling.
4. `set`/`unset`/`copy`/`info`/`list`/`remove`, then `show` particles.
