package whitelabeltest.gamemanagers.replay;
import whitelabeltest.gamemanagers.input.InputManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.SerializationException;
import whitelabeltest.player.WeaponLoadout;

/** Captures one run's worth of ReplayFrames and persists them - see GameController's recorder
 *  field, which owns exactly one of these per reset()-to-reset()/dispose() span. Deliberately uses
 *  Gdx.files.external() rather than DebugSaveStateManager/PatternPreviewer's local-file style:
 *  those two are dev-only tools that intentionally write back into the project's assets/ source
 *  tree (via lwjgl3/build.gradle's `run.workingDir = assets/`), but replays are a player-facing
 *  feature meant to be recorded, found, and shared from a real packaged build - where Gdx.files.local
 *  would resolve relative to whatever directory the game happens to be launched from (unpredictable,
 *  and not something a player sharing a run with a friend could reliably find). external() instead
 *  resolves under the user's home directory, same stable-per-user-location principle AudioSettings/
 *  KeyBindings already use via Gdx.app.getPreferences(). */
public class ReplayRecorder {
    static final String REPLAY_DIR = "WhiteLabelTest/replays/";
    // Below this, a recording is almost certainly a false start (e.g. an instant F9 restart) -
    // not worth littering a file for.
    private static final int MIN_FRAMES_TO_SAVE = 30;

    private final ReplayData data = new ReplayData();

    public ReplayRecorder(String stageSequenceId, WeaponLoadout loadout, long seed) {
        data.rngSeed = seed;
        data.stageSequenceId = stageSequenceId;
        data.weaponLoadout = loadout.name();
        data.recordedAtEpochMillis = System.currentTimeMillis();
    }

    public void record(float delta, InputManager input) {
        ReplayFrame frame = new ReplayFrame();
        frame.delta = delta;
        frame.moveX = input.getMoveDirection().x;
        frame.moveY = input.getMoveDirection().y;
        frame.shooting = input.isShooting();
        frame.bombJustPressed = input.isBombJustPressed();
        frame.weaponSwitchJustPressed = input.isWeaponSwitchJustPressed();
        frame.hyperAttackHeld = input.isHyperAttackHeld();
        frame.confirmJustPressed = input.isRestartJustPressed();
        data.frames.add(frame);
    }

    /** Appends a special marker frame for a debug-menu seek/bookmark jump (see
     *  GameController.seekToTime()) - an instantaneous clock jump that normal delta-accumulation
     *  playback can't reconstruct on its own, so it needs its own event in the stream. */
    public void recordSeek(float targetTime) {
        ReplayFrame frame = new ReplayFrame();
        frame.seekToTime = targetTime;
        data.frames.add(frame);
    }

    public void setSummary(int score, int stagesReached, boolean wasGameOver) {
        data.finalScore = score;
        data.stagesReached = stagesReached;
        data.wasGameOver = wasGameOver;
    }

    public void saveIfNonTrivial() {
        if (data.frames.size < MIN_FRAMES_TO_SAVE) return;
        String path = REPLAY_DIR + "replay_" + data.recordedAtEpochMillis + ".json";
        try {
            Json json = new Json();
            Gdx.files.external(path).writeString(json.toJson(data), false);
        } catch (SerializationException e) {
            Gdx.app.error("ReplayRecorder", "Error writing " + path, e);
        }
    }
}
