package whitelabeltest.gamemanagers.replay;

/** How a replayed run ended in GameController's headless mode, for comparison with the
 *  ReplayData summary the client recorded. */
public class ReplayResult {
    public int score;
    public int stagesReached;
    public boolean gameOver;
    public int framesPlayed;
    public int totalFrames;
    // True if a debug seek frame was replayed (only debug tools produce these).
    public boolean containsSeek;
    // More of the final state, for comparing two simulations with each other.
    public int enemiesDestroyed;
    public int gemsCollected;
    public int maxChain;
    public int livesLeft;

    /** True when two simulations ended in exactly the same state. */
    public boolean sameAs(ReplayResult other) {
        return toString().equals(other.toString());
    }

    /** True when the simulation reproduced the recorded summary. */
    public boolean matches(ReplayData recorded) {
        return score == recorded.finalScore
            && stagesReached == recorded.stagesReached
            && gameOver == recorded.wasGameOver;
    }

    @Override
    public String toString() {
        return "score=" + score + " stagesReached=" + stagesReached + " gameOver=" + gameOver
            + " frames=" + framesPlayed + "/" + totalFrames + " kills=" + enemiesDestroyed + " gems=" + gemsCollected
            + " maxChain=" + maxChain + " lives=" + livesLeft + (containsSeek ? " (has debug seeks)" : "");
    }
}
