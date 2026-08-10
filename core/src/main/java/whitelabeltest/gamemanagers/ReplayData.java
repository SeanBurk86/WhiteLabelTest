package whitelabeltest.gamemanagers;

import com.badlogic.gdx.utils.Array;

/** One file = one recording, spanning one GameController.reset() to the next reset()/dispose() -
 *  see ReplayRecorder (writer) and ReplayPlayer (reader/playback cursor). rngSeed is what was fed
 *  to MathUtils.random.setSeed() at the start of the recorded run - since every random draw in the
 *  game funnels through that one shared source, replaying the same seed + the same frames
 *  reproduces the run deterministically (same enemy/powerup RNG, same SFX picks). finalScore/
 *  stagesReached/wasGameOver are a summary header filled in right before saving, purely so a
 *  replay-browser UI can show something more useful than a filename without fully parsing frames. */
public class ReplayData {
    public long rngSeed;
    public String stageSequenceId;
    public String weaponLoadout;
    public long recordedAtEpochMillis;
    public int finalScore;
    public int stagesReached;
    public boolean wasGameOver;
    public Array<ReplayFrame> frames = new Array<>();

    public ReplayData() {}
}
