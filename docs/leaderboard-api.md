# Leaderboard API

The contract between the game client (`core/.../online/`) and the online leaderboard server.
`leaderboard-mock` implements it for local testing, including full replay validation through the
`headless` module. See [Validation](#validating-a-score).

- Base URL: configured in `assets/leaderboard.json` (`baseUrl`), overridable with
  `-Dleaderboard.url=...`. The default is `http://localhost:8787` (the mock).
- Every request and response body is JSON (`Content-Type: application/json`).
- Every error response is `{"error": "<code>"}` with the status codes listed below.
- Authenticated requests send `Authorization: Bearer <token>`.
- Production must use HTTPS: the token is the only credential.

---

## Usernames

A username is exactly one word from each of these lists, joined with no separator, e.g.
`SwiftFalcon`. Matching is case-sensitive. The lists are `UsernameGenerator.FIRST_WORDS` and
`SECOND_WORDS`. They're repeated here so a non-Java server can implement the check, and **both
copies must stay identical**.

First words (32):
`Amber Arctic Azure Bold Brave Bright Calm Clever Cobalt Cosmic Crimson Crystal Daring Electric
Emerald Frosty Gentle Golden Hidden Jade Lucky Lunar Mighty Neon Nimble Quiet Rapid Silver Solar
Swift Velvet Violet`

Second words (32):
`Beacon Cipher Circuit Comet Dragon Falcon Fox Glacier Golem Hawk Heron Knight Koala Lynx Mantis
Meteor Nebula Orbit Otter Owl Panda Phoenix Pilot Pixel Raven Rocket Spark Sparrow Tiger Voyager
Wizard Wolf`

There are 1,024 possible names, and each belongs to at most one player. Players never type a name;
the client only offers generated ones. The server must still reject anything else, since the API is
public.

---

## POST /v1/players

Registers a username and returns the player's credentials.

Request:
```json
{ "username": "SwiftFalcon" }
```

| Status | Body | Meaning |
|--------|------|---------|
| 201 | `{"playerId": "…", "username": "SwiftFalcon", "token": "…"}` | Registered. The client stores the token and sends it with scores. |
| 400 | `{"error": "invalid_username"}` | Not a valid word pair. |
| 409 | `{"error": "username_taken"}` | Already registered. The client offers new names. |

The token is an opaque, unguessable string (the mock uses 32 random bytes, URL-safe base64). There
is no login or recovery flow: the token lives in the player's local Preferences
(`whitelabeltest-account`).

---

## POST /v1/scores  *(authenticated)*

Submits a run. The client only sends a run that beats the player's best score the server has
already accepted.

Request:
```json
{
  "score": 3213319,
  "stagesReached": 2,
  "wasGameOver": true,
  "gameBuild": "1.0.0",
  "dataHash": "009245d2…",
  "replayEncoding": "gzip+base64",
  "replay": "H4sIAAAA…"
}
```

- `replay` is the run's `ReplayData`, serialized as JSON by libGDX `Json` (`OutputType.json`),
  gzipped, then base64-encoded. Its fields:
  - `rngSeed`, `stageSequenceId`, `weaponLoadout`, `recordedAtEpochMillis`;
  - `finalScore`, `stagesReached`, `wasGameOver`, `gameBuild`, `dataHash`;
  - `frames[]`: `delta`, `moveX`, `moveY`, `shooting`, `bombJustPressed`,
    `weaponSwitchJustPressed`, `hyperAttackHeld`, `confirmJustPressed` and `seekToTime`.
  Fields equal to their defaults are omitted (e.g. `seekToTime` when it's NaN, `false` booleans).
- Size: roughly 6 bytes per frame after compression (a 4.5-minute run is about 94 KB). The mock
  caps bodies at 32 MB.

| Status | Body | Meaning |
|--------|------|---------|
| 200 | `{"status": "accepted", "personalBest": 3213319, "rank": 12}` | Validated and recorded as the new personal best. |
| 200 | `{"status": "not_a_personal_best", "personalBest": 4000000, "rank": 9}` | Valid but not better than the player's best. |
| 400 | `{"error": "invalid_body" \| "invalid_score" \| "invalid_replay" \| "unsupported_replay_encoding"}` | Malformed. The client discards it. |
| 401 | `{"error": "unauthorized"}` | Unknown token. The client keeps the run, re-registers its name to get a new token, and resends. |
| 413 | `{"error": "too_large"}` | Body too big. The client discards it. |
| 422 | `{"error": "replay_mismatch" \| "replay_empty" \| "unsupported_build" \| "replay_invalid" \| "replay_has_debug_seek"}` | Failed validation. The client discards it. |
| 5xx / no response | – | The client keeps the run and retries on the next launch, run or leaderboard visit. |

### Validating a score

The replay system makes runs reproducible. With the same code, data, seed and per-frame inputs
(including each frame's recorded `delta`), the simulation replays identically. A server validates a
submission like this:

1. **Check the build.** Accept only `gameBuild` / `dataHash` pairs it has a simulator for. It needs
   a copy of the game's code and `assets/` for each supported release. `dataHash` is
   `BuildFingerprint.dataHash()`: SHA-256 over every `data/*.json` listed in `assets.txt`, taking
   each file's path then its bytes, in listed order, excluding `data/replays/` and
   `data/debug_savestates.json`. Bump `BuildFingerprint.GAME_BUILD` whenever a code change could
   change how a replay plays out.
2. **Sanity-check cheaply**, before the expensive simulation: score ≥ 0, the claim equals
   `replay.finalScore`, there are frames, a known `stageSequenceId` and `weaponLoadout`, `delta`
   values within a plausible range (e.g. each 0 < delta ≤ 0.1 s), and a per-player rate limit.
3. **Re-simulate** with the `headless` module:
   `HeadlessReplayValidator.simulate(replay)` returns a `ReplayResult`. Accept only if
   `result.matches(replay)` (same score, stages reached and game-over state) and
   `!result.containsSeek` (seek frames only come from debug tools).
   - A 4.5-minute run takes about 3–4 s to simulate. Simulations run one at a time on a single
     libGDX thread, so a busy server should queue submissions or run several processes.
   - The process's working directory must be the game's `assets/` folder.
4. **Store** the replay alongside accepted scores, so leaderboard entries can be audited or
   re-verified after a fix.

`leaderboard-mock` does steps 1–3 (without the delta range check or rate limiting). In testing it
accepted genuine runs, and rejected:
- a forged score, even when the replay's own summary was edited to agree;
- a replay whose inputs were changed partway through;
- replays from another build.

### Headless mode and determinism

`GameController.simulateReplay()` runs the real game code with `headless = true`:
- no shaders, framebuffers, videos or debug tools;
- interstitial videos are skipped, but their clip pick still consumes its random value;
- `draw()` is never called.

Textures are still loaded, because sprite and hitbox sizes come from their dimensions. The `headless`
module installs a no-op GL (`NoOpGL`) so this works without a GPU.

A replay only reproduces if every use of the gameplay RNG (`MathUtils.random`) happens during
`update()` in the same order. Anything cosmetic must use its own generator. Two places broke this
before build 1.1.0: sound-bank picks, which were skipped when muted, and Thunderbolt's per-segment
bolt phase, whose segment count was clock-seeded. That's why earlier replays don't reproduce.

`gradlew headless:run --args="--self-test <replay.json> ..."` checks this property on any replay,
whatever build it came from. It runs each replay twice headlessly and once with `draw()` every
frame, and all three must end in exactly the same state. Run it after changing gameplay or effects
code. With the Thunderbolt bug put back, it fails: three runs of one replay gave three different
scores.

---

## GET /v1/leaderboard?limit=N

`N` defaults to 10 (the mock caps it at 100). Authentication is optional: with a token, `me` holds
that player's standing (`null` if they have no score yet).

```json
{
  "entries": [
    { "rank": 1, "username": "SwiftFalcon", "score": 3214319, "recordedAt": 1790801390403 }
  ],
  "me": { "rank": 2, "score": 3564 }
}
```

Each player appears at most once, with their best accepted score. Higher scores rank first; ties go
to whoever set the score first.

---

## Client behaviour summary

- **First launch:** the username screen offers six generated names plus "generate new names". A
  taken name offers fresh choices. If the server can't be reached, the name is kept locally and
  registered later; if it has been taken by then, the player picks again.
- **End of run:** when a recording ends (game over then restart or quit, a completed run, or closing
  the game), the run is submitted if it beats the player's best. Tutorial runs, replays, Quick Play
  runs, and any run where debug mode was on are never submitted.
- **Retry:** the best unsent run is saved to `~/WhiteLabelTest/pending_score.json` and retried on
  startup, after each run, and when the leaderboard screen opens.
