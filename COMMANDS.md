# CSG WorldBorder: Commands

<p align="center">
  <img src="https://img.shields.io/badge/Permission-Level%202-C0392B?style=for-the-badge" alt="Permission level 2" />
  <img src="https://img.shields.io/badge/Root-%2Fworldborder-3C8527?style=for-the-badge" alt="/worldborder" />
</p>

**Every command of CSG WorldBorder**, organized the way you think about the border: pick **what** you're
changing, then **what to do** with it.

All commands live under vanilla's `/worldborder` and need **permission level 2** (operators, command blocks,
the server console). Each one acts on the **dimension you're in**. Tab completion knows every verb, shape
name, mode, outline and setting.

Back to the [README](README.md).

## Contents

- [The command tree](#the-command-tree)
- [Rules that apply everywhere](#rules-that-apply-everywhere)
- [shape: your shapes](#shape-your-shapes)
  - [add](#add) · [remove](#remove) · [move](#move) · [resize](#resize) · [rotate](#rotate) · [scale](#scale)
  - [reshape](#reshape) · [mode](#mode) · [protect](#protect) · [rename](#rename) · [order](#order) · [info, list, clear](#info-list-clear)
- [protection: map blocks](#protection-map-blocks)
- [classic: the vanilla border](#classic-the-vanilla-border)
- [formula: advanced combining](#formula-advanced-combining)
- [settings: per-world options](#settings-per-world-options)
- [gui: the editor](#gui-the-editor)
- [Vanilla commands](#vanilla-commands)
- [Recipes](#recipes)
- [Messages and errors](#messages-and-errors)

## The command tree

```
/worldborder
├── set · add · center · damage · warning · get        vanilla, unchanged: drives the classic border
│
├── shape                                               your shapes  (bare: prints this list)
│   ├── add      <name> <type> <sizes> [at <pos>|here] [mode <mode>]
│   ├── add      <name> box from <pos> to <pos> [mode <mode>]
│   ├── remove   <name>
│   ├── move     <name> to <pos>|here                    [over <seconds>]
│   ├── move     <name> by <dx> <dy> <dz>                [over <seconds>]
│   ├── resize   <name> to <sizes>                       [over <seconds>]
│   ├── resize   <name> times <factor>                   [over <seconds>]
│   ├── rotate   <name> to <yaw> <pitch> <roll>          [over <seconds>]
│   ├── rotate   <name> by <yaw> <pitch> <roll>          [over <seconds>]
│   ├── scale    <name> to <factor> | <x> <y> <z>        [over <seconds>]
│   ├── reshape  <name> <type> <sizes>                   [over <seconds>]
│   ├── reshape  <name> box from <pos> to <pos>          [over <seconds>]
│   ├── mode     <name> add|subtract|intersect
│   ├── protect  <name> none|edge|inside
│   ├── rename   <name> <newName>
│   ├── order    <name> up|down|top|bottom
│   ├── info     <name>
│   ├── list
│   └── clear
│
├── protection                                          protection overview  (bare: status)
│   └── remember all|<name>
│
├── classic                                             the vanilla border  (bare: status)
│   ├── on · off
│   └── outline [square|circle|hexagon|octagon|diamond|triangle|star]
│
├── formula                                             advanced combining  (bare: show)
│   ├── set <formula>
│   └── auto
│
├── settings                                            per-world options  (bare: list all)
│   ├── get   <key>
│   ├── set   <key> <value>
│   ├── reset <key>|all
│   └── reload
│
└── gui                                                 open the editor (same as the 0 key)
```

The pattern is always the same: **`/worldborder <what> <verb> <name> <values>`**. The verb is the second word,
so you can read any command out loud and know what it does: *shape move arena to 0 64 0*, *shape rotate gate
by 90 0 0 over 5*, *classic outline circle*, *settings set protectEdge true*.

## Rules that apply everywhere

**Absolute and relative.** Verbs that change a value take a small word that says how:

| Word | Meaning | Example |
|------|---------|---------|
| `to` | set it to exactly this | `move arena to 0 64 0` |
| `by` | add this to the current value | `move arena by 0 10 0` |
| `times` | multiply the current value | `resize arena times 2` |

**Animation.** Everything that changes a shape's geometry (`move`, `resize`, `rotate`, `scale`, `reshape`) can
end with **`over <seconds>`**. The change then plays out smoothly from wherever the shape is right now.
Animations keep going across restarts and are synced to players who join halfway.

**Positions.**

- **Whole numbers mean block centers.** `0 64 0` is the center of block `0 64 0`, so a sphere there is
  symmetrical around that block. Decimals are used as they are: `0.0 64.0 0.0` is the corner between eight
  blocks.
- **`~` and `^`** work like in `/tp`: `~ ~10 ~` is ten blocks above you.
- **`here`** is the block you stand in. A shape added without `at` goes **here** too.

**Names** are one word: letters, digits and `_`. Case matters. `border` and `all` are taken by the formula.

**Numbers.** Sizes are in blocks and can be decimal; **radius** is from the center, not the diameter. Angles
are degrees. Seconds are whole numbers, `0` is instant.

## shape: your shapes

### add

Creates a new shape. The name must be free.

```
/worldborder shape add <name> sphere   <radius>                  [at <pos>|here] [mode <mode>]
/worldborder shape add <name> cylinder <radius> <height>         [at <pos>|here] [mode <mode>]
/worldborder shape add <name> cone     <radius> <height>         [at <pos>|here] [mode <mode>]
/worldborder shape add <name> torus    <radius> <thickness>      [at <pos>|here] [mode <mode>]
/worldborder shape add <name> box      <width> <height> <depth>  [at <pos>|here] [mode <mode>]
/worldborder shape add <name> box from <pos> to <pos>            [mode <mode>]
```

| Type | Sizes | Notes |
|------|-------|-------|
| `sphere` | radius | |
| `cylinder` | radius, height | upright; `height` is the full height, centered on the position |
| `cone` | radius, height | base at the bottom, tip at the top |
| `torus` | radius, thickness | a ring lying flat; radius to the middle of the tube, thickness is the tube's radius |
| `box` | width (X), height (Y), depth (Z) | full lengths, centered on the position |
| `box from … to …` | two corner blocks | both corners are included: `from 0 0 0 to 4 4 4` is 5×5×5 |

`mode` defaults to `add`. To rotate or scale a new shape, follow up with `rotate` or `scale`.

```
/worldborder shape add arena sphere 40
/worldborder shape add arena sphere 40 at 0 80 0
/worldborder shape add tower cylinder 12 120 at 100 100 0
/worldborder shape add spire cone 20 60 at here
/worldborder shape add track torus 80 6 at 0 64 0
/worldborder shape add room box 30 12 30 at 0 70 0
/worldborder shape add room box from -15 64 -15 to 15 76 15
/worldborder shape add pit cylinder 6 20 at ~ ~-10 ~ mode subtract
```

### remove

```
/worldborder shape remove <name>
```

Removing the last shape brings back the plain classic border.

### move

Moves the shape's center.

```
/worldborder shape move <name> to <pos>                 [over <seconds>]
/worldborder shape move <name> to here                  [over <seconds>]
/worldborder shape move <name> by <dx> <dy> <dz>        [over <seconds>]
```

```
/worldborder shape move arena to 0 100 0
/worldborder shape move arena to here
/worldborder shape move lift by 0 20 0 over 20
```

`by` uses plain offsets (no block-center rounding), so `by 0 20 0` moves exactly 20 blocks up.

### resize

Changes the shape's own sizes, the same ones `add` takes.

```
/worldborder shape resize <name> to <a> [<b> [<c>]]     [over <seconds>]
/worldborder shape resize <name> times <factor>         [over <seconds>]
```

`to` takes the sizes in the type's order: radius for a sphere; radius and height for a cylinder or cone;
radius and thickness for a torus; width, height and depth for a box. Give fewer and the rest stay as they are.
`times` multiplies all of them, keeping the proportions.

```
/worldborder shape resize arena to 60
/worldborder shape resize tower to 12 200
/worldborder shape resize room to 40 12 40
/worldborder shape resize zone to 30 over 1800
/worldborder shape resize arena times 0.5 over 60
```

### rotate

```
/worldborder shape rotate <name> to <yaw> <pitch> <roll>   [over <seconds>]
/worldborder shape rotate <name> by <yaw> <pitch> <roll>   [over <seconds>]
```

| Angle | Turns around | Same as on F3 |
|-------|--------------|---------------|
| yaw | the vertical axis | yes: `90` turns toward west |
| pitch | the shape's own sideways axis | yes: `90` tips the front down |
| roll | the shape's own front axis | |

Angles go from `-36000` to `36000`, so `by 3600 0 0 over 600` spins a shape ten times in ten minutes.

```
/worldborder shape rotate tunnel to 0 90 0
/worldborder shape rotate ring to 45 90 0
/worldborder shape rotate spike to 0 180 0
/worldborder shape rotate gate by 90 0 0 over 5
```

The first lays a cylinder on its side along Z, the second stands a ring up and turns it diagonal, the third
flips a cone so its tip points down, the fourth swings a gate a quarter turn.

### scale

Stretches the shape along its own axes, on top of its sizes.

```
/worldborder shape scale <name> to <factor>             [over <seconds>]
/worldborder shape scale <name> to <x> <y> <z>          [over <seconds>]
```

One number scales evenly. Range `0.001` to `10000`. `to 1` is the shape's natural size.

```
/worldborder shape scale egg to 1 1.5 1
/worldborder shape scale lens to 1 0.25 1
```

### reshape

Turns a shape into another type, keeping its name, layer, mode, position, rotation and scale.

```
/worldborder shape reshape <name> <type> <sizes>        [over <seconds>]
/worldborder shape reshape <name> box from <pos> to <pos>   [over <seconds>]
```

```
/worldborder shape reshape arena cylinder 40 30
/worldborder shape reshape room box from 0 60 0 to 20 80 20 over 10
```

A shape can only animate into the **same** type; a sphere turning into a box changes instantly, and the reply
says so. `box from … to …` also moves the box to its new corners.

### mode

```
/worldborder shape mode <name> add|subtract|intersect
```

| Mode | Effect |
|------|--------|
| `add` | its space becomes playable |
| `subtract` | its space is cut out of everything above it |
| `intersect` | only space inside it stays |

### protect

Sets the protection of the **area** the shape belongs to.

```
/worldborder shape protect <name> none|edge|inside
```

| Level | Survival players can't change |
|-------|-------------------------------|
| `none` | nothing is protected (default) |
| `edge` | the blocks along the inside of the border, `protectThickness` deep, on the sides enabled in settings |
| `inside` | any block in the area, like adventure mode |

Protected means no breaking, placing, buckets, tools changing blocks (axes, hoes, shovels, bone meal), fire
starters, item frames, paintings, armor stands, explosions, pistons, fire, mob griefing, or water and lava washing blocks away.
Doors, buttons, levers and chests still work, and so do items that place entities, like mines, claymores and C4.
Creative players and commands are never blocked. With `protectMapBlocksOnly` on, blocks placed by survival
players stay breakable; see [protection](#protection-map-blocks).

**Areas.** Shapes in `add` mode that touch or overlap form one area. Naming any of them sets the whole area:

```
/worldborder shape add lobby box 40 12 40 at 0 70 0
/worldborder shape add dome sphere 25 at 0 76 0
/worldborder shape protect dome inside
```

```
Area lobby + dome is protected inside
```

- When separate shapes start to touch (by `move`, `resize` and so on), they join one area. If any of them was
  protected, the joined area is: **protected wins**.
- `subtract` and `intersect` shapes don't form areas, so `protect` refuses them; protect the add shape they cut
  into. Switching a protected shape to `subtract` or `intersect` drops its protection.
- `shape list` and `shape info` show each area's protection and its shapes.
- The `protectEdge` setting protects the edge of the **whole** border on top of this, for maps that want every
  wall unbreakable.

### rename

```
/worldborder shape rename <name> <newName>
```

Also renames the shape inside a custom formula.

### order

Shapes are layers, combined from top to bottom.

```
/worldborder shape order <name> up|down|top|bottom
```

### info, list, clear

```
/worldborder shape info <name>
/worldborder shape list
/worldborder shape clear
```

```
arena (add, sphere radius 40) at 0.5 80.5 0.5, layer 1 of 3
```

```
Shapes, top to bottom:
1. arena (add, sphere radius 40) at 0.5 80.5 0.5
2. pit (subtract, cylinder radius 6 height 20) at 0.5 70.5 0.5 (animating)
3. tower (add, cylinder radius 12 height 120) at 100.5 100.5 0.5
Classic border: on, square
Formula: border & (arena - pit | tower)
```

Rotation and scale are shown when they're not the default. A moving shape shows where it's heading. `clear`
removes every shape in the dimension and brings back the plain classic border.

`/worldborder shape` on its own prints the list of shape commands.

## protection: map blocks

```
/worldborder protection
/worldborder protection remember all
/worldborder protection remember <name>
```

Bare `protection` shows every area's protection, the `protectEdge` and `protectMapBlocksOnly` settings, and how
many player blocks are remembered in this dimension:

```
Protection in this dimension:
Whole border edge (protectEdge): off
Area lobby + dome: inside
Area arena: edge
Map blocks only (protectMapBlocksOnly): on
Player blocks remembered: 214
```

With `protectMapBlocksOnly` on, every block a survival or adventure player places is remembered as a **player
block** and is never protected, so a shrinking border or a growing area can't turn a player's cover into an
unbreakable wall. Everything else is a **map block**.

`remember` turns blocks back into map blocks by forgetting the player blocks: `all` for the whole dimension, or a
shape's name for the area it belongs to. Use it after survival players helped build the map, or between matches.
The Settings screen of the editor has a **Remember map blocks** button that does `remember all`.

```
/worldborder settings set protectMapBlocksOnly true
/worldborder protection remember all
```

## classic: the vanilla border

The vanilla border stays part of every border. In formulas it's called `border`.

```
/worldborder classic
/worldborder classic on
/worldborder classic off
/worldborder classic outline
/worldborder classic outline square|circle|hexagon|octagon|diamond|triangle|star
```

| State | Border is |
|-------|-----------|
| no shapes | the classic border alone (with the square outline: pure vanilla) |
| shapes, classic **on** (default) | your shapes, cut to the classic border: `border & (…)` |
| shapes, classic **off** | your shapes only |

**Outlines.** The classic border is a column from the bottom of the world to the top. Its outline can be:

| Outline | Fits the vanilla square like this |
|---------|-----------------------------------|
| `square` | the vanilla border itself (default) |
| `circle` | diameter = the border size |
| `hexagon` | corners touch the east and west sides, flat edges face north and south |
| `octagon` | regular, its straight sides touch all four sides of the square |
| `diamond` | the square turned 45°, its corners touch the middle of each side |
| `triangle` | equilateral, pointing north, corners on the circle of the border size; its center is the border center |
| `star` | five points, one pointing north, tips on the circle of the border size |

Vanilla's own commands size and move it: `/worldborder set 1000` makes a 1000-block circle, `/worldborder set 100
600` shrinks it over ten minutes, `/worldborder center 0 0` moves it. The outline is saved per dimension and stays
when shapes come and go. With classic off, the outline only matters again when classic is on or there are no
shapes.

```
/worldborder classic outline circle
/worldborder set 2000
/worldborder set 200 1800
```

## formula: advanced combining

For borders that layers can't describe. A custom formula replaces the layers' order and modes until you go back
to `auto`.

```
/worldborder formula
/worldborder formula set <formula>
/worldborder formula auto
```

| Symbol | Operation | Example |
|--------|-----------|---------|
| `\|` or `+` | union: inside either | `arena \| lobby` |
| `&` | intersection: inside both | `arena & dome` |
| `-` | subtraction: inside the first, not the second | `arena - pit` |
| `^` | exclusive or: inside exactly one | `ring1 ^ ring2` |
| `( )` | grouping | `(a \| b) - c` |
| `border` | the classic border, with its outline | `border & arena` |
| `all` | everything | `all - prison` |

`&` binds tighter than the others, like multiplication: `a | b & c` is `a | (b & c)`. Everything else is read
left to right: `a | b - c` is `(a | b) - c`. When in doubt, use parentheses.

- Every name in a new formula must be an existing shape. Shapes left out of it still exist but don't affect the
  border.
- `classic on`/`off` doesn't apply to a custom formula: write `border &` yourself if you want the classic
  border.
- Renaming a shape updates the formula. A removed shape that the formula still names counts as empty.
- New shapes are not added to a custom formula by themselves; the `add` reply reminds you.

```
/worldborder formula set all - jail
/worldborder formula set (outer - inner) ^ cross
/worldborder formula set border & (spawn | arena1 | arena2 | arena3)
```

## settings: per-world options

```
/worldborder settings
/worldborder settings get <key>
/worldborder settings set <key> <value>
/worldborder settings reset <key>
/worldborder settings reset all
/worldborder settings reload
```

Bare `settings` lists everything. `get` shows one value and what it does. `set` changes it, saves the file and
sends it to all players. `reset` puts one setting, or all of them, back to the default. `reload` reads the file
again after you edit it by hand.

| Key | Values | Default | What it does |
|-----|--------|---------|--------------|
| `protectEdge` | `true` `false` | `false` | the edge of the whole border can't be broken or built on, except in creative |
| `protectThickness` | `1`-`8` | `1` | how many blocks deep the protected edge is, for `protectEdge` and `edge` areas |
| `protectWalls` | `true` `false` | `true` | protect the side walls |
| `protectFloor` | `true` `false` | `true` | protect the floor |
| `protectCeiling` | `true` `false` | `true` | protect the ceiling |
| `protectMapBlocksOnly` | `true` `false` | `false` | blocks placed by survival and adventure players are never protected |
| `insideWarning` | `true` `false` | `true` | red screen near the border while inside; outside always shows it |
| `blockProjectiles` | `true` `false` | `false` | projectiles stop when they hit the border |
| `showBorder` | `true` `false` | `true` | draw the forcefield |
| `borderFadeDistance` | `5`-`100` | `60` | forcefield visible up to this % of render distance |

Settings belong to the **world**, not the server: `world/serverconfig/csgworldborder-server.toml`.

```
/worldborder settings set protectEdge true
/worldborder settings set protectThickness 3
/worldborder settings get insideWarning
/worldborder settings reset borderFadeDistance
/worldborder settings reset all
```

## gui: the editor

```
/worldborder gui
```

Opens the editor for the dimension you're in. Players can also press **0**, rebindable in
*Options › Controls › CSG WorldBorder*. Without permission level 2 it opens read-only. Every edit goes through
the same checks as the commands. See the [README](README.md#the-editor-gui).

## Vanilla commands

All of vanilla's `/worldborder` still works and changes the **classic** border, animations included:

| Command | With CSG WorldBorder |
|---------|----------------------|
| `/worldborder set <size> [time]` | resizes the classic border, in any outline |
| `/worldborder add <size> [time]` | grows or shrinks it |
| `/worldborder center <x> <z>` | moves it |
| `/worldborder damage amount <n>` | damage per block outside, measured in 3D |
| `/worldborder damage buffer <n>` | safe distance outside, measured in 3D |
| `/worldborder warning distance <n>` | red screen distance, measured in 3D |
| `/worldborder warning time <n>` | works as before |
| `/worldborder get` | the classic border's size |

## Recipes

**Round world**
```
/worldborder classic outline circle
/worldborder set 4000
```

**Shrinking battle royale circle**
```
/worldborder classic outline circle
/worldborder set 800
/worldborder set 40 1800
/worldborder settings set blockProjectiles true
```

**Moving battle royale zone**
```
/worldborder shape add zone cylinder 500 400 at 0 64 0
/worldborder shape move zone to 300 64 -200 over 300
/worldborder shape resize zone to 150 over 300
```
Sent together with the same `over`, both play as one animation: the zone drifts and shrinks at once. A full
datapack with phases is in the [README](README.md#battle-royale-and-timed-borders).

**Star world**
```
/worldborder classic outline star
/worldborder set 1500
```

**Shrinking sphere zone**
```
/worldborder shape add zone sphere 400 at 0 64 0
/worldborder shape resize zone to 30 over 1800
```

**Dome arena with a shaft**
```
/worldborder shape add dome sphere 50 at 0 64 0
/worldborder shape add floor box 120 60 120 at 0 34 0 mode subtract
/worldborder shape add shaft cylinder 8 20 at 0 60 0
```
A half-sphere above `y 64` with a shaft going down in the middle. `floor` cuts the bottom half away, `shaft`
adds back because it's below it.

**Protected lobby next to a free-build arena**
```
/worldborder shape add lobby box 40 12 40 at 0 70 0
/worldborder shape add dome sphere 25 at 0 76 0
/worldborder shape add arena box 80 30 80 at 200 75 0
/worldborder shape protect lobby inside
/worldborder shape protect arena edge
```
The lobby works like adventure mode; in the arena, players build and break freely but can't dig through its
walls, floor or ceiling.

**PvP box with unbreakable walls**
```
/worldborder shape add arena box from -40 60 -40 to 40 100 40
/worldborder settings set protectEdge true
/worldborder settings set protectThickness 2
```
Build walls in creative along the border; survival players can't break them, build on them or blow them up.

**Floating island**
```
/worldborder classic off
/worldborder shape add island sphere 60 at 0 120 0
/worldborder shape scale island to 1 0.5 1
/worldborder shape add cave sphere 15 at 0 110 0 mode subtract
```

**Spinning gate**
```
/worldborder shape add gate box 20 20 4 at 0 70 50 mode subtract
/worldborder shape rotate gate by 3600 0 0 over 600
```

**Rising lift shaft**
```
/worldborder shape add lift box 5 6 5 at 0 64 0
/worldborder shape move lift by 0 60 0 over 30
```

**Hub with arenas, by formula**
```
/worldborder shape add hub cylinder 30 40 at 0 70 0
/worldborder shape add north box 60 30 60 at 0 70 -200
/worldborder shape add south box 60 30 60 at 0 70 200
/worldborder shape add bridge box 6 6 400 at 0 70 0
/worldborder formula set border & (hub | north | south | bridge)
```

**Back to vanilla**
```
/worldborder shape clear
/worldborder classic outline square
```

## Messages and errors

| Message | Why |
|---------|-----|
| `No shape named <name>. See /worldborder shape list` | a verb on a shape that doesn't exist |
| `A shape named <name> already exists. Change it with move, resize, …` | `add` with a taken name |
| `Invalid shape name '<name>'…` | a name with other characters, or `border` / `all` |
| `<name> is a subtract shape. Only add shapes make areas…` | `protect` on a subtract or intersect shape |
| `Sizes must be between …`, `Scale must be between …`, `Position is out of range` | see the limits below |
| `… instantly, a shape can't animate into another type` | `reshape … over` into a different type |
| `Invalid formula: … at position <n>` | an unclosed `(`, a missing name, a stray character |
| `Unknown shape(s) in formula: …` | the formula names a shape that doesn't exist |
| `Too many shapes (limit 256)` | 256 per dimension at most |

| Limit | Range |
|-------|-------|
| sizes | `0.01` to `60000000` |
| scale | `0.001` to `10000` |
| position | `±30000000` |
| angles | `±36000` |
| animation | up to `1000000` seconds |
| shapes per dimension | `256` |
