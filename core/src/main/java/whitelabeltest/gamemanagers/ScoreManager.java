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

    public void registerWeaponHit(float chainTimerBonus, float chainWindow) {
        currentChainWindow = chainWindow;
        chainTimer = Math.min(chainTimer + chainTimerBonus, currentChainWindow);
    }

    /** Dying always ends the current chain (score already banked stays, unlike reset()) - called
     *  from GameController.applyPlayerHit() on every life lost, not just a full game reset. */
    public void breakChain() {
        chainCount = 0;
        chainValueSum = 0;
        chainTimer = 0;
    }

    public void addBonus(int points) {
        score += points;
        if (score > highScore) highScore = score;
    }

    public void multiplyScore(int factor) {
        score *= factor;
        if (score > highScore) highScore = score;
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
