package whitelabeltest.gamemanagers;

public class ScoreManager {
    private static final float DEFAULT_CHAIN_WINDOW = 2.0f;
    private static final int MAX_CHAIN_MULTIPLIER = 8;

    private int score;
    private int highScore;
    private int chainCount;
    private float chainTimer;
    private float currentChainWindow = DEFAULT_CHAIN_WINDOW;

    public void update(float delta) {
        if (chainTimer > 0) {
            chainTimer -= delta;
            if (chainTimer <= 0) {
                chainCount = 0;
            }
        }
    }

    public void addScore(int basePoints, float chainWindow) {
        chainCount++;
        currentChainWindow = chainWindow;
        chainTimer = chainWindow;
        score += basePoints * Math.min(chainCount, MAX_CHAIN_MULTIPLIER);
        if (score > highScore) highScore = score;
    }

    public void addScore(int basePoints) {
        addScore(basePoints, currentChainWindow);
    }

    public void reset() {
        score = 0;
        chainCount = 0;
        chainTimer = 0;
        currentChainWindow = DEFAULT_CHAIN_WINDOW;
    }

    public int getScore() { return score; }
    public int getHighScore() { return highScore; }
    public int getChainCount() { return chainCount; }
    public float getChainTimerFraction() { return currentChainWindow > 0 ? chainTimer / currentChainWindow : 0; }
}
