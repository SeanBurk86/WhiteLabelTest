package whitelabeltest.gamemanagers.replay;

import com.badlogic.gdx.utils.Array;

/** One recorded run (reset to reset). Replaying rngSeed with the same frames reproduces the run,
 *  since all randomness uses MathUtils.random. The score/stage/game-over fields are a summary. */
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
