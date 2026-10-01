# WhiteLabelTest

A data-driven vertical shoot 'em up built on [libGDX](https://libgdx.com/) (Java 21, Gradle), with a
JavaFX stage editor. Almost all content is JSON under `assets/data/`: enemies, bullet patterns,
stages, trigger scripts, weapons, balance and sounds. Most tuning never needs a code change.

This document explains how the game is put together. Code comments cover the local "why"; this file
covers the big picture, the data formats and the rules that span several classes.

---

## Contents

1. [Modules and running](#modules-and-running)
2. [Application flow](#application-flow)
3. [The game loop (GameController)](#the-game-loop-gamecontroller)
4. [Stages, sequences and the stage map](#stages-sequences-and-the-stage-map)
5. [The trigger system](#the-trigger-system)
6. [Legacy spawn schedules](#legacy-spawn-schedules)
7. [Enemies](#enemies)
8. [Movement patterns](#movement-patterns)
9. [Firing patterns and bullets](#firing-patterns-and-bullets)
10. [The player](#the-player)
11. [Weapons](#weapons)
12. [Collisions](#collisions)
13. [Scoring, chains, gems and rank](#scoring-chains-gems-and-rank)
14. [Entities, pooling and draw order](#entities-pooling-and-draw-order)
15. [Backgrounds and shaders](#backgrounds-and-shaders)
16. [Effects](#effects)
17. [UI](#ui)
18. [Audio](#audio)
19. [Input](#input)
20. [Replays](#replays)
21. [Debug tools](#debug-tools)
22. [Performance tooling](#performance-tooling)
23. [The stage editor](#the-stage-editor)
24. [Data file reference](#data-file-reference)
25. [Conventions](#conventions)
26. [Gradle reference](#gradle-reference)

---

## Modules and running

| Module   | Contents |
|----------|----------|
| `core`   | The whole game: `whitelabeltest.*` (menus, managers, enemies, player, weapons). |
| `lwjgl3` | Desktop launcher (`Lwjgl3Launcher`): full screen, vsync, 64 OpenAL sources, Quick Play config. |
| `editor` | JavaFX stage editor (`EditorApp`). It depends on `core` and reuses its data classes and pattern math. |

```
gradlew lwjgl3:run              # play
gradlew lwjgl3:run -Pdebug      # play with debug mode enabled (F12 toggles it)
gradlew editor:run              # stage editor
gradlew lwjgl3:jar              # runnable jar in lwjgl3/build/libs
```

Both run tasks use `assets/` as the working directory. All data paths are relative to it, and the
in-game debug editors and the stage editor save straight into the real source files. Saving only
reaches the source tree when the game is launched through `lwjgl3:run`.

Extra `lwjgl3:run` properties (each becomes a `-D` system property; see
[Performance tooling](#performance-tooling) and [Quick Play](#quick-play)):
`-Pdebug`, `-PperfLog=<csv>`, `-PautoReplay=<json>`, `-PautoReplaySeconds=<n>`, `-PjfrFile=<jfr>`,
`-PperfInvincible`, `-PperfNoFeedback`, `-PperfNoHueCycle`, `-PperfNoBossVideo`,
`-PperfFpsCap=<n>`, `-PquickPlay*`.

---

## Application flow

`Main` is a small state machine:

```
START ──► WEAPON_SELECT ──► PLAYING
  │  ├──► OPTIONS ──────────► START
  │  ├──► REPLAY_SELECT ────► PLAYING (watching) ──► START
  │  └──► PLAYING (tutorial) ──► START
```

- **StartScreen**: title signs, then a menu with ARCADE MODE / TUTORIAL / REPLAYS / OPTIONS / EXIT.
  The first key or button press also picks the input device (keyboard or gamepad).
- **WeaponSelectScreen**: picks one of the `WeaponLoadout` pairs (Basic+Thunderbolt, Basic+Orbit,
  Thunderbolt+Orbit). WaveBlast is never a starting weapon.
- **OptionsScreen** (scene2d): key bindings, audio volumes and background shader quality.
- **ReplaySelectScreen**: pick a saved replay to watch.
- The **tutorial** skips weapon select. Its stage sequence supplies a starting loadout.
- **Quick Play** (from the editor) goes straight to PLAYING at a chosen stage and distance.
- `-DautoReplay=<file>` plays a replay without any menus and exits when it finishes.

Each screen polls input directly. A screen seeds its "previous button state" when it opens, so the
button that opened it isn't read as a new press. Menu confirm sounds outlive their screen: the
next screen or `Main` disposes them.

The game draws into a 9 × 12 world-unit play area (`ExtendViewport`), with black side panels for
the HUD. Gameplay is scissored to the play area.

---

## The game loop (GameController)

`GameController` owns one run: player, entities, stage loading, collisions, scoring, the debug menu,
replays and stage-to-stage flow. `update(delta)` runs in this order:

1. Pull the replay frame, if one is playing (a recorded seek is applied instantly).
2. Interstitial video, if one is playing (skipped with SHOOT/RESTART using live input).
3. Score, audio and timer updates; bomb cooldown (with a "ready" sound).
4. `InputManager.update(frame)`, then debug hotkeys. While the debug menu is open, only the menu
   and the pattern previewer run.
5. Record the input frame (unless this is a replay or the tutorial).
6. Disabled windows (weapons, Hyper Attack, bomb), then bomb input. A bomb pressed within
   `BOMB_SAVE_WINDOW` (0.0325 s) after a hit cancels that hit ("panic bomb").
7. Resolve a pending hit; game-over and level-complete prompts.
8. Camera speed scale → background → entities → trigger manager (or legacy scheduler).
9. Text cues; legacy boss-video and music-fade latches.
10. Boss dead → 3 s later, level complete (victory music, bonuses, rank).
11. Collisions: shield reflections, player hit, grazes, powerups, gems, player bullets vs enemies,
    enemy bullets vs enemies, halo dash, Thunderbolt detonation, paired-enemy deaths.

**Player hit** resolution:
- Ignored while `-Dperf.invincible` is set or inside an invincibility window.
- Otherwise the chain breaks. Inside a practice window the section restarts with no life lost.
- With no lives left it's game over.
- Otherwise the player loses a life, plays the death sequence, and a restore powerup drops.

**destroyEnemy** does the following:
- counts the kill (per type and per spawn group);
- cancels the enemy's bullets if it has `bulletCancel`;
- plays its explosion pattern;
- drops its guaranteed powerup and point gems;
- advances every other boss's firing pattern (`SequencedFiringPattern.advance`).

**Stage flow.** A fixed sequence advances on RESTART at level complete. A sequence with a stage map
shows a stage select when there's more than one choice. Each stage is preceded by a random
interstitial video from `interstitials.json`, picked with the seeded RNG, so replays stay
deterministic.

`loadStage` does the following:
- builds a new `ScrollingBackground`;
- creates a `TriggerManager` if the stage has a `triggerFile`, otherwise a legacy `SpawnScheduler`;
- sets the kaleidoscope and mandelbulb distances (by default, 4 and 10 units before the boss);
- sets the hue-cycle period, starts the stage music and preloads cue sounds.

---

## Stages, sequences and the stage map

- `data/stages.json` holds every stage (`StageDefinition`).
- `data/stage_sequences.json` holds named runs (`StageSequenceDefinition`): `id`, `stageIds`, an
  optional `stageMap` and an optional `startingLoadout`.

### StageDefinition fields

| Field | Meaning |
|-------|---------|
| `id`, `name` | Identity. |
| `triggerFile` | The stage's trigger script (every current stage has one). |
| `spawnSchedule` | Legacy time-based schedule, used only when `triggerFile` is null. |
| `music` | Stage music path. |
| `backgroundLayers[]` | `{texture \| textureSequence, scrollSpeed}` from far to near. `scrollSpeed` NaN = default (-1.25). A `textureSequence` relays images inside one layer, and the last image freezes once fully scrolled. |
| `backgroundVideo` | Looping video from the start of the stage. |
| `bossVideo` | Video cut in by a trigger (`triggerBossVideo`). |
| `shaderBackground` | `boxTunnel`, `kaleidoscope` or `mandelbulb`. Takes priority over video and layers. |
| `hueCycleBackground` | Rotates the layers' hue once, ending as the boss video starts. |
| `playerFeedbackBackground` | Video-feedback trail overlay on any background. |
| `kaleidoscopeTransitionTime`, `kaleidoscopeColorFadeDistance`, `kaleidoscopeTransitionDistance`, `mandelbulbDiveDistance` | Shader timing. Null values default relative to the boss trigger. |
| `groundScrollSpeed` | Scroll speed for ground enemies not attached to a layer. |

### Stage map

```json
"stageMap": { "columns": [ ["stage1"], ["stage2", "stage3"], [null, null, null], [null, null, null, null] ] }
```

- The first column is a single node (the start).
- Each node connects to the vertically adjacent nodes in the next column. Each column has one more
  node than the last, so there are always two choices ahead.
- `null` is a locked placeholder and is never offered. The run ends when no stage lies ahead.
- `StageMap` and `StageSelect` are pure data, driven only by recorded input, so they're replay-safe.
  `UIManager.drawStageSelect` draws the hex lattice. See `assets/stage guide.png`.

### Starting loadout

`slotAWeaponId`, `slotBWeaponId`, `weaponLevels {id: level}`, `maxBombs` (0 = player default),
`numBombs`, `numLives`. Weapon ids: `BasicWeapon`, `WaveBlastWeapon`, `OrbitWeapon`, `Thunderbolt`.
The tutorial sequence uses one; other sequences use the WeaponSelectScreen choice.

---

## The trigger system

Package `gamemanagers/trigger`. A stage is a list of **triggers** placed at a **distance** along
the level. `LevelCamera` is a 1D camera that advances at `cameraSpeed` world units per second. A
trigger arms when the camera reaches it, waits for its **conditions**, performs its **action**, and
can hold the camera still as a **gate**.

### Trigger file format

```jsonc
{
  "cameraSpeed": 1.0,
  "triggers": [ { "distance": 12.5, "type": "IceSkull", "x": 4.5, "y": 8, ... } ],
  "practiceCheckpoints":         [ { "start": 25, "end": 40 } ],
  "invincibilityWindows":        [ ... ],
  "weaponsDisabledWindows":      [ ... ],
  "hyperAttackDisabledWindows":  [ ... ],
  "bombDisabledWindows":         [ ... ]
}
```

Windows are `[start, end)` in distance units.

### Per-frame lifecycle

1. `realTime += delta`. This clock never freezes, and text cues use it.
2. Due wave members spawn.
3. If a gate is active, only that gate is re-checked; the camera stays frozen.
4. Otherwise the camera advances. Its collision box is the span swept this frame, so a fast camera
   can't skip a trigger. Each unfired trigger, in distance order:
   - **arms** when `distance - spawnLead` (clamped at 0) falls inside the swept span;
   - is **skipped** if `firstAttemptOnly` / `retryOnly` says so for this attempt;
   - snapshots a **baseline** for each condition (counts are measured from arming);
   - **fires** its action once when every condition is met;
   - if `requireConfirm` is set, waits for SHOOT or RESTART. The first press finishes a typewriter
     reveal; the second dismisses the text cue.
   - An unresolved **gate** becomes the active gate. The camera is clamped to it and the loop stops.
     Clamping (instead of stopping short) lets other triggers at the same distance still arm.

Several triggers can be armed and waiting at once, each with its own baselines.

### Trigger flags

| Flag | Effect |
|------|--------|
| `gate` | Freezes the camera until the trigger resolves. |
| `requireConfirm` | Its text cue stays until the fire button is pressed. |
| `firstAttemptOnly` | Skipped on a practice retry. |
| `retryOnly` | Only runs on a practice retry. |
| `spawnLead` | Arms this many distance units early (the node still shows at `distance`). |
| `enterFromAbove` | See [Entrances](#entrances). |
| `inverseMovement` | Mirrors the movement pattern. |
| `id` | Lets `spawnDestroyed` conditions refer to this spawn. |

### Conditions

| Type | Satisfied when |
|------|----------------|
| `shoot`, `bomb`, `hyperAttack` | That input is newly pressed. |
| `hyperAttackReleased` | Hyper Attack is released. |
| `weaponSwitch` (+`weaponId`) | That weapon is equipped. |
| `moved`, `movedLeft`, `movedRight` | Movement starts (optionally in a direction). |
| `enemiesDestroyed` (+`count`) | Kills since arming. |
| `enemyTypeDestroyed` (+`enemyType`, `count`) | Kills of that type since arming. |
| `gemsCollected` (+`count`) | Gems since arming. |
| `grazed` (+`count`) | Graze **points** since arming (each graze is worth 0.5). |
| `spawnDestroyed` (+`triggerId`) | Every enemy that trigger spawned, including a whole wave, has been **killed**. Despawns and fly-offs never count. An unknown id, or a trigger skipped by a seek, counts as satisfied. |
| unknown or `null` | Satisfied, so a typo can't soft-lock a stage. |

**Post-spawn snapshot.** When a waypoint gem or an enemy spawn fires, the gem and kill counts are
recorded. The next trigger *with conditions* uses that snapshot as its baseline. Otherwise a fast
player could collect or kill before the gate armed and the gate would wait forever. Triggers without
conditions (plain text cues) don't use the snapshot.

### Actions

`fire()` checks these in order and runs the first one set:

`sound` › `spriteTexture` (sprite cue) › `setSpeed` (camera speed) › `text` (text cue) ›
`triggerBossVideo` › `fadeOutMusic` › `music` › `silence` (stop enemies firing) › `despawn` ›
`waypointGem` › `swapWeaponId` › `type` (enemy spawn or wave).

A trigger with no action is valid; a bare gate is one. The editor clears the old action's fields when
the action changes, so a leftover field can't win this dispatch.

`getSpeedScale()` (current camera speed divided by the authored speed) also scales the background
and ground-enemy scroll, so `setSpeed: 0` visibly stops the world.

### Text cues

`TextCue` fields:
- `effect`: static, typewriter or blinking;
- `duration`, and `x`/`y` (or centered);
- `fontSize`, `charsPerSecond` (typewriter), `blinksPerSecond` (blinking);
- `requireConfirm`.

Cues are timed on the never-frozen real clock and drawn with a backdrop. A confirm hint shows the
SHOOT binding for the active device.

### Entrances

`EnemyEntranceMovement` is shared by the game and the editor preview. With `enterFromAbove` and a
`spawnLead`, the enemy spawns off-screen above the play area. It flies down to its authored `(x, y)`
and arrives as the camera reaches the trigger's distance. Travel time is
`min(spawnLead, distance) / cameraSpeed`.

The spawn margin is the sprite's height, which stays inside the off-screen removal tolerance. If the
enemy's own movement is a WaypointPath, there's no separate drop; the path itself curves in from
off-screen. Trigger `x`/`y` is the sprite's bottom-left corner, while WaypointPath works in
sprite-center space.

### Waves

`WaveSpawnPlanner` is shared by the game and the editor. A trigger with a `waveShape` spawns several
copies of its enemy.

**Shapes:**
- `point`;
- `circle`: a full circle divides 360° by the count; a partial arc divides by count − 1;
- `plane`: `waveLines` across by `waveColumns` down;
- `triangle`: apex at the front, `c(c+1)/2` members.

**Orientation** (facing and movement direction): in front, toward the center, toward the player, or
toward the outside. `waveRotation` rotates the layout about the anchor. Positions are never clamped
to the screen.

**Timing:** members spawn at `waveStartDelay + i × interval` on the real clock.

**Movement:**
- `waveKeepFormation` + an authored pattern: each member gets a copy of the pattern with its
  absolute targets shifted by the member's offset. These are synthetic `__waveN` ids, never saved.
  Health-phase patterns are shifted too.
- `waveKeepFormation` without a pattern: every member flies in one shared direction.
- Otherwise each member uses its own pattern or angle.

`waveSpawnLift = max(worldHeight − minSlotY, baseTarget.y − trigger.y)`. One lift is shared by the
whole formation, which keeps its shape as it enters.

### Seeking and practice sections

- `seekTo(distance)`: triggers behind the target are marked fired *without* running (skipped, not
  replayed). Text cues and the active gate are cleared, speed changes are skipped, and the music at
  that distance (`musicAt()`) is restored.
- `reset()`: full restart of the stage.
- **Practice checkpoints:** a hit inside a checkpoint window calls
  `GameController.restartPracticeSection`. No life is lost, and `seekToPracticeRetry(start)` runs.
  Triggers in that window marked `firstAttemptOnly` are skipped and `retryOnly` triggers play; the
  tutorial uses these for its "Let's try that again…" messages. A seek counts triggers at exactly
  the start distance as already passed, so retry triggers must sit after `start`.

### Window semantics

These rules apply to trigger files and legacy schedules alike.

| Window | Effect |
|--------|--------|
| Practice checkpoint | A hit rewinds the section; no life lost. |
| Invincibility | Hits are ignored but enemies **keep firing**. Death i-frames are different: they pause all enemy fire. |
| Weapons / Hyper Attack / bomb disabled | Only the press's effect is blocked. The input is still read, for gates, focus slowdown and replays. |

---

## Legacy spawn schedules

`SpawnScheduler` reads `*_schedule.json`, the older **time-based** format. It's only constructed
for a stage with no `triggerFile`, and no current stage uses it.

It contains:
- `SpawnEvent`s: enemy, silence, despawn, waypoint gem, weapon swap;
- `SoundCue`, `SpriteCue` and `textCues`;
- `GateCue`s (one gate at a time; it freezes `totalTime`, while `realTime` keeps running);
- `backgroundVideoTime`, `musicFadeOutTime`, `scheduleEndTime` (the tutorial's end), `kaleidoscopeTransitionTime` and `groundScrollSpeed`;
- the same five window types, measured in time;
- a file-wide `textCuesRequireConfirm`.

`EnemySpawnOps` holds the spawn, silence, despawn, gem and sprite actions shared by both systems.
`EnemySpawnRegistry` gives firing patterns (`SpawnEnemyFiring`) and the pattern previewer static
access to spawning.

---

## Enemies

Package `enemy`. Enemies are entirely data-driven: `GenericEnemy` (extends `BaseEnemy`) is built
from an `EnemyDefinition` plus the spawning trigger.

`PatternRegistry` holds these files:
- `enemies.json`;
- `movement_patterns/<id>.json` and `firing_patterns/<id>.json`, one pattern per file (file
  name = id), listed from the assets folder or from `assets.txt` inside a packaged jar;
- `bullets.json` and `explosion_patterns.json`.

Its `putX` methods register in-memory edits and synthetic patterns. `PatternFactory` turns
definitions into live `MovementPattern` and `FiringPattern` objects.

### EnemyDefinition

| Group | Fields |
|-------|--------|
| Art | `texture`, `frameCount`, `columns` (0 = one row), `rows`, `frameDuration`, `size` (along the longer axis), `uniformPixelScale`, `animations {name: EnemyAnimationDef}`, `flipWithDirection` (art faces left), spawn and death animation sheets and durations. |
| Combat | `health`, `healthRegenPerSecond`, `score`, `firingPattern`, `weaponSets {key: firingId}`, `bulletTexture`, `explosionPattern`, `bulletCancel`, `hitboxes[]`. |
| Behaviour | `isBoss`, `isGround`, `backgroundLayer` (draw and scroll with that layer), `rotateWithMovement`, `facePlayer`, `inverseMovement`, `showHealthBar`, `targetableByHoming`. |
| Rules | `sealable` (holds fire while the graze halo overlaps it), `ignoreCeasefireZone`, `defiant` (can't be damaged until it has fired once), `damageableByEnemyBullets`, `pairId`. |

**Movement is not part of the definition.** Each spawn sets its own `movementPattern`; null means
stationary.

### Lifecycle

- **ENTERING**: the spawn animation (or a fade-in) over `spawnDuration`. The enemy moves but can't be
  hit or fire.
- **ACTIVE**: normal behaviour.
- **DYING**: the death animation or fade. Paired enemies freeze until their pair is resolved.

**Rules while active:**
- An enemy can't be damaged until its whole box is on screen.
- **Ceasefire zones**: the bottom 1.5 units and the side 0.5 units. An enemy there doesn't fire
  unless `ignoreCeasefireZone` is set.
- While paused, in a ceasefire zone or sealed, the firing pattern doesn't update, so its cooldown
  freezes.
- A hit flashes the enemy red for 0.05 s. The drop shadow falls toward the play-area center.

**Removal** happens when:
- the death animation finishes;
- the enemy self-destructs;
- its movement finishes;
- it leaves the screen bounds (two sprite sizes) after having been on screen once.

### Health phases

`Trigger.healthPhases` belong to the spawn. At each `healthPercent` remaining, the enemy switches
movement, firing, animation and/or `flipWithDirection`. Phases are one-way. If a single hit crosses
several phases, they apply highest first. A new movement starts from the current position.

### Hitboxes

`HitboxDef` rectangles and circles are given as fractions of the sprite size, in sprite-local space,
and rotate with the sprite. With no hitboxes, the whole sprite rectangle is used. `EnemyHitboxes`
does this math for both collisions and the debug overlay.

### Paired enemies

Enemies with the same `pairId` must be killed together. Damage is deferred until all hits in the
frame are applied. If both partners are dying, both are destroyed. Otherwise the first one waits
0.35 s, and if its partner hasn't followed by then, both revive at full health. The tutorial's
"simultaneous takedown" drill uses this.

---

## Movement patterns

Angles are in degrees: 0 = right (+X), 90 = up (+Y), 270 = down toward the player (the default).
The default speed is 0.

| Type | Parameters / behaviour |
|------|------------------------|
| `None` | Doesn't move. |
| `Straight` | `speed`, angle. Never finishes (removed off-screen). |
| `ZigZag` | Zigzags along the angle. Never finishes. |
| `Bounce` | Bounces just inside the screen edges, so it stays damageable. Never finishes. |
| `Seeking` | Chases the player; **finishes (enemy removed)** within `stopDistance` once fully on screen. |
| `MoveToPoint` | `target` (defaults `spawnX, 0`), `stopDistance`. **Finishes (removed)** at the target. |
| `Spline` | Spline descent across the screen. |
| `Sequence` | Sub-patterns one after another by `duration` (default 3 s). |
| `Squadron` | The leader pattern plus a formation offset. |
| `WaypointPath` | MoveToPoint legs on a Cardinal spline (see below). Never finishes. |

Ground scroll is included in curve-based patterns.

### WaypointPath

**Per-waypoint fields:**
- `target`, `tension`, `speed` (the speed arriving at this point), `waitSeconds`;
- `orientation`: `path`, `player` (turns at `aimSpeed`) or `fixed` (`fixedAngle`);
- an optional sound (volume and pitch variation);
- an optional `weaponSet` switch (a key into the enemy's `weaponSets`).

**Path-level fields:**
- `closePath` loops the path, re-anchoring each lap;
- `globalSpeed` scales every speed;
- `flipX` / `flipY` mirror about the play-area center.

Speed follows arc length, approximated from the tangent, in steps of 0.02 units. `WaypointSpline`
(Hermite segments with per-point tension) is shared with the editor. At the end of the path the enemy
parks at its last waypoint.

---

## Firing patterns and bullets

### Field resolution

- **Numeric fields:** pattern value, then `BulletDef` (via `bulletId`), then the type default.
- **Speed phases:** the pattern's `bulletSpeedPhases`, else the BulletDef's (the two are never
  merged), else a single phase of `bulletAcceleration`. `SpeedProfile` / `SpeedRamp` run them, with
  an optional loop.
- **Speed limits:** `minSpeed` defaults to 0 (a bullet can slow to a stop but not reverse);
  `maxSpeed` is unbounded unless set.
- **Damage** defaults to 1.
- **Bullet texture:** pattern, then BulletDef, then the enemy's `bulletTexture`. The sheet layout
  comes from the pattern or BulletDef only.

### Firing types

Defaults are given as (bullet size, speed).

| Type | Notes |
|------|-------|
| `Aimed` (0.25, 5) | At the player, with an optional `targetOffset`. |
| `AimedAtPoint` (0.25, 5) | At `targetX/Y`. |
| `BurstAimed` (0.25, 5) | 5 aimed shots `burstInterval` (0.15) apart, then `fireRate`; `phaseOffset`. Ignores the target offset. |
| `QuarterCircle` (0.25, 5) | A fan of 9 bullets over 90°; `quarterCircleFixedAngle`. |
| `Sweep` (0.25, 5) | One shot per tick while sweeping between angles (default 225°→315° over 2 s), ping-pong. |
| `SineWave` (0.2, 5) / `Feather` (0.2, 1.5) | Falling bullets that sway (`amplitude`, `frequency` in rad/s). Feather adds a second sway and rocks ±28°. |
| `Orbiting` (0.5, 4) | Two bullets orbiting a center that drifts toward the player (`orbitRadius`, `orbitSpeed`). Never accelerates. |
| `ExplodingAimed` (0.25, 6) | A ring of 8 that bursts outward, then after 0.4 s re-aims at the player 20% faster. |
| `SelfDestruct` (0.25, 4) | Fires when the player comes near, then the enemy dies (3 s). |
| `Laser` | An anchored beam: `thickness` (0.3), `length`, `angularSpeed`, `fireAngle`, `duration`. Its hitbox is 0.4 × the thickness. |
| `Wall` (0.25, 5) | A row of falling bullets across the field with gap lanes (`margin` 0.25, `spacing` 0.4, `gapLaneStart`, `gapLaneCount`, `gapLaneSequence`, rate 0.3). |
| `PolkaDot` (0.25, 5) | Alternating offset rows (rate 0.3). |
| `RadialNearMiss` (0.3, 4) | A ring of 16 from the screen edges, each passing the player at `nearMissDistance` 0.35 (rate 1.5, `volleyCount` 3). |
| `Shape` (0.25, 3) | Bullets that form a picture: `shapePoints`, `numBullets`, spread, `fireAngle` (NaN = aimed), `shapeScale`, `shapeFormTime`, `shapeDriftRatio`, `shapeFlipX`, `shapeRotateWithDirection`, rate 1. |
| `SpawnEnemy` | Spawns `spawnType` at a rate and offset, with `spawnMovementPattern`. |
| `Sequence` | Sub-patterns by duration (default 3 s). `advance()` skips to the next one; each boss kill advances the others. |
| `Combined` | All sub-patterns at once. |
| `None` | Doesn't fire. |

**Shape formation** (`ShapeBullet`): every dot starts at the emitter. At `formTime` the picture has
formed at `formScale`, and afterwards it keeps drifting apart. `shapeDriftRatio` r sets how fast:

- r = 1: constant spread speed;
- r < 1: dots burst out, then settle into the shape;
- r > 1: dots start slow and speed up.

### Bullet classes

`enemy/bullets`: `BasicEnemyBullet` (falls), `AimedEnemyBullet` (toward a point), `DrifterBullet`
(a direction), `SineBullet`, `FeatherBullet`, `OrbitingBullet`, `LaserBullet`, `ShapeBullet` and
`ExplodingAimedBullet`.

- Aimed, Drifter, Sine and Feather default to circular hitboxes; ExplodingAimed and Laser to
  rectangles.
- A bullet's hitbox is its sprite rectangle scaled by `hitboxScale`, plus an offset that rotates
  with it. It can also be a circle (`hitRadius`).
- Rotated bullets rotate about their own pivot: the center for most, bottom-center for lasers.
- Bullets are culled outside x ∈ [0, 9] and y ∈ [−2, 14].

### Explosion patterns

`ExplosionPatternDef`:
- `Burst`: particles from random textures, with angle, speed and size ranges, a start delay and
  velocity damping;
- `Combined`: children play together;
- `Sequence`: children play one after another.

---

## The player

`player/Player`, configured by `data/player.json` (`PlayerDefinition`): sprites, hitbox and graze
hitbox sizes, speed, `startingLives`, `baseMaxBombs` and `maxWeaponLevel`.

- **Two weapon slots**, swapped with Switch Weapon. Both slots' cooldowns tick every frame, so
  switching can never reset or skip a cooldown.
- **Focus movement:** while firing, the player moves at the active weapon's `shootSpeedMultiplier`
  (default 0.75). The same slowdown applies while a Hyper Attack has the halo out, for precise
  aiming, but not during the Thunderbolt bomb animation.
- **Hitboxes:** a small circle for being hit, and a wider **graze halo** for grazing bullets and
  collecting powerups and gems. The graze hitbox follows the halo even when it's detached.
- **Graze points:** 0.5 per graze. Past 100, the player earns a bomb (up to `maxBombs`).
- **Bomb:** clears every enemy bullet and damages enemies (`balance.json`: `bombDamage`,
  `bombCooldown`). A bomb just after a hit cancels the hit (see the game loop).
- **Death:**
  - a 2 s wait, then respawn with 2 s of i-frames (enemies hold fire during both);
  - the halo snaps back and both weapons drop to level 1;
  - a restore powerup drops at the death spot, and `maxBombs` grows by one, up to
    `Player.MAX_BOMB_CAPACITY` (6). Every way of setting the capacity is clamped to that.
- **Powerups** (`WeaponPowerup`) bounce around the screen until collected. Each adds its `amount`
  (1–3) to *both* equipped weapons, capped at `maxWeaponLevel`. The restore drop is additive too, so
  it stacks with other pickups.
- **New stage:** start position, halo attached, equipped weapons back to level 1. Lives, bombs and
  the loadout carry over.
- `weaponsDisabled` / `hyperAttackDisabled` block only the press effect; the raw input still drives
  focus slowdown, the orbit ring and gem homing.

### Hyper Attacks

Each weapon's `hyperAttack()` runs on the Hyper Attack button:

- **Basic: halo dash.** The halo launches `haloDashDistance` forward at `haloDashSpeed`, hitting
  enemies once each for `haloDashDamage` (flashing red, with a burst sized by
  `haloCollisionScale`). It then rests, detached, firing half of Basic's pattern: the ship fires the
  even streams and the halo the odd ones, so the pattern is split rather than doubled. Pressing again
  recalls it at `haloFastReturnSpeed`; other recalls, like switching weapons, use `haloReturnSpeed`.
- **Thunderbolt: charge bomb.** The halo moves `thunderboltHaloFrontDistance` in front of the ship
  and tracks it, gaining a charge tier every `thunderboltChargeLevelTime` (with a sound per tier).
  Releasing detonates it at that tier's `thunderboltChargeDamageByTier` and
  `thunderboltBlastRadiusByTier`: `CollisionManager` applies the area damage and green lightning,
  and the bomb animation, scaled to the blast, plays before the halo reattaches. A quick tap
  detonates at the base tier. A press during the bomb animation is buffered and restarts the charge.
  Switching weapons fizzles the charge. There's no cooldown.
- **Orbit: reflect shield.** It lasts 2 s with a 4 s cooldown and radius 1.1. Enemy bullets that
  touch it become `ReflectedBolt`s aimed at the enemy that fired them.
- **WaveBlast: homing bolts.** Three `HomingBolt`s fire out the back at the current level's damage
  (4 s cooldown). They turn toward the nearest enemy at a limited rate.

While the halo is detached, only Basic's re-press (the recall) is accepted.

---

## Weapons

`data/weapons.json` holds one `WeaponDefinition` per weapon.

**Common fields:**
- `damageByLevel[]`, `fireRateByLevel[]`, `speedByLevel[]` (indexed by level − 1 and clamped);
- `chainWindow`, `shootSpeedMultiplier`;
- the texture and sheet layout;
- an optional hit effect (`hitTexture` and layout, built once by `AssetManager` into
  `hitAnimation`).

| Weapon | Fire | Hyper Attack |
|--------|------|--------------|
| `BasicWeapon` | Parallel streams that widen with level (1 stream, then 4-wide, then with outer and inner spreads). Each stream is a side-by-side pair so the ship/halo split stays even. | Halo dash. |
| `Thunderbolt` | Lightning strikes on the nearest enemies anywhere on screen (2/4/6/8 bolts by level). Extra bolts double up when there are few enemies; with no target it plays a "whiff" sound. **Chain lightning:** a hit arcs to the `arcTargets` nearest other enemies within `arcRange` for `damage × arcDamageMultiplier`, preferring enemies not already targeted; arcs don't chain further. | Charge bomb. |
| `OrbitWeapon` | While fire is held, a ring of `level` bullets orbits the ship (`radius`, `rotationSpeed`), damaging on contact, with a whip crack per member per lap. | Reflect shield. |
| `WaveBlastWeapon` | Wave shots that splinter on hit. Only obtainable from powerups. | Homing bolts. |

**Implementation notes:**
- `Weapon` instances are both "slot" objects (never drawn; they hold cooldowns, the shield state
  and the ring) and pooled projectiles.
- A persistent projectile damages each enemy only once (`hasDamaged` / `markDamaged`).
- `onHit` can spawn follow-up projectiles.
- A non-zero `getRotation()` makes collision treat the rectangle as an oriented box.
- Thunderbolt strikes are drawn as generated lightning (`LightningBolt`: recursive midpoint
  displacement with forks, clamped to the hitbox). They use an outline pass with normal alpha
  blending, then glow and core layers with `GL_MAX` blending. `GL_MAX` stops overlapping joints
  getting brighter, and `EntityManager` opens one `GL_MAX` section for all strikes.

---

## Collisions

`CollisionManager`:

- **Player vs air enemies and enemy bullets:** uses the small hit circle. Ground enemies never touch
  the player. The reflect shield is checked first.
- **Graze halo:** grazes, weapon powerups and point gems.
- **Player weapons vs enemies:**
  - each projectile damages a given enemy once and may pierce;
  - hits add a chain timer bonus;
  - the loop keeps applying until the enemy dies, so bullets don't pass through bosses.
- **Enemy bullets vs enemies:** only enemies with `damageableByEnemyBullets` are affected, never the
  bullet's own source.
- **Halo dash:** one hit per enemy per dash.
- **Thunderbolt detonation:** everything in the radius.

**Geometry:** bullet hitboxes are scaled and offset rectangles or circles and respect rotation.
Rotated rectangles use a separating-axis (SAT) test. Enemies use custom `HitboxDef` shapes when
they have them.

---

## Scoring, chains, gems and rank

### Chains (ScoreManager)

- Each kill increments `chainCount`, adds the enemy's score to `chainValueSum`, then adds
  `chainValueSum` to the score. Later kills in a chain are worth the running total.
- The chain timer resets to the weapon's `chainWindow` on each kill. Weapon hits add a small bonus
  (capped at the window).
- The chain breaks when the timer runs out or a life is lost.
- ScoreManager also tracks kills (total, per type and per spawn group), gems, the peak chain and the
  high score.

### Point gems

- A kill drops `health / gemsPerEnemyHealth` gems, capped at `maxGemsPerEnemy` (60); beyond that,
  each gem is worth several.
- Gem size and value scale between `gemMinScale` and `gemMaxScale` with how close the player was to
  the kill (full value within `gemFullDistance`).
- Gems pop up, then fall while the player fires. When the player stops firing they home in on the
  graze halo, accelerating for 2 s and then re-aiming so they don't orbit.
- Waypoint gems (a trigger action) are stationary.

### Level complete

- **Bomb bonus:** unused bombs × `bombBonusPerUnusedBomb`.
- **Boss time bonus:** `(bossTimeBonusParSeconds − fight seconds) × bossTimeBonusPerSecond`,
  timed from the boss spawn.
- **Lives multiplier:** score × (lives + 1) if any lives are left.
- **Rank** (S/A/B/C/D, display only) averages five fractions and compares the result to
  `rankThresholds`:
  - kills out of total spawned (wave members included);
  - peak chain / `chainRankTarget`;
  - boss speed;
  - bombs kept;
  - lives kept.

---

## Entities, pooling and draw order

- `ObjectPools`: libGDX pools for player projectiles, enemy bullets, `GenericEnemy`, powerups,
  effects and gems. Always free objects back through `ObjectPools`.
- `EntityManager` owns the player and every entity list. Enemies hold fire while the player is dead
  or in respawn i-frames (`firingPaused`).
- **Bullet cancel:** a bomb destroys every enemy bullet. An enemy with `bulletCancel` destroys its
  own bullets when it dies. Bullets are matched by source reference, so this happens before the
  enemy is freed to the pool.

**Draw order**, back to front:

1. powerups, then point gems;
2. player bullets, then Thunderbolt strikes (outline pass, then one `GL_MAX` pass);
3. enemy shadows, ground enemies, explosions, air enemies;
4. hit effects, bullet-cancel effects, green lightning;
5. the bomb animation (additive);
6. the player;
7. enemy bullets (grouped by texture to cut batch flushes, with lasers last);
8. scripted sprite cues.

Enemies attached to a background layer are drawn between the background layers instead.

---

## Backgrounds and shaders

`ScrollingBackground` draws, in priority order:

1. the boss video;
2. the shader background;
3. the background video;
4. image layers, far to near.

**Image layers** scroll at `scrollSpeed × speedScale` and freeze when the top of the strip is
reached.

**Videos** use gdx-video. The decode buffer is padded, so only the real region is drawn. Videos can't
seek, so a seek restarts them. On game over, the boss video (and its audio) stops.

**Layered draw:** `GameController` draws each layer, then the enemies attached to it, then the
feedback overlay, then the other entities.

### Shader backgrounds

All implement `BackgroundShader`; the shader sources are in `assets/shaders`, compiled by
`ShaderLoader` with optional `#define`s.

- **boxTunnel** (`TutorialBoxTunnelShader`).
- **kaleidoscope** (`Stage2KaleidoscopeShader`): a phosphene and lattice overlay, then a hard cut
  to a tentacle tunnel. The color fades from monochrome to the palette by distance. It uses camera
  distance rather than a clock, because the clock restarts on a seek.
- **mandelbulb** (`MandelbulbShader` + `MandelbulbCamera`): an autopilot orbits the fractal, then
  dives through its surface from `mandelbulbDiveDistance`. The Java `distance()` mirrors the
  shader's `field()` so the camera can steer.

`ReducedResolutionRenderer` renders shader backgrounds off-screen and scales them up. The shaders
only use `v_texCoords` and `u_resolution`, so they work at any resolution.

| `GraphicsSettings.shaderQuality` | Render scale |
|------|------|
| HIGH | 0.5 |
| MEDIUM | 0.35 |
| LOW | 0.35, capped at 300 px tall, updated every other frame |

The setting is stored in Preferences `whitelabeltest-graphics` and applies from the next stage.
MEDIUM and LOW also compile the shaders with `QUALITY_MEDIUM` / `QUALITY_LOW`.

### Overlays

- **HueCycleShader**: rotates the image layers' hue once per period (the boss video's
  time/distance). It's stateless, so a seek lands on the right hue.
- **PlayerFeedbackShader**: a video-feedback trail of the player and halo, alpha-blended over any
  background.
  - It uses ping-pong framebuffers: zoom 1.035, decay 0.975, chroma, scroll drift, a hue step, a
    saturation floor and a 3-frame warmup.
  - It saves and restores the scissor and viewport.
  - While the player is dead it gets a null frame, so no "statue" is left behind.

---

## Effects

`gamemanagers/effects`:

- `AnimationCache`: shared animations keyed by texture, grid, timing and play mode.
- `ExplosionEffect`: runs explosion patterns (pooled, with reused particles).
- `PointGem`: see [Point gems](#point-gems).
- `SingleShotAnimation` subclasses:
  - `HitEffect`: a weapon's hit animation;
  - `BulletCancelEffect`: 5 random variants;
  - `ScheduledSpriteEffect`: sprite cues.
- HUD shader effects:
  - `ChainFireEffect`: a port of a Godot fire shader with procedural noise. Intensity is
    chain / 125, with an overdrive ramp up to 250. `getOverflow()` eases from 0 at a chain of 100
    to 1 at 250, and the HUD uses it to grow the flame out of its box. Coloured to the HUD palette:
    lime embers, a lime-to-cyan blaze, then orange to red.
  - `CircleMeterEffect`: a ring meter, solid or dashed (with a rotation offset for spinning dashes).

  They use `tinted.vert` plus their own fragment shaders.

---

## UI

`UIManager` draws:

- **The HUD** (`GameHud`, owned by `UIManager`), over the play area. The layout follows
  `assets/UIGuide.png`; the styling is WipEout / Designers Republic: cyan `#00F0FF`, lime `#CCFF00`
  and orange `#FF3B30` on dark carbon, chamfered panels, soft glows, hazard stripes, and the
  Orbitron (numerals) and Share Tech Mono (labels) fonts in `assets/fonts` (SIL OFL; licences
  alongside). Sizes are given in reference-design pixels: `PX` = 1/90 of a world unit, times
  `SCALE` (1.25, for legibility).
  - **Score bar:** a pinging dot, SCORE (lime), a STAGE tag, and HIGH SCORE (white, cyan glow).
  - **Chain stack** (top right):
    - the COMBO box, chamfered, with CHAIN and the count over the chain flame. A bar fills toward a
      chain of 100, where a MAX tag lights up and the flame grows out of the box through the
      score bar;
    - lives as shield pods with hearts, sized for the starting lives: cyan, orange on the last
      life, dark once lost;
    - the chain timer as a heat meter: a red-to-orange hazard-striped fill, a dashed critical line
      at 25%, and a CRIT label that blinks when the chain is about to break.
  - **Weapons capsule** (bottom left), top to bottom:
    - the weapon level in Roman numerals in a pill (both equipped weapons level up
      together, so it's the active one's level);
    - the two weapons' tiles (`images/ui/RainIcon`, `LightningIcon`, `MoonIcon`): bright
      for the active one, dimmed for the other;
    - the GRZ gauge, filling toward the next graze bomb (100 points), with the points shown;
    - the BOMB gauge: a slowly turning dashed ring, full when a bomb is ready, filling while it
      recharges, dim with none, with the bomb icon (`images/ui/BombIcon`) in the middle;
    - one chevron per bomb of capacity (`FullBombIcon` / `EmptyBombIcon`), filled from the bottom
      and pulsing, with room for the maximum capacity of 6;
    - a PWR status tag underneath (CRITICAL on the last life).
  - Cyan L-brackets in the play area's corners.
  - **Side switching:** the chain stack and the capsule each move to the opposite edge when the
    player's hitbox comes within 1 unit of them (`DockedPanel`), as long as the other side is
    clear. They slide out past the edge and back in on the other side over 0.3 s, mirrored. The
    HUD is drawn inside the play-area scissor, so a sliding panel disappears into the edge.
- **Text cues:** see [Text cues](#text-cues).
- **Game over sign, stage clear screen** (kills / total, peak chain, bonuses, TACTICAL RANK card)
  and the **stage select** hex lattice.
- **Debug overlays:**
  - an FPS monitor with a history graph;
  - the trigger distance and active gate bar;
  - enemy health;
  - the mute indicator;
  - the debug menu and in-game editors.

---

## Audio

- **AudioManager** handles:
  - fixed SFX and the music tracks;
  - **sound banks** (`data/sounds.json`): `{type, level, sounds[]}` for `BasicWeapon`,
    `WaveBlastWeapon`, `OrbitWhip`, `Thunderbolt`, `Explosion`, `PointGem` and `OrbitGong`, with a
    random pick on each play;
  - cue sounds by path (cached, preloaded per stage);
  - stage music: switching to the track already playing does nothing, and music fades out over 3 s;
  - the victory fanfare, then a loop;
  - the text cue blip (once for static and blinking cues, looped for typewriter cues).
- **Throttling:** gem, explosion, orbit gong and halo bash sounds are throttled to one per
  0.04–0.05 s. Hundreds of `Sound.play` calls in one frame dropped frames.
- **AudioSettings** (Preferences `whitelabeltest-audio`): master, music and SFX volumes from 0 to 1.
  The applied gain is slider³ (perceptual), and the effective volume is master × channel.

---

## Input

- **KeyBindings** (Preferences `whitelabeltest-keybindings`):

  | Action | Keyboard | Gamepad |
  |--------|----------|---------|
  | Move | Arrows | Stick / D-pad |
  | Shoot | Space | X |
  | Bomb | Left Shift | B |
  | Switch Weapon | X | RB |
  | Hyper Attack | C | A |
  | Restart / confirm | R | Start |
  | Quit | Q | Back |

- **InputManager** reads the keyboard or the current gamepad (stick deadzone 0.2, plus the D-pad).
  Press and release edges come from the held state. On the first frame it seeds the held state, so
  a button held over from the previous screen isn't a new press. During replays it reads
  `ReplayFrame`s instead.
- **Debug hotkeys** always read the live keyboard:
  - F12: debug mode;
  - F9: restart;
  - F1: menu (arrows, Enter, Delete, N for a bookmark);
  - M: mute.

---

## Replays

- `ReplayRecorder` writes `~/WhiteLabelTest/replays/replay_<epochMillis>.json` for each run, from
  one reset to the next. Runs shorter than 30 frames aren't saved.
- **ReplayData:** `rngSeed`, `stageSequenceId`, `weaponLoadout`, `recordedAt`, `finalScore`,
  `stagesReached`, `wasGameOver`, `frames`.
- **ReplayFrame:** `delta`, `moveX/Y`, `shooting`, `bomb`, `weaponSwitch`, `hyperAttackHeld`,
  `confirm` (RESTART) and `seekToTime` (NaN when there's no seek).
- **Determinism:**
  - `MathUtils.random` is seeded at every reset, and a replay reuses the recorded seed;
  - the debug menu and interstitial videos don't consume frames;
  - stage select and every random pick use only recorded input or the seeded RNG;
  - debug seeks are recorded.
- Tutorial runs aren't recorded.
- `ReplayBrowser` pages replays newest first. Both ReplaySelectScreen and the debug menu use it.

---

## Debug tools

Debug mode is on with `-Pdebug` / `-Ddebug`, or when a debugger is attached. F1 opens the menu:

- **Seek:** scrub to a distance or time, confirm, and save bookmarks
  (`data/debug_savestates.json`, via `DebugSaveStateManager`).
- **Loadout:** slot 1 and slot 2 weapons, weapon levels, and lives.
- **Enemy / Pattern editor** (`PatternPreviewer`): edit enemies, movement and firing pattern trees,
  and bullets, with a live preview enemy. *Save All* writes `movement_patterns/`,
  `firing_patterns/`, `enemies.json` and `bullets.json`. Values snap to 0.01.
- **Replay browser.**
- **Stage select:** any stage in `stages.json`, e.g. `testground`.
- **Spawn schedule editor:** edits legacy schedule events, then saves and reloads.

`Gdx.input.getTextInput` does nothing on LWJGL3, so these tools have their own text entry. With debug
on, the hitbox overlay draws:
- the player and graze circles;
- the Thunderbolt blast radius preview;
- enemy hitboxes (custom shapes rotated with the sprite);
- bullet hitboxes (scaled, offset and rotated as collision sees them);
- powerups and Thunderbolt strike boxes;
- on game over, the collision that killed the player.

---

## Performance tooling

`perf/PerfProbe` is off unless `-Dperf.log=<csv>` (`-PperfLog`) is set; when off, each call is a
single boolean check. When on, it writes one CSV row per frame. Sections are timed with nestable
`begin`/`end` calls. Each row records:
- CPU time per section;
- time spent outside `render()` (swap, vsync, the FPS limiter, GPU back-pressure);
- GL draw calls, texture binds and shader switches (`GLProfiler`);
- entity counts, sounds started and GC activity.

| Flag | Effect |
|------|--------|
| `-DautoReplay=<json>` / `.seconds=<n>` | Repeatable hands-free runs; quits when done. |
| `-Dperf.invincible` | Player ignores hits (profile a whole stage off a desynced replay). |
| `-Dperf.noFeedback`, `-Dperf.noHueCycle`, `-Dperf.noBossVideo` | Disable those features for A/B tests. |
| `-Dperf.fpsCap=<n>` | Override the fallback FPS cap. |
| `-PjfrFile=<file>` | Record a JFR profile. |

The desktop launcher relies on vsync. Its FPS cap is only a fallback, set to twice the refresh rate:
GLFW rounds fractional refresh rates down, and a cap below the real rate causes periodic missed
vsyncs.

---

## The stage editor

`gradlew editor:run` launches a JavaFX app. It reads and writes the same files the game loads, and it
reuses the game's own data classes and pattern math (`WaveSpawnPlanner`, `EnemyEntranceMovement`,
`WaypointSpline`, `MovementPattern`s). It can't create libGDX textures because it has no GL context,
so all drawing uses JavaFX.

**Layout:**
- **Left:** the enemy palette (drag to place; click to edit the type; "+ New Enemy"), the action
  palette (sound, sprite, camera speed, and a blank "Trigger Event") and the stage palette.
- **Center, "Edit" tab (`StageCanvas`):**
  - x is world x, with 2.5 units of off-screen margin either side; y is distance, with 0 at the
    bottom;
  - triggers show their real sprites at in-game size;
  - it draws true-scale movement path previews, wave member dots and arrows, and the background art
    (sampled per screen-height band so every parallax layer is true scale);
  - editing: click, Ctrl/Cmd-click or rubber-band to select; drag groups; Ctrl+C/Ctrl+V (a paste
    forks the movement pattern file); Delete to remove.
- **Center, "Player View" tab (`PlayerPreviewView`):** the game screen at the timeline's distance.
  Enemies are stepped through the real movement classes, with ground scroll and wave handling.
- **Right:** the properties panel for the selection:
  - **Trigger:** distance, flags (Gate, Require confirm, First attempt only / Retry only),
    conditions, the action, and the enemy spawn fields (spawn lead, enter from above, movement path,
    wave, health phases);
  - **Enemy type:** stats, textures, firing pattern, weapon sets, and the hitbox editor;
  - **Stage:** files, music, background and overrides.
- **Movement Path editing:** "Edit Path on Stage" turns the selected spawn's WaypointPath into
  draggable handles; clicking empty canvas adds a waypoint. Older `Sequence` patterns can be
  converted. Paths are saved to `movement_patterns/<id>.json`, separately from the trigger file.
- **Dialogs:**
  - Firing Pattern Editor: list, tabbed fields, a live preview and nested sub-patterns;
  - Shape Editor: draw a Shape pattern's dots;
  - Hitbox Editor: rectangles and circles over the animated sprite, rotatable;
  - Open Stage: can create a missing trigger file;
  - Quick Play.

The firing pattern preview re-implements each pattern's timing and motion in plain Java, because the
real bullets need a GL context. The in-game `PatternPreviewer` shows the exact behaviour.

### Quick Play

The Quick Play button runs `gradlew :lwjgl3:run` with
`-PquickPlayStage/Distance/SlotA/SlotB/SlotALevel/SlotBLevel/Input`. `lwjgl3/build.gradle` passes
these to the game as `-DquickPlay.*` system properties. `Lwjgl3Launcher` builds a
`Main.QuickPlayConfig` from them, and `GameController.quickStartAtStage` starts there. The dialog
lets you choose any of the four weapons and levels, and the input device, since the start screen
(which normally detects the device) is skipped.

---

## Data file reference

All under `assets/data/`:

| File | Contents |
|------|----------|
| `stages.json` | Stage definitions. |
| `stage_sequences.json` | Runs: stage lists, maps, starting loadouts. |
| `stages/<id>_triggers.json` | Trigger scripts. |
| `stages/<id>_schedule.json` | Legacy time-based schedules. |
| `enemies.json` | Enemy definitions. |
| `movement_patterns/<id>.json` | One movement pattern per file. |
| `firing_patterns/<id>.json` | One firing pattern per file. |
| `bullets.json` | Reusable bullet definitions (`bulletId`). |
| `explosion_patterns.json` | Explosion effects. |
| `weapons.json` | Player weapons. |
| `player.json` | Player sprites, hitboxes, lives, bombs, max weapon level. |
| `balance.json` | Bomb, gem, bonus and rank tuning. |
| `sounds.json` | Sound banks. |
| `interstitials.json` | Pre-stage video clips. |
| `debug_savestates.json` | Debug bookmarks. |

`assets/assets.txt` lists the asset files for packaged builds, where directories can't be listed.

---

## Conventions

- **World space:** the play area is 9 × 12 units, with y up. Enemies and bullets travel toward the
  player by moving to lower y.
- **Angles:** degrees; 0 = right, 90 = up, 270 = down.
- **Sprite positions:** `x`/`y` on a trigger is the sprite's bottom-left corner. Waypoints use
  sprite-center space.
- **NaN means "unset"** for optional floats (e.g. `scrollSpeed`, `fireAngle`, trigger `x` on text
  cues and gates, `seekToTime`).
- **Two clocks:** stage progress (distance, or the legacy `totalTime`) freezes at gates. Real time
  never freezes; text cues, waves and videos use it.
- **Defaults:** `x/y/offsetX/offsetY` fields are NaN when unset. Pattern fields left at their
  defaults aren't written to JSON.

---

## Gradle reference

This project uses the Gradle wrapper (`gradlew.bat` / `./gradlew`). Useful tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the game.
- `editor:run`: starts the stage editor.
- `test`: runs unit tests (if any).

Most tasks that aren't specific to one project can be run with a `name:` prefix, where `name` is the
project's ID. For example, `core:clean` removes the `build` folder only from the `core` project.
