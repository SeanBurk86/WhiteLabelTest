package whitelabeltest.gamemanagers.replay;
import whitelabeltest.gamemanagers.input.InputManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.SerializationException;
import whitelabeltest.player.WeaponLoadout;

/** Records one run's frames and saves them to ~/WhiteLabelTest/replays/ (external storage, a stable
 *  per-user location, unlike the debug tools which write into assets/). */
public class ReplayRecorder {
    static final String REPLAY_DIR = "WhiteLabelTest/replays/";
    // Shorter recordings are false starts (e.g. an instant restart) and aren't saved.
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

    /** Records a debug seek as its own frame (it can't be reconstructed from deltas). */
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
