package whitelabeltest.gamemanagers;

public class ScoreManager {
    private static final float DEFAULT_CHAIN_WINDOW = 2.0f;

    private int score;
    private int highScore;
    private int chainCount;
    private int chainValueSum;
    private float chainTimer;
    private float currentChainWindow = DEFAULT_CHAIN_WINDOW;

    public void update(float delta) {
        if (chainTimer > 0) {
            chainTimer -= delta;
            if (chainTimer <= 0) {
                chainCount = 0;
                chainValueSum = 0;
            }
        }
    }

    public void addScore(int basePoints, float chainWindow) {
        chainCount++;
        chainValueSum += basePoints;
        currentChainWindow = chainWindow;
        chainTimer = chainWindow;
        score += chainValueSum;
        if (score > highScore) highScore = score;
    }

    public void addScore(int basePoints) {
        addScore(basePoints, currentChainWindow);
    }

    /** Called on every weapon hit (not just kills) to keep the chain alive between kills - tops
     *  the timer up by a bonus (see CollisionManager: half the weapon's fire rate) on top of
     *  whatever a kill already refreshed it to (see addScore), capped at chainWindow so the meter
     *  (chainTimer / currentChainWindow) never exceeds 100%. This is a supplement, not a
     *  replacement, for the kill-triggered reset: a bonus alone can never sustain the chain, since
     *  at a weapon's own max fire rate it always adds less (fireRate / 2) than the real time
     *  (fireRate) that decays between hits. */
    public void registerWeaponHit(float chainTimerBonus, float chainWindow) {
        currentChainWindow = chainWindow;
        chainTimer = Math.min(chainTimer + chainTimerBonus, currentChainWindow);
    }

    public void reset() {
        score = 0;
        chainCount = 0;
        chainValueSum = 0;
        chainTimer = 0;
        currentChainWindow = DEFAULT_CHAIN_WINDOW;
    }

    public int getScore() { return score; }
    public int getHighScore() { return highScore; }
    public int getChainCount() { return chainCount; }
    public float getChainTimerFraction() { return currentChainWindow > 0 ? chainTimer / currentChainWindow : 0; }
}
