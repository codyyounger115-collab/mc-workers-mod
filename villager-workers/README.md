# Villager Workers (Fabric, Minecraft 1.21.1)

Give a villager a tool and it goes to work during the day.

| Tool in villager's hand | Job | What it does |
|---|---|---|
| Pickaxe (any tier) | Miner | Mines exposed ore it is strong enough for (coal, iron, copper, gold, redstone, lapis, diamond, emerald) |
| Axe | Lumberjack | Walks to a natural tree and fells the whole tree. Player-built log buildings are left alone |
| Hoe | Farmer | Harvests fully grown wheat, carrots, potatoes and beetroot, and replants them |
| Fishing rod | Fisher | Walks to nearby water and catches fish/junk from the vanilla fishing loot table |

Without a chest link, drops land on the ground next to the villager. With one, see below.

## Chest linking

Use a **stick** as the link wand:

1. **Sneak + right-click a chest or barrel** with the stick to select it (again to deselect).
2. **Sneak + right-click each villager** with the stick to link it. One selected chest can link many villagers.
3. **Unlink:** with nothing selected, sneak + right-click a linked villager with the stick.

A linked villager carries what it gathers (saved with the villager) and walks it to the chest once it
holds 16+ items, or when it runs out of work nearby. Double chests work. If the chest is full or removed,
the extra items are dropped on the ground. The chest must be within 32 blocks when you link it.
A villager that dies drops what it was carrying.

## Controls

- **Give a tool:** hold it, **sneak + right-click** the villager. If it already holds a tool, you get the old one back.
- **Take the tool back:** **sneak + right-click** with an empty hand.
- Normal right-click (no sneak) still opens trading.

## Rules

- Works only in daylight, and not while trading, sleeping, hiding, panicking or during a raid.
- Tools lose 1 durability per block/catch and break like normal (unbreaking is ignored).
- The tool drops if the villager dies. Baby villagers can't be given tools.
- Miners only mine ore with an open side (they can't tunnel) and only ore the pickaxe can actually harvest.

## Building

Requires JDK 21.

1. Open the folder in IntelliJ IDEA (it will import the Gradle project and download Minecraft), or
2. From the command line with Gradle 8.12+ installed: `gradle wrapper` once, then `./gradlew build`.

The mod jar ends up in `build/libs/villager-workers-1.0.0.jar`. Put it in your `mods` folder along with
[Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1. To test in a dev client: `./gradlew runClient`.

## Layout

- `VillagerWorkersMod` - entrypoint, attaches the job goals to every villager that loads
- `VillagerToolInteraction` - the sneak + right-click give/take logic
- `ToolRole` - maps tool items to jobs
- `goal/ToolWorkGoal` - shared find-target / walk / work / finish loop
- `goal/MineOreGoal`, `ChopTreeGoal`, `HarvestCropGoal`, `FishGoal` - one per job

The held tool is stored in the villager's normal main-hand slot, so it is saved with the world,
visible on the villager, and needs no extra data.

## Tuning

Search radii (the first numbers passed to `scan(...)`), walking speed (the `0.6` in each goal's constructor)
and work durations (`beginWork()`) are plain numbers in each goal class.
