# SHARP

Hold-to-lock targeting prototype for Minecraft, built as a Fabric mod for **Minecraft 26.3**. Client-side only, for **single-player practice**.

Hold a key. It picks the practice entity closest to your crosshair, outlines its real hitbox in colours that tell you what you can hit, prints a short status line above your hotbar, and can optionally steer your camera onto it. Let go and everything stops on the same frame.

## Status

| Part | State |
| --- | --- |
| Logic: activation, target selection, aim point, camera maths, hitbox geometry, colours, status text, config and its migration | **261 unit tests**, all passing, run outside the game. Injected-bug (mutation) checks on the newest logic are listed under [Tests](#tests). |
| The game-facing code (`src/client`) | Compiles against Minecraft 26.3 and **runs in the real game**. A client game test launches Minecraft on a virtual screen, plays the feature through and checks 71 things. See [Tests](#tests) for what it covers and what it cannot. |
| The jar | `./gradlew build` produces `build/libs/sharp-0.2.0.jar`. |

Not tested in the real game, only in unit tests: **raised shields**, **elytra / swimming box shrink**, and **fake-player bodies** from other mods (a mannequin is tested, and it has the same box). Details under [Known limits](#known-limits).

## Multiplayer: not supported on purpose

This mod only runs in a world your own game hosts, and never once that world is opened to LAN. There is no setting that changes that.

A lock-on that outlines players and turns your camera onto them is an aimbot with an ESP on any server, and it breaks the rules of basically every one. The point of this project is practice against dummies, bots and mannequins in your own world. Everything else on the request list (range, speed, colours, reach, player-shaped targets) is in, inside that gate.

## Build

You need **JDK 25** and an internet connection. The first build downloads Minecraft and the mappings.

```bash
./gradlew build
```

The mod jar is `build/libs/sharp-0.2.0.jar`. Ignore `sharp-0.2.0-sources.jar`. `build` also runs all the unit tests, and fails if any of them fail.

Install it for normal play: put `sharp-0.2.0.jar` in your `mods` folder next to Fabric API. It needs:

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API 0.163.0+26.3

Other commands:

```bash
./gradlew test                  # unit tests only, fast
./gradlew runClient             # a development client with the mod loaded
./gradlew runClientGameTest     # plays the feature in the real game on a virtual screen (see Tests)
```

## Using it

**1. Make a practice entity.** Only entities marked as practice entities can ever be targeted. Name one so its name **starts with `sharp`** (name tag, or a command), or put it in a scoreboard team called exactly `sharp`.

```
/summon minecraft:armor_stand ~ ~ ~ {CustomName:"sharp dummy"}
/summon minecraft:mannequin ~ ~ ~ {CustomName:"sharp mannequin"}

/team add sharp
/team join sharp @e[type=minecraft:villager,limit=1,sort=nearest]
```

Any living entity works: armor stands, mobs, mannequins, and player-shaped bodies (bots from a fake-player mod, if they are named `sharp...`). Each is outlined with its own real hitbox.

**2. Open a single-player world** and hold the lock key.

| Key (default) | Action |
| --- | --- |
| `G` (hold) | Lock the target nearest your crosshair. Active only while held. |
| `H` | Toggle camera tracking on or off. Starts off. |
| `J` | Toggle the debug panel. |
| `K` | Cycle the status text: auto, minimal, off. |

All four are under **Controls > SHARP** and can be rebound there.

### What the colours mean

| Colour | Meaning |
| --- | --- |
| **Blue** | The main target, the one the lock is on. Thicker while the camera is following it. |
| **Yellow** | Another practice entity in range. |
| **Red** | Something the weapon in your main hand can hit from where you stand. Another entity: its outline is red. The main target: its outline stays blue and its fill and aim cross turn red. |
| **Purple** | The main target is covered by another hitbox, or you are standing inside it. |
| **Cyan** | The aim cross, when the target holds a shield up. |

Reach is not a number the mod guesses. It asks the game whether the item in your hand could hit that box from where you stand, so a spear, a sword and bare hands each give their own answer.

### The text above the hotbar

While the key is held, a short coloured status shows in the lower middle of the screen, in the same colours as the outlines. It does not stay loud: by default it is **full for the first 3 seconds of each hold, then shrinks to one dim line** with no backing strip.

```
first 3 seconds                                  after that (one dim line)

LOCKED  sharp dummy  13.1 m                      LOCKED  13.1 m
out of reach | camera off | +1 in range | aim feet
TARGET   IN RANGE   IN REACH   COVERED
```

The state word is the part that matters, and it is coloured:

| Word | Colour | Meaning |
| --- | --- | --- |
| `LOCKED` | blue | Locked, the held weapon cannot reach it yet. |
| `IN REACH` | red | The held weapon can hit it. |
| `COVERED` / `INSIDE` | purple | Another hitbox is in the way, or you are inside it. |
| `SHIELD UP` | cyan | It holds a shield up. |
| `NO TARGET` / `SHARP OFF` | orange | Nothing to lock, or it cannot run here. The full version says what is missing or why. |

Four styles, set with `statusStyle` or by pressing `K` in game (the key cycles auto, minimal, off):

| Style | What you get |
| --- | --- |
| `auto` (default) | Full text for `statusFullSeconds` (3) after the key goes down, then one dim line. |
| `minimal` | The one dim line, always. |
| `full` | The full text for as long as you hold the key. Only reachable from the config. |
| `off` | No text at all. The outlines and colours are untouched. With the text off, a closed gate or an empty lock is silent, so press `J` and read the `gate:` and `target:` lines if holding the key seems to do nothing. |

### Where it aims

It tries the spots in this order and takes the first one that you can actually hit:

1. **The feet.** Just above the bottom edge, so a little tracking error still lands inside the box.
2. **A lowered centre** (`aimHeight`, 0.3 of the box height by default).
3. The middle, then the upper body.

Heights are a fraction of the box's *current* height, so when a target's box shrinks (elytra, swimming, crawling) the point stays inside the box and the lock holds. A spot is skipped when a block hides it, or when another entity's hitbox would be hit first. Spots to the left and right are tried too, so a target half hidden behind another one is aimed at where it sticks out.

### Shields

With a shield raised, the aim point moves to the **back** of the target. Read this part carefully:

Vanilla decides whether a shield blocks your hit from **where you stand**, not from where you aim. So aiming at the back does nothing for your damage by itself. What matters is that you are on the back half of the shielder. That is why the status row says either *shield blocks you, get behind it* or *shield up, you are behind it*. The camera follows the back point, the text tells you what you need to do with your feet.

### Standing inside the target

If your eye is inside the target's box, it is shown purple, locks regardless of where you look, and the camera **holds still** (aiming at a point a hand's width away would whip the view around).

## What happens when you hold it

- **Selection.** Among practice entities inside `range` and the `fovDegrees` cone (64 blocks and 140 degrees by default), it picks the one closest to your crosshair. Distance only breaks near-ties. Entities behind blocks are skipped (`requireLineOfSight`).
- **Stable lock.** It stays on that target for as long as you hold the key. It only switches when the target is gone, dead, out of range, out of the cone, or hidden. A better placed rival never steals it.
- **Camera (optional, off until you press `H`).** It eases your view onto the target: fast, never overshooting, with a speed limit, an acceleration limit and a per-frame limit. It backs off when you move the mouse yourself. In the real game it closes a 27 degree gap in 8 ticks (0.4 s).
- **Release.** Let go and the lock, outline and camera motion all stop on that same frame.

## When it switches itself off

It turns off at once, clears the target, and **will not turn back on while you keep holding the key**. You have to release and press again. That applies to:

- releasing the key
- the window losing focus (alt-tab)
- any screen opening (inventory, chat, pause)
- dying or respawning
- changing world or dimension
- disconnecting, or leaving the world
- the practice gate closing (below)

## The practice-only gate

This is enforced in code, not left to a setting. The feature only runs when all of these hold:

1. You are in a **single-player world** (the game's own built-in server).
2. That world is **not opened to LAN**.
3. `enabled` is true in the config.
4. The entity is a **practice entity** (name starts with the marker, or team name equals it).

The config can change the marker text, but there is no option to remove the requirement. On any other server it does nothing, and the text says why (`SHARP OFF: not a single-player world`).

## Configuration

`config/sharp.json` in your game folder, created on first launch. It is re-read each time you join a world. Bad values are clamped into range. A file that is not valid JSON is kept as `sharp.json.broken` and replaced with defaults.

**Upgrading from the first release:** its file is detected, copied to `sharp.json.v1.bak`, and replaced with the new defaults. Only your on/off switches, the practice marker, camera tracking and the debug flags are carried over. Old tuning values were just the old defaults written to disk, and keeping them would hide every improvement. The log says when this happens.

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `enabled` | `true` | | Master switch. |
| `practiceMarker` | `"sharp"` | up to 32 chars | Name prefix or team name that marks a practice entity. |
| `range` | `64.0` | 1 to 128 | Max distance in blocks, measured to the hitbox surface. |
| `fovDegrees` | `140.0` | 1 to 360 | Full cone around the crosshair. 140 means 70 degrees each side. 360 turns the limit off. |
| `aimHeight` | `0.3` | 0 to 1 | Aim spot when the feet are not wanted or not visible: 0 feet, 0.5 centre, 1 top. Fraction of the current box height. |
| `aimFeetFirst` | `true` | | Try the feet first. |
| `aimShieldRear` | `true` | | Aim at the back of a target with a shield up. |
| `aimAvoidCover` | `true` | | Aim at a part that another hitbox does not cover. |
| `distanceWeight` | `0.05` | 0 to 5 | Degrees of penalty per block of distance. Higher favours near targets. |
| `retainMarginPercent` | `0.0` | 0 to 50 | Extra slack for keeping a lock at the edge of range or cone. 0 is strict. |
| `requireLineOfSight` | `true` | | Skip targets behind blocks. |
| `cameraTrackingEnabled` | `false` | | Camera follows the target while the key is held. |
| `cameraSensitivity` | `2.0` | 0.1 to 5 | Higher closes the gap faster. |
| `cameraSmoothing` | `0.2` | 0 to 1 | 0 is snappiest, 1 is gentlest. |
| `maxRotationSpeed` | `540.0` | 10 to 1440 | Hard cap, degrees per second. |
| `maxRotationAcceleration` | `6000.0` | 50 to 20000 | Hard cap on how fast the speed can change, degrees per second squared. |
| `maxStepPerFrame` | `45.0` | 0.1 to 90 | Hard cap on rotation in one frame, degrees. |
| `deadzoneDegrees` | `0.1` | 0 to 5 | Camera holds still when this close. Stops jitter. |
| `rampSeconds` | `0.08` | 0 to 3 | Fade-in time when tracking starts. |
| `pitchMin` / `pitchMax` | `-85` / `85` | -90 to 90 | Pitch range the assist may aim at. Negative is up. |
| `yieldToManualInput` | `true` | | Back off while you move the mouse. |
| `yieldRate` | `120.0` | 10 to 2000 | Your turn speed (degrees per second) at which it has fully backed off. |
| `hitboxEnabled` | `true` | | Draw the outlines. |
| `hitboxRenderDistance` | `96.0` | 1 to 256 | Boxes farther than this from the camera are not drawn. |
| `hitboxFill` / `hitboxFillAlpha` | `true` / `0.12` | 0 to 1 | Faint fill inside the main outline. |
| `hitboxAimMarker` | `true` | | Cross at the aim point. |
| `hitboxLineWidth` | `2.0` | 0.5 to 8 | Outline width in rough pixels. Kept constant on screen as you move away. |
| `hitboxShowOthers` | `true` | | Outline the other practice entities in range. |
| `hitboxMaxOthers` | `16` | 0 to 32 | Cap on those extra outlines. |
| `hitboxMainColor` | `"#4D8DFF"` | | `#RRGGBB` or `#AARRGGBB`. Blue. |
| `hitboxRangeColor` | `"#FFD93D"` | | Yellow. |
| `hitboxAttackColor` | `"#FF5B54"` | | Red. |
| `hitboxCoverColor` | `"#B36BFF"` | | Purple. |
| `hitboxShieldColor` | `"#4DE8E0"` | | Cyan. |
| `statusStyle` | `"auto"` | `auto`, `full`, `minimal`, `off` | How loud the text above the hotbar is. Anything unknown becomes `auto`. |
| `statusFullSeconds` | `3.0` | 0 to 30 | How long `auto` keeps the full text after the key goes down. 0 is quiet from the start. |
| `debugOverlay` | `false` | | Show the debug panel. |
| `debugLogging` | `false` | | Log activation, target and deactivation events to the game log. |

## Debug panel

Press `J`. It shows activation state and the last reason it turned off, the practice gate, the key state and whether the next press would activate, the locked target with distance and **angular error**, whether it is in reach, where it aims and what is in the way, how many others are around, and the camera's own errors, step, speed, yield factor and fade-in.

## How it is built

```
src/main/java/dev/sharp          environment-neutral, no Minecraft imports, all unit tested
  core/math                      V3, Box (with ray entry), AimMath (Minecraft yaw/pitch conventions)
  core/activation                ActivationTracker   hold-to-lock state machine
  core/targeting                 TargetSelector, TargetLock, AimPointPicker, Exposure, settings, LineOfSight
  core/camera                    AimController       smoothing, limits, yielding
  core/render                    hitbox geometry, colour policy, interpolation, mesh builder
  core/hud                       CueBuilder, CueStyle   the coloured status text and how loud it is
  core/debug                     DebugSnapshot, DebugFormatter
  config                         SharpConfig, ConfigStore (JSON, validated, versioned)
  controller                     SharpController     runs one frame of all of the above
src/client/java/dev/sharp/client  the only code that touches Minecraft
  SharpClient                    wiring to Fabric events
  InputHandler                   key bindings
  GameAccess                     reading game state: entities, reach, ray tests, turning the camera
  CameraController               applies a camera step
  SharpRuntime                   per-frame and per-tick glue, error containment
  HitboxRenderer, BoxMesh        submits the boxes to draw
  CueOverlay                     the status text above the hotbar (full block or one dim line)
  DebugOverlay                   the debug panel
src/gametest                     the client game test that plays it in the real game
```

Target selection, aim point, camera control, rendering, input, and settings are separate classes with no dependencies on each other beyond plain data. You can run selection with the camera off, or test the camera on its own with a fixed point.

Frame flow:

```
key, focus, screen, player, world ──> ActivationTracker ──off──> clear everything
                                           │ on
     practice entities ──> TargetSelector/TargetLock ──> target
                                           │
   target + every pickable entity + blocks ──> AimPointPicker ──> aim point, exposure
                                           │
      hitbox scene + status text <──┴──> (camera tracking on) AimController ──> player view
```

Two hooks keep it from getting stuck on: the full work happens once per rendered frame, and a lighter check runs once per game tick even on screens with no world. Connection and level-change events also force a reset. If anything in the mod throws, it switches itself off for the session and logs the error instead of crashing the game.

## Tests

**Unit tests:** `./gradlew test` runs 261 JUnit tests with no game running.

**Real-game test:** `./gradlew runClientGameTest` starts Minecraft with the mod on a virtual screen (on Linux: `xvfb-run -a ./gradlew runClientGameTest`), builds a stone platform with dummies, holds the key, and checks what the game and the mod actually do. It covers:

- lock on the right dummy (not the closer unnamed one), colours blue / yellow / red / purple read from what the renderer is given
- the aim lands in the feet zone; the camera reaches the target in well under a second, in one direction, without overshoot or wobble
- a bare hand does not reach 3.5 blocks and an iron spear does, and the colour and text change with it
- a mannequin is outlined with exactly its real 0.6 by 1.8 box
- an entity standing in the way turns the target purple and the text to COVERED
- standing inside the target is detected and the camera holds still
- the status text: full at the start, one dim line after a few seconds, always minimal, off; and the "nothing to lock" and "world open to LAN" messages
- release, screens opening, and the LAN gate switch it off

It writes `sharp-gametest-report.txt` and screenshots into `build/run/clientGameTest/`.

**What neither can check:** a raised shield (no vanilla mob raises one, so the shield maths is unit-tested only, and the call that reads it is compile-checked), elytra, bots from fake-player mods, and how the camera *feels* on a real GPU and a real mouse.

**Checking the tests themselves:** the targeting, aim-point, status-text and config logic were also run against deliberately injected bugs (a flipped sign, a dropped check, a wrong colour). A test suite that cannot fail proves nothing, so each injected bug has to make at least one test fail.

## Known limits

- **Shields:** aiming at the back is what you asked for, and it is implemented, but see [Shields](#shields): it does not make the hit land by itself.
- **Covered detection is geometric.** It treats every pickable entity as a plain box, the way the game's own crosshair pick does. It does not know about entities that ignore the pick or about anything modded that changes picking.
- **Reach comes from the game for the item in your main hand.** Enchantments and attribute changes that the game applies to reach are included because the game answers; the mod adds nothing of its own.
- **Server-side checks:** this is a client-side practice tool. Nothing here changes what a server would accept, and it does not run on one.

## If something does not work

- **Holding the key does nothing.** Read the text above the hotbar: it names the reason (`SHARP OFF: ...` or `NO TARGET`). Then press `J` and read the `gate:` and `target:` lines. The usual causes are a world open to LAN, a mob whose name does not start with `sharp`, or nothing within range and in front of you.
- **"Incompatible mod set" at launch.** Your Minecraft is not 26.3. `fabric.mod.json` and `gradle.properties` pin 26.3 because the code is written against that API.
- **Outline is invisible or draws through walls.** It uses `RenderTypes.debugFilledBox()` in the `BEFORE_TRANSLUCENT_TERRAIN` event. Try `AFTER_TRANSLUCENT_FEATURES` in `HitboxRenderer.register`.
- **Camera feels off.** Lower `cameraSensitivity`, raise `cameraSmoothing`, or lower `maxRotationSpeed`. The old gentle feel is `1.0 / 0.5 / 240 / 1800 / 20 / 0.15 / 0.25`.
