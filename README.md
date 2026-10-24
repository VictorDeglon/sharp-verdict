# SHARP

Hold-to-lock targeting for Minecraft, built as a Fabric mod for **Minecraft 26.3**. Client-side only. It works in single-player worlds, on LAN and on servers.

Hold a key. It picks a target (players first, then armor stands, then animals, the nearest of each), outlines its real hitbox in colours that tell you what you can hit, prints a short status line above your hotbar, and can optionally steer your camera onto it. It stays on that one target until you let go and press again. Let go and everything stops on the same frame.

## Status

| Part | State |
| --- | --- |
| Logic: activation, target selection, aim point, camera maths, hitbox geometry, colours, status text, config and its migration | **280 unit tests**, all passing, run outside the game. Injected-bug (mutation) checks on the newest logic are listed under [Tests](#tests). |
| The game-facing code (`src/client`) | Compiles against Minecraft 26.3, and the mod loads in the real game. A client game test launches Minecraft on a virtual screen and plays the feature through; see [Tests](#tests) for what it covers and what it cannot. **The 0.3.0 changes (no gate, tiers, no-switch lock, client tick counter) were built and unit tested, but the game test was not re-run for them:** it could not open a graphics context in the sandbox they were built in. Run `./gradlew runClientGameTest` before trusting them. |
| The jar | `./gradlew build` produces `build/libs/sharp-0.3.0.jar`. |

Not tested in the real game, only in unit tests: **raised shields**, **elytra / swimming box shrink**, and **fake-player bodies** from other mods (a mannequin is tested, and it has the same box). Details under [Known limits](#known-limits).

## Multiplayer

It runs everywhere: single-player, a world opened to LAN, and any server. Nothing checks where you are, and the camera follows the target in all of them.

Three things are different on a server:

- **What you see is a little behind what is real.** Other players' positions arrive over the network, so the lock follows the position your client has, not where the server has them. The outline and the aim point are interpolated against a client tick counter, not the server's clock, so they stay smooth when the server resends the time.
- **The first release's rule is gone.** Early versions refused to run outside a single-player world. This one does not.
- **Servers have rules about this.** A lock-on that turns your camera onto other players is aim assist, and most public servers prohibit it and many detect it. The mod does not hide itself in any way. Use it where it is allowed: your own server, a friend's, a practice server.

## Build

You need **JDK 25** and an internet connection. The first build downloads Minecraft and the mappings.

```bash
./gradlew build
```

The mod jar is `build/libs/sharp-0.3.0.jar`. Ignore `sharp-0.3.0-sources.jar`. `build` also runs all the unit tests, and fails if any of them fail.

Install it for normal play: put `sharp-0.3.0.jar` in your `mods` folder next to Fabric API. It needs:

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

**1. Find something to lock.** Players, armor stands, animals and every other living thing in range are targets. Nothing needs naming. If you would rather limit it to some entities, turn on `requireMarker` in the config: then only entities whose name **starts with `sharp`** (name tag, or a command) or that are in a scoreboard team called exactly `sharp` can be targeted.

```
/summon minecraft:armor_stand ~ ~ ~ {CustomName:"sharp dummy"}
/summon minecraft:mannequin ~ ~ ~ {CustomName:"sharp mannequin"}

/team add sharp
/team join sharp @e[type=minecraft:villager,limit=1,sort=nearest]
```

Spectators, dead or dying entities, and you yourself are never targets.

**2. Hold the lock key** in any world or on any server.

| Key (default) | Action |
| --- | --- |
| `G` (hold) | Lock the best target. Active only while held. |
| `H` | Toggle camera tracking on or off. Starts off. |
| `J` | Toggle the debug panel. |
| `K` | Cycle the status text: auto, minimal, off. |

All four are under **Controls > SHARP** and can be rebound there.

### What the colours mean

| Colour | Meaning |
| --- | --- |
| **Blue** | The main target, the one the lock is on. Thicker while the camera is following it. |
| **Yellow** | Another target in range. |
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

- **Selection.** Among the entities inside `range` and the `fovDegrees` cone (64 blocks and 140 degrees by default), it picks the best one by the rules in [Who gets picked](#who-gets-picked). Entities behind blocks are skipped (`requireLineOfSight`).
- **It does not change its mind.** The first target it takes is the target for the whole hold. If that target dies, leaves range or the cone, or is hidden behind a block, the lock goes quiet (`NO TARGET`) and waits. It never moves to another entity, even a better one, and it picks the same one up again if it becomes valid. To lock something else, let go of the key and press it again. If nothing was valid when you pressed, the first valid target during that hold is the one it takes.
- **Camera (optional, off until you press `H`).** It eases your view onto the target: fast, never overshooting, with a speed limit, an acceleration limit and a per-frame limit. It backs off when you move the mouse yourself. In the real game it closes a 27 degree gap in 8 ticks (0.4 s).
- **Release.** Let go and the lock, outline and camera motion all stop on that same frame.

### Who gets picked

Every candidate gets a rank in blocks, and the lowest rank wins:

```
rank = distance + handicap(kind) + 0.1 * (degrees away from the crosshair)
```

| Kind | Handicap (default) | Setting |
| --- | --- | --- |
| Players, mannequins, fake-player bodies | 0 | |
| Armor stands | 3 blocks | `armorStandPenalty` |
| Animals and everything else that lives | 6 blocks | `otherPenalty` |

So a player beats an animal unless the animal is more than 6 blocks closer. A sheep 2 blocks away loses to a player 2.5 blocks away, and wins against a player 15 blocks away. Between two of the same kind the nearer one wins, and the crosshair only breaks near-ties (30 degrees off costs 3 blocks). Range, cone and line of sight decide who is eligible at all, before any of this: a player behind you never beats a sheep in front of you when the cone is 140 degrees.

Set `priorityTiers` to `false` for the earlier behaviour: the entity closest to the *crosshair* wins and distance only breaks ties.

## When it switches itself off

It turns off at once, clears the target, and **will not turn back on while you keep holding the key**. You have to release and press again. That applies to:

- releasing the key
- the window losing focus (alt-tab)
- any screen opening (inventory, chat, pause)
- dying or respawning
- changing world or dimension
- disconnecting, or leaving the world

## Who can be targeted

A living entity can be targeted when all of these hold:

1. `enabled` is true in the config.
2. It is within `range` and inside the `fovDegrees` cone, and (with `requireLineOfSight`) not hidden behind blocks.
3. It is not you, not dead or dying, and not a spectator.
4. If `requireMarker` is on: its name starts with the marker, or its team is named exactly that.

## Configuration

`config/sharp.json` in your game folder, created on first launch. It is re-read each time you join a world. Bad values are clamped into range. A file that is not valid JSON is kept as `sharp.json.broken` and replaced with defaults.

**Upgrading from the first release:** its file is detected, copied to `sharp.json.v1.bak`, and replaced with the new defaults. Only your on/off switches, the practice marker, camera tracking and the debug flags are carried over, and `requireMarker` is switched on so it keeps targeting only marked entities. A file from 0.2.0 has no `requireMarker` key, so it gets the new default (off) and every entity is a target. Old tuning values were just the old defaults written to disk, and keeping them would hide every improvement. The log says when this happens.

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `enabled` | `true` | | Master switch. |
| `requireMarker` | `false` | | Only entities carrying the marker can be targeted. Off: every living entity counts. |
| `practiceMarker` | `"sharp"` | up to 32 chars | Name prefix or team name the marker is. Only used with `requireMarker`. |
| `range` | `64.0` | 1 to 128 | Max distance in blocks, measured to the hitbox surface. |
| `fovDegrees` | `140.0` | 1 to 360 | Full cone around the crosshair. 140 means 70 degrees each side. 360 turns the limit off. |
| `aimHeight` | `0.3` | 0 to 1 | Aim spot when the feet are not wanted or not visible: 0 feet, 0.5 centre, 1 top. Fraction of the current box height. |
| `aimFeetFirst` | `true` | | Try the feet first. |
| `aimShieldRear` | `true` | | Aim at the back of a target with a shield up. |
| `aimAvoidCover` | `true` | | Aim at a part that another hitbox does not cover. |
| `priorityTiers` | `true` | | Rank by distance with a handicap per kind (players, then armor stands, then the rest). Off: the crosshair decides. |
| `armorStandPenalty` | `3.0` | 0 to 64 | Blocks added to an armor stand's distance when ranking. |
| `otherPenalty` | `6.0` | 0 to 64 | The same for animals and every other entity. Never below `armorStandPenalty`. |
| `distanceWeight` | `0.05` | 0 to 5 | Only with `priorityTiers` off: degrees of penalty per block of distance. Higher favours near targets. |
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
| `hitboxShowOthers` | `true` | | Outline the other targets in range. |
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

Press `J`. It shows activation state and the last reason it turned off, whether the feature is open, the key state and whether the next press would activate, the locked target with distance and **angular error**, whether it is in reach, where it aims and what is in the way, how many others are around, and the camera's own errors, step, speed, yield factor and fade-in.

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
     candidates ──> TargetSelector/TargetLock ──> target
                                           │
   target + every pickable entity + blocks ──> AimPointPicker ──> aim point, exposure
                                           │
      hitbox scene + status text <──┴──> (camera tracking on) AimController ──> player view
```

Two hooks keep it from getting stuck on: the full work happens once per rendered frame, and a lighter check runs once per game tick even on screens with no world. Connection and level-change events also force a reset. If anything in the mod throws, it switches itself off for the session and logs the error instead of crashing the game.

## Tests

**Unit tests:** `./gradlew test` runs 280 JUnit tests with no game running.

**Real-game test:** `./gradlew runClientGameTest` starts Minecraft with the mod on a virtual screen (on Linux: `xvfb-run -a ./gradlew runClientGameTest`), builds a stone platform with dummies, holds the key, and checks what the game and the mod actually do. It covers:

- with `requireMarker` on, lock on the right dummy (not the closer unnamed one), colours blue / yellow / red / purple read from what the renderer is given
- the aim lands in the feet zone; the camera reaches the target in well under a second, in one direction, without overshoot or wobble
- a bare hand does not reach 3.5 blocks and an iron spear does, and the colour and text change with it
- a mannequin is outlined with exactly its real 0.6 by 1.8 box
- an entity standing in the way turns the target purple and the text to COVERED
- standing inside the target is detected and the camera holds still
- the status text: full at the start, one dim line after a few seconds, always minimal, off; and the "nothing to lock" and "world open to LAN" messages
- priority tiers: a player-shaped body is locked before a nearer sheep, and when it is killed mid-hold the lock does not jump to the sheep; the next press takes the sheep
- release and screens opening switch it off, and a world opened to LAN still activates

It writes `sharp-gametest-report.txt` and screenshots into `build/run/clientGameTest/`.

**What neither can check:** a raised shield (no vanilla mob raises one, so the shield maths is unit-tested only, and the call that reads it is compile-checked), elytra, bots from fake-player mods, and how the camera *feels* on a real GPU and a real mouse. Nothing here has run on a real multiplayer server: the tick-counter fix for the once-a-second hitch comes from reading how the server's time packets reach the client, and a LAN world is the closest the game test gets.

**Checking the tests themselves:** the targeting, aim-point, status-text and config logic were also run against deliberately injected bugs (a flipped sign, a dropped check, a wrong colour). For 0.3.0 two more were injected: ignoring the tier handicap broke 4 tests, and letting the lock switch targets broke 7. A test suite that cannot fail proves nothing, so each injected bug has to make at least one test fail.

## Known limits

- **Shields:** aiming at the back is what you asked for, and it is implemented, but see [Shields](#shields): it does not make the hit land by itself.
- **Covered detection is geometric.** It treats every pickable entity as a plain box, the way the game's own crosshair pick does. It does not know about entities that ignore the pick or about anything modded that changes picking.
- **Reach comes from the game for the item in your main hand.** Enchantments and attribute changes that the game applies to reach are included because the game answers; the mod adds nothing of its own.
- **Servers:** it only moves your camera and draws on your screen. A server sees a player who turns smoothly onto targets, and many have anti-cheat that flags exactly that. See [Multiplayer](#multiplayer).
- **Network lag:** on a server the target is where your client last heard it was, which is slightly behind where it really is.

## If something does not work

- **Holding the key does nothing.** Read the text above the hotbar: it names the reason (`SHARP OFF: ...` or `NO TARGET`). Then press `J` and read the `gate:` and `target:` lines. The usual causes are nothing within range and in front of you, `requireMarker` being on with nothing named `sharp...`, or `enabled` being false.
- **It stopped following the target and says `NO TARGET` while the key is down.** The target died, left the cone or range, or went behind a block, and it does not pick another one mid-hold. Release and press again, or turn back toward the same target and it resumes.
- **"Incompatible mod set" at launch.** Your Minecraft is not 26.3. `fabric.mod.json` and `gradle.properties` pin 26.3 because the code is written against that API.
- **Outline is invisible or draws through walls.** It uses `RenderTypes.debugFilledBox()` in the `BEFORE_TRANSLUCENT_TERRAIN` event. Try `AFTER_TRANSLUCENT_FEATURES` in `HitboxRenderer.register`.
- **Camera feels off.** Lower `cameraSensitivity`, raise `cameraSmoothing`, or lower `maxRotationSpeed`. The old gentle feel is `1.0 / 0.5 / 240 / 1800 / 20 / 0.15 / 0.25`.
