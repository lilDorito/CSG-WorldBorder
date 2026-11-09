# CSG WorldBorder

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-3C8527?style=for-the-badge" alt="Minecraft 1.20.1" />
  <img src="https://img.shields.io/badge/Forge-47%2B-F58220?style=for-the-badge" alt="Forge 47+" />
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/Side-Client%20%2B%20Server-5865F2?style=for-the-badge" alt="Client and server" />
</p>

**CSG WorldBorder** by **lilDorito** turns the vanilla world border into a **3D volume** built from shapes and
boolean operations (CSG, constructive solid geometry).

> **All Rights Reserved.** Modpacks are welcome; see [LICENSE.txt](LICENSE.txt) for the terms.

Spheres, boxes, cylinders, cones and tori, each one movable, rotatable, scalable and animated, combined with
**add**, **subtract** and **intersect**. The mod does **not** add a border of its own: it makes the vanilla
border *be* that shape. Everything in Minecraft that already respects the border (block placing and breaking,
collision, mob spawning, portals, respawning, explosions, pistons, damage, the red warning) now respects the
3D shape, as if it had always been that way.

modId: `csgworldborder` · version: `1.0.0` · required on both client and server

## Contents

- [Why](#why)
- [Features](#features)
- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Shapes](#shapes)
- [Layers and modes](#layers-and-modes)
- [The classic border](#the-classic-border)
- [Animation](#animation)
- [Battle royale and timed borders](#battle-royale-and-timed-borders)
- [The editor (GUI)](#the-editor-gui)
- [Settings](#settings)
- [Protection](#protection)
- [Maps and other mods](#maps-and-other-mods)
- [What follows the 3D border](#what-follows-the-3d-border)
- [Where data is saved](#where-data-is-saved)
- [Mod compat](#mod-compat)
- [API for other mods](#api-for-other-mods)
- [Build](#build)
- [Project layout](#project-layout)
- [Limits](#limits)
- [Credits](#credits)
- [License](#license)

## Why

Custom maps, PvP arenas and event servers all hit the same wall: the vanilla border is a square, flat,
endless-height box. Existing border mods add a **second** border next to vanilla's, with their own rules and
their own gaps. CSG WorldBorder takes the other way: the one border Minecraft already has becomes any shape you
can build, and the whole game keeps treating it as native.

- a dome arena with a pit cut into its floor
- a floating island you can't fly away from
- a tower you can only climb inside
- a ring-shaped race track
- a battle royale zone that shrinks as a sphere instead of a square

## Features

**3D border**
- Five primitives: **box**, **sphere**, **cylinder**, **cone**, **torus**.
- Every shape can be moved, rotated (yaw, pitch, roll) and scaled per axis.
- Shapes are **layers** combined top to bottom with **add**, **subtract** or **intersect**; an advanced
  **formula** (`border & (arena | lobby) - pit ^ ring`) is there when layers are not enough.
- **Block-snapped**, like the vanilla border: a block is either fully inside or fully outside. Curved shapes
  have flat faces instead of single-block bumps.
- **Animated**: any change can play out over time, like vanilla's `/worldborder set <size> <time>`, and keeps
  going across restarts.
- Each **dimension** has its own shapes.
- **Classic outlines**: the vanilla border itself can be a **square**, **circle**, **hexagon**,
  **octagon**, **diamond**, **triangle** or **star**
  column, still sized and moved by vanilla `/worldborder set` and `center`.

**Native behaviour**
- Collision for players, mobs and vehicles, including ceilings and floors, in survival, creative and while
  flying. Entities outside can walk back in, like vanilla.
- Block placing, breaking, pistons, fluids, explosions, mob spawning, block-entity ticking, nether portals,
  spawn and respawn points all use the 3D shape.
- Border damage and the red warning screen use the real 3D distance.
- Vanilla `/worldborder` commands keep working. With no shapes and a square outline, the mod changes nothing
  at all.

**Tools**
- **Structured commands**: one group per thing you change (`shape`, `classic`, `protection`, `formula`,
  `settings`) and one verb per action (`add`, `move`, `resize`, `rotate`, `scale`, `reshape`, `mode`, `protect`,
  `order`, …), so every command says exactly what it does.
- A full **editor** in Minecraft style: press **0** or run `/worldborder gui`. Layers, every property, the
  formula, settings, and an orange **live preview** of what you're editing before you apply it.
- **Protection**: unbreakable border walls for PvP maps, or whole areas that work like adventure mode,
  chosen per area.
- **Per-world settings**: projectiles stopping at the border, hidden borders, fade distance, the inside
  warning.

**Compat**
- **Canary** (Lithium port): full collision, block-entity and portal support.
- **Xaero's World Map** with **Xaero World Border**: exact outlines of your shapes on the map.
- **Air Tactical Arsenal**: shading and border line on its route and targeting maps follow your shapes.
- Every other mod that reads the border's corners gets the rectangle around your shapes.

## How it works

Each shape is a **signed distance field**: a function that tells how far a point is from its surface.
Combining shapes is cheap arithmetic: union is `min`, intersection is `max`, subtraction is `max(a, -b)`.
The result is evaluated at block centers and cached per 16x16x16 section, so the border costs about as much as a
block lookup. During an animation, only blocks inside the moving shapes skip the cache.

The mod hooks the vanilla `WorldBorder` itself. Every place vanilla asks "is this inside the border?" or "how
far is the border?" gets the 3D answer, and the few vanilla spots that read the square's corners directly
(spawn search, nether portal placement, the 2D distance) get targeted fixes. That's why other mods that use the
border work with it too, without knowing it exists.

The border is drawn with vanilla's forcefield texture on the actual block faces of the shape, merged into
large tiles and faded with distance like vanilla's walls.

## Requirements

- Minecraft 1.20.1
- Forge 47 or newer (built and tested on 47.4.10)
- Java 17
- Installed on **both** the server and every client. Clients without it can't join a server that has it.

No other mods are needed.

## Quick start

Stand where you want the arena and run:

```
/worldborder shape add arena sphere 40
```

That's it: the border is now a sphere of radius 40 around you, still limited by the classic border. Then:

```
/worldborder shape add pit cylinder 6 10 at ~ ~-8 ~ mode subtract
/worldborder shape resize arena to 60 over 30
/worldborder gui
```

Cut a pit into it, grow the sphere over 30 seconds, and open the editor. Remove every shape and the classic
border is back exactly as it was:

```
/worldborder shape clear
```

Want a round world instead of a square one, no shapes needed?

```
/worldborder classic outline circle
```

All commands are in **[COMMANDS.md](COMMANDS.md)**.

## Shapes

| Shape | Sizes | Map outline |
|-------|-------|-------------|
| `box` | width, height, depth, or two corners with `from <pos> to <pos>` | rectangle |
| `sphere` | radius | equator circle |
| `cylinder` | radius, height | circle |
| `cone` | radius, height (base at the bottom, tip at the top) | base circle |
| `torus` | radius (to the middle of the tube), thickness (tube radius) | ring |

Every shape also has:

- **position**: its center. Whole numbers mean the center of that block, so `0 64 0` is `0.5 64.5 0.5`;
  decimals are used exactly; `~` works as usual; `here` is the block you stand in.
- **rotation**: yaw, pitch and roll in degrees, same directions as the F3 screen (yaw 90 faces west).
- **scale**: one factor for all axes or one per axis, applied after rotation's local axes.
- **mode**: how it combines with the layers above it.

**Block snapping.** A block is inside a shape if its center is. Boxes are exact: `from 0 0 0 to 4 4 4` is
125 blocks. Curved shapes get a half-block margin so their flat sides are proper patches instead of one block
sticking out.

## Layers and modes

Shapes are stacked like layers in an image editor, top to bottom:

| Mode | Symbol | Meaning |
|------|--------|---------|
| **add** | `+` | the space inside this shape becomes playable |
| **subtract** | `-` | cuts this shape out of everything above it |
| **intersect** | `&` | keeps only what overlaps with this shape |

The first layer starts the volume (a first **subtract** starts from "everything" and cuts from it). Order
matters: `arena + pit(subtract) + tower` is not the same as `arena + tower + pit(subtract)`. Reorder with
`/worldborder shape order <name> up|down|top|bottom`, or with the arrows in the editor.

From the layers the mod builds a **formula**, shown in `/worldborder shape list` and at the bottom of the editor:

```
border & (arena - pit | tower)
```

For anything layers can't express, set your own formula (see [COMMANDS.md](COMMANDS.md#formula-advanced-combining)).
`/worldborder formula auto` goes back to the layers.

## The classic border

The vanilla border stays part of the border. In formulas it's called `border`.

- **No shapes**: the classic border alone. With the square outline, that's pure vanilla.
- **Shapes, classic on** (default): your shapes are also limited by it, `border & (...)`.
- **Shapes, classic off**: only your shapes count.

`/worldborder set`, `center`, `add`, `damage` and `warning` work as always and move or resize the classic
layer, animation included. Removing the last shape brings the plain classic border back at once.

### Classic outlines

Vanilla's border is a square column from the bottom of the world to the top. CSG WorldBorder lets that same
column take other outlines, without any shapes:

```
/worldborder classic outline circle
```

| Outline | Fits the vanilla square like this |
|---------|-----------------------------------|
| `square` | the vanilla border itself (default) |
| `circle` | diameter = the border size |
| `hexagon` | corners touch the east and west sides, flat edges face north and south |
| `octagon` | regular, its straight sides touch all four sides of the square |
| `diamond` | the square turned 45°, its corners touch the middle of each side |
| `triangle` | equilateral, pointing north, corners on the circle of the border size; its center is the border center |
| `star` | five points, one pointing north, tips on the circle of the border size |

Every outline fits inside the vanilla square, so `/worldborder set 1000` makes a 1000-block circle,
`/worldborder set 100 600` shrinks it over ten minutes, and `/worldborder center` moves it. Damage, the red
warning, collision, block rules and map mods all follow the outline. The outline is saved with the world, per
dimension, and stays when you add or remove shapes.

## Animation

Add `over <seconds>` to any change and it plays out smoothly instead of jumping:

```
/worldborder shape add zone sphere 300 at 0 70 0
/worldborder shape resize zone to 40 over 600
/worldborder shape rotate gate by 90 0 0 over 5
/worldborder shape move lift by 0 20 0 over 20
```

`move`, `resize`, `rotate`, `scale` and `reshape` all take `over`, and each one starts from wherever the shape
is right now. Animations keep running through restarts, and players who join mid-animation see it at the right
point. While shapes move, the forcefield turns red, like vanilla's shrinking border.

## Battle royale and timed borders

Shrinking and growing over time is built in, in two ways, and both are made for automation.

**1. The classic border, any outline.** Vanilla's own timed resize works with every outline:

```
/worldborder classic outline circle
/worldborder set 1000
/worldborder set 100 600
```

The last line shrinks the circle from 1000 to 100 blocks across over ten minutes. Vanilla runs the timing: it's
based on real time, survives restarts, and `/worldborder get` gives the current size at any moment.

**2. A shape, with `over`.** Any shape can move and resize at the same time, so the zone can drift toward a new
center while it shrinks, as in most battle royale games:

```
/worldborder shape add zone cylinder 500 400 at 0 64 0
/worldborder shape move zone to 300 64 -200 over 300
/worldborder shape resize zone to 150 over 300
```

Commands sent in the same tick with the same `over` play as one smooth animation. Use a sphere instead of a
cylinder and the zone shrinks from above and below too.

**How smooth is it?** Each tick, the border is worked out again from the exact current size, so the movement
is continuous, with no jumps between phases. Like the rest of the mod it's **block-snapped**: walls, collision
and block rules advance one block at a time as the edge crosses block centers. A border shrinking 900 blocks in
ten minutes moves less than a block per second, so players see a steady creep. Damage and the red warning
use the real distance, without snapping. While the border moves, the forcefield turns red like vanilla's, and
the map outlines in Xaero's World Map update as it goes.

**Automating it with a datapack.** Everything is plain commands, so `schedule` does the timing. A complete
four-phase game, in `data/br/functions/` of a datapack:

`start.mcfunction`
```
worldborder shape clear
worldborder classic outline circle
worldborder center 0 0
worldborder set 1200
worldborder damage buffer 0
worldborder damage amount 1
worldborder warning distance 15
worldborder settings set blockProjectiles true
tellraw @a {"text":"The zone closes in 5 minutes","color":"gold"}
schedule function br:phase1 300s
```

`phase1.mcfunction`
```
tellraw @a {"text":"The zone is closing!","color":"red"}
worldborder set 600 240
schedule function br:phase2 360s
```

`phase2.mcfunction`
```
tellraw @a {"text":"The zone is closing!","color":"red"}
worldborder set 250 180
schedule function br:phase3 300s
```

`phase3.mcfunction`
```
tellraw @a {"text":"Final zone","color":"dark_red"}
worldborder set 20 120
```

Start a round with `/function br:start`, stop it with `/schedule clear br:phase1` (and the others) and
`/worldborder set 1200`.

**A zone that moves to a random spot.** Shapes go where you tell them, including to an entity: spread a marker
with vanilla `spreadplayers`, then move the zone onto it.

```
summon marker 0 64 0 {Tags:["br_next"]}
spreadplayers 0 0 0 200 false @e[tag=br_next]
execute at @e[tag=br_next,limit=1] run worldborder shape move zone to ~ 64 ~ over 240
worldborder shape resize zone to 120 over 240
kill @e[tag=br_next]
```

Each `/worldborder` command also returns a result for `execute store`: `1` when it worked, `0` when it
didn't, and `shape list` returns the number of shapes.

## The editor (GUI)

Press **0** (change it in Controls, *CSG WorldBorder*) or run `/worldborder gui`. The world stays visible
in the middle while you edit.

- **Left**: the **Classic border** toggle and its **Outline**, the layer list with colored mode badges, and
  **New**, **Delete** (asks *Sure?* first), **▲** and **▼**.
- **Right**: everything about the selected shape: name, shape type, mode, position (with **Here**), sizes,
  rotation, scale, **Animate** seconds and **Protect** (none, edge or inside, for the shape's whole area).
  **Apply** sends it, **Revert** throws your edit away.
- **Orange preview**: the selected shape is outlined in orange in the world, and follows your typing before
  you press Apply.
- **Coordinates** in the editor are exact: `60` is the block corner, `60.5` the block center. **Here** fills
  in the center of the block you stand in. (Commands are different: there a whole number means the center.)
- **Bottom**: the formula (gold when custom), **Set** and **Auto**, and the server's answer to your last
  change.
- **Settings** (top right): every [setting](#settings) as toggles and sliders, each explained on hover, and
  **Remember map blocks**.

Players without operator permission (level 2) see the editor read-only. The server checks every edit again.

## Settings

Saved per world in `world/serverconfig/csgworldborder-server.toml`, changed with `/worldborder settings set`, in
the editor's Settings page, or by editing the file and running `/worldborder settings reload`. Every change
reaches all players at once.

| Setting | Default | Range | What it does |
|---------|---------|-------|--------------|
| `protectEdge` | `false` | | the edge of the whole border can't be broken or built on, except in creative |
| `protectThickness` | `1` | 1-8 | how many layers in from the border are protected, for `protectEdge` and `edge` areas |
| `protectWalls` | `true` | | protect the layers along the sides |
| `protectFloor` | `true` | | protect the layers above the bottom |
| `protectCeiling` | `true` | | protect the layers below the top |
| `protectMapBlocksOnly` | `false` | | blocks placed by survival and adventure players are never protected |
| `insideWarning` | `true` | | red screen near the border from inside; off still shows it outside |
| `blockProjectiles` | `false` | | arrows, tridents, snowballs, pearls and fireballs stop at the border |
| `showBorder` | `true` | | draw the forcefield; off keeps the border working but invisible |
| `borderFadeDistance` | `60` | 5-100 | how far the forcefield is visible, in % of render distance |

A modpack can ship `defaultconfigs/csgworldborder-server.toml`: every new world starts with it.

## Protection

Turn parts of the map into something survival players can't change: the walls of an arena, or a whole lobby
that works like adventure mode. Creative players and commands are never blocked.

### Two levels

| Level | Protects |
|-------|----------|
| `edge` | the blocks along the inside of the border, `protectThickness` deep, on the sides you enabled |
| `inside` | every block of the area, like adventure mode |

Both block the same things, only in different places:

| Blocked | Allowed |
|---------|---------|
| breaking (no crack animation either) | creative players |
| placing blocks, pouring and scooping buckets | `/setblock`, `/fill`, `/clone` and other commands |
| explosions (protected blocks survive, others don't) | opening doors, chests, buttons and levers |
| tools changing blocks: axes stripping, hoes tilling, shovels making paths, bone meal | placing blocks against a protected wall from outside it |
| flint and steel, fire charges, item frames, paintings, armor stands, end crystals | walking, fighting, eating, using items in the air |
| pistons pushing or pulling into or out of it | mines, claymores, C4, deployers and other items that place entities |
| withers, the Ender Dragon, ravagers, endermen | using items on protected blocks that don't change them (target markers, radios, launchers) |
| fire burning it, water and lava washing it away | |
| weapons breaking blocks directly (see [Combat mods](#combat-mods)) | |

The fire, decoration and crystal items come from the item tag `csgworldborder:blocked_on_protected`. Add any
modded item that changes blocks to it with a datapack, and it's blocked on protected blocks too.

### Whole border: one setting

`protectEdge` protects the edge of the **entire** border, for every shape and the classic border alone:

```
/worldborder shape add arena box from -30 60 -30 to 30 90 30
/worldborder settings set protectEdge true
/worldborder settings set protectThickness 2
```

Build the walls in creative along the border; survival players face solid walls, floor and ceiling.

### Per area: choose what's protected

Each **area** gets its own protection, set by naming any shape in it:

```
/worldborder shape protect lobby inside
/worldborder shape protect arena edge
/worldborder shape protect mine none
```

**What's an area?** Shapes in **add** mode that touch or overlap form one area. A lobby made of a box and a
dome overlapping it is one area; a separate arena far away is another. Name any shape of an area and the setting
goes to all of them:

```
/worldborder shape add lobby box 40 12 40 at 0 70 0
/worldborder shape add dome sphere 25 at 0 76 0
/worldborder shape add arena box 60 20 60 at 300 70 0
/worldborder shape protect dome inside
```

`lobby` and `dome` overlap, so the whole lobby is protected inside; `arena` is untouched.
`/worldborder shape list` shows each area's protection and its shapes.

- **Protected wins.** When shapes that were apart start to touch, by moving or growing, they become one
  area, and if any of them was protected, the joined area is too.
- **Subtract and intersect shapes** don't make areas: they only carve the add shapes, and protection follows
  the add shape the block is in.
- **Edge protection** in an area uses the same `protectThickness`, `protectWalls`, `protectFloor` and
  `protectCeiling` settings as `protectEdge`.
- `protectEdge` and per-area protection add up: a block is protected if either one protects it.
- When a shape grows, the old edge becomes ordinary inside blocks and the new edge is protected: protection
  always follows the current border.

In the editor, the **Protect** button next to *Animate* sets the selected shape's area.

### Map blocks and player blocks

Protection follows the border, so when a border shrinks or an area grows, blocks that were outside the protected
zone end up inside it. In a survival PvP match that would turn a player's quick cover into an unbreakable wall.
`protectMapBlocksOnly` fixes that:

```
/worldborder settings set protectMapBlocksOnly true
```

- Every block a **survival or adventure** player places from then on is remembered as a **player block**.
  Player blocks are never protected: anyone can break them, explosions destroy them, wherever the border goes.
- Everything else is a **map block** and is protected as usual: blocks built in creative, with commands or
  structures, and everything that was there before the setting was turned on.
- A player block that's broken, blown up, or replaced by another block is forgotten.
- Players still can't place blocks *into* a protected spot; player blocks only get there when the border moves
  onto them.

When survival players build part of the map, or when one match ends and the next starts, turn everything into
map blocks again:

```
/worldborder protection remember all
/worldborder protection remember arena
```

The first works for the whole dimension, the second only for the area `arena` belongs to. The Settings screen of
the editor has a **Remember map blocks** button for the first. `/worldborder protection` shows every area's
protection and how many player blocks are remembered.

The list is saved in the world (`world/data/csgworldborder_player_blocks.dat`, and the same name in each dimension's
`data` folder) and sent to players, so
breaking a player block looks normal on their screen.

### Combat mods

Protection was designed for PvP maps, and combat mods were taken into account, with **Superb Warfare** and
**MH Warfare** as the examples it was worked out on:

- **Explosions** from any mod that uses Minecraft's explosion event (rockets, shells, bombs, C4, mines, craters)
  destroy everything around them except protected blocks.
- **Weapons that break blocks directly**, without an explosion (demolition charges, cannon shells, bullets
  breaking glass, vehicles crushing blocks), stop at protected blocks.
- **Mines, claymores, charges, deployers** and other items that place an entity work anywhere, protected or not.
  Target markers, radios, launchers and other items that only aim at a block keep working on protected blocks.
- **Tools from mods** that strip, till or flatten blocks the vanilla way follow the same rules as vanilla tools.

A mod that breaks blocks in its own way, outside these paths, can still get around protection. When it's an item
used on a block, adding the item to the `csgworldborder:blocked_on_protected` tag with a datapack stops it.

## Maps and other mods

Map mods are 2D, so every shape is shown as the outline of its **widest horizontal slice**: a sphere is its
equator circle, a cone its base, a torus a ring, a lying cylinder a long rectangle. Layers combine those
outlines with the same modes, so a pit subtracted inside a dome shows as a hole even when it's underground.

- **Xaero's World Map** with **Xaero World Border**: the exact outlines, in its own red line style.
  Zoomed far out, they're traced on a coarser grid so the map stays smooth.
- **Air Tactical Arsenal**: route and targeting maps shade everything outside your outlines and draw the
  red edge line along them.
- **Any other mod** that reads the border's four corners gets the rectangle around your outlines instead of
  the square. Remove all shapes and the classic corners come back.

## What follows the 3D border

| Vanilla behaviour | With shapes |
|-------------------|-------------|
| block placing and breaking, bucket use | only inside the 3D volume |
| entity collision | walls, floors and ceilings are solid from inside |
| border damage, red warning screen | real 3D distance |
| mob spawning, block-entity and random ticking | only inside |
| pistons, dragon egg teleports | only inside |
| world spawn, player spawn and respawn | moved to the nearest standable spot inside |
| nether portals | found and created only where the whole frame fits inside |
| vanilla `/worldborder` | still works and drives the classic layer |

## Where data is saved

Everything about a world's border lives **in that world**: copy a world to another server, and its borders and
settings come with it.

| What | Where |
|------|-------|
| shapes, modes, protection, order, formula, outline (Overworld) | `world/data/csgworldborder.dat` |
| same, Nether | `world/DIM-1/data/csgworldborder.dat` |
| same, End | `world/DIM1/data/csgworldborder.dat` |
| player blocks, per dimension | `csgworldborder_player_blocks.dat` next to the above |
| settings | `world/serverconfig/csgworldborder-server.toml` |
| GUI key | each player's own `options.txt` |

## Mod compat

All compat is optional and switches itself on only when the other mod is installed.

- **Canary**: Canary replaces vanilla's entity collision with its own, which never asked the border for the 3D
  walls. CSG WorldBorder hands Canary the real wall shapes, resets Canary's cached "is this block entity
  inside?" flags whenever the shapes change, and makes its portal search always check the 3D border.
  A built-in safety check also stops movement through the border if any other mod skips collision.
- **Xaero World Border**: exact shape outlines on Xaero's World Map.
- **Air Tactical Arsenal**: map shading and border line follow the shapes; waypoint clamping and map size use
  the rectangle around them.

Every other mod that reads the border's corners gets the rectangle around your shapes, and everything that asks
vanilla whether a block or an entity is inside the border gets the 3D answer without knowing the mod exists.

## API for other mods

`com.lildorito.csgworldborder.api.CsgWorldBorderApi`, works on both sides:

| Method | Returns |
|--------|---------|
| `isActive(level)` | whether the 3D border is active in this dimension (shapes, or a non-square outline) |
| `classicOutline(level)` | the classic border's outline: square, circle, hexagon, octagon, diamond, triangle or star |
| `isInside(level, pos)` | whether a block is inside the 3D border |
| `isOnMap(level, x, z)` | whether a column is inside the 2D map outline |
| `mapBounds(level)` | the rectangle around the map outline (or the classic square) |
| `mapOutline(level, minX, minZ, maxX, maxZ)` | outline segments in that area, ready to draw |
| `nearestInside(level, x, y, z)` | the nearest block inside the border |
| `revision(level)` | a number that changes whenever the shape changes, for caching |

## Build

```bat
.\gradlew.bat build
```

Output: `build\libs\csgworldborder-1.0.0.jar`. A dev client runs with `.\gradlew.bat runClient` (also
`runServer`).

**Tests.** An in-game GameTest suite covers the 3D logic, collision, commands, layers, animation, portals,
spawn, classic outlines, edge and area protection, map blocks, projectiles and the map outline. It's kept out of
the release jar:

```bat
.\gradlew.bat -PcsgTest runGameTestServer
```

**Testing with other mods.** Put their jars in a `compat\` folder and add `-PcsgCompat` to any run task; they
are loaded into the dev environment. The folder is ignored by git and never shipped.

## Project layout

```
src/main/java/com/lildorito/csgworldborder
├── CsgWorldBorder.java        entry point, @Mod("csgworldborder")
├── csg/                       the geometry: primitives, transforms, layers, formula,
│                              block-snapped volume, cache, distance, meshing, map outline
├── border/                    saved data, settings, edits, collision, protection, player blocks, projectiles
├── command/                   /worldborder shape, classic, protection, formula, settings, gui
├── network/                   sync, settings, editor packets
├── api/                       public API for other mods
├── client/                    editor, settings screen, border renderer, key binding
│   └── compat/                Xaero World Border and Air Tactical Arsenal map bridges
└── mixin/                     WorldBorder, Entity, Level, ServerPlayer, PortalForcer, protection hooks
    ├── client/                border rendering, warning screen, map mod hooks
    └── compat/                Canary, block-breaking hooks for combat mods
src/gametest/                  in-game tests, built only with -PcsgTest
src/main/resources
├── assets/csgworldborder/     key binding names
├── data/csgworldborder/       the blocked_on_protected item tag
├── csgworldborder.mixins.json mixin config (compat hooks load only if their mod is present)
└── META-INF/                  mods.toml, access transformer
```

## Limits

- Up to **256** shapes per dimension.
- Sizes from **0.01** to **60,000,000** blocks, scale from **0.001** to **10,000**, positions within
  **±30,000,000**.
- Animations up to **1,000,000** seconds (about 11.5 days).
- The border is block-snapped: no half blocks, by design.
- Map mods see one 2D outline per shape (its widest slice), not the full 3D volume.

## Credits

- **lilDorito**: idea, design and the mod.
- **Mojang**: the forcefield texture, used from the game itself.
- **Canary**, **Xaero's World Map**, **Xaero World Border** and **Air Tactical Arsenal** belong to their
  authors; CSG WorldBorder only talks to them when they're installed and includes none of their code.
- Forge, Mixin and other libraries stay under their own licenses.

## License

**All Rights Reserved.** See [LICENSE.txt](LICENSE.txt). The LICENSE file is what counts; short version:

- personal use and use on your own servers: allowed
- re-uploading or mirroring it on its own, outside a modpack: not allowed
- modifying, forking or decompiling to republish: not allowed
- including it in modpacks and mod bundles, on any platform: allowed, with the jar unmodified and the mod
  credited
- commercial use: only with written permission from lilDorito
