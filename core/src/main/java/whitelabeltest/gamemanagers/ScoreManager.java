package whitelabeltest.gamemanagers;

import com.badlogic.gdx.utils.ObjectMap;

/** Score, chain and run statistics. See the README's "Scoring" section. */
public class ScoreManager {
    // Chain window used until a weapon supplies its own (GameBalance.defaultChainWindow).
    private final float defaultChainWindow;

    private int score;
    private int highScore;
    private int chainCount;
    private int chainValueSum;
    private float chainTimer;
    private float currentChainWindow;
    // Kills this run (every kill passes through GameController.destroyEnemy()); excludes enemies
    // that leave the screen alive.
    private int enemiesDestroyed;
    // Highest chain reached this run.
    private int maxChainCount;
    // Point gems collected this run (for the gemsCollected condition).
    private int gemsCollected;
    // Kills by EnemyDefinition id (for the enemyTypeDestroyed condition).
    private final ObjectMap<String, Integer> enemiesDestroyedByType = new ObjectMap<>();
    // Kills by spawn group / Trigger.id (for the spawnDestroyed condition).
    private final ObjectMap<String, Integer> enemiesDestroyedByGroup = new ObjectMap<>();

    public ScoreManager(float defaultChainWindow) {
        this.defaultChainWindow = defaultChainWindow;
        this.currentChainWindow = defaultChainWindow;
    }

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
        if (chainCount > maxChainCount) maxChainCount = chainCount;
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

    /** Ends the current chain (on every life lost); banked score stays. */
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

    public void registerEnemyDestroyed(String definitionId) {
        enemiesDestroyed++;
        if (definitionId != null) {
            enemiesDestroyedByType.put(definitionId, enemiesDestroyedByType.get(definitionId, 0) + 1);
        }
    }

    public void registerGemCollected() {
        registerGemCollected(1);
    }

    /** @param count gems just collected (see PointGem.getRepresents()). */
    public void registerGemCollected(int count) {
        gemsCollected += count;
    }

    public void reset() {
        score = 0;
        chainCount = 0;
        chainValueSum = 0;
        chainTimer = 0;
        currentChainWindow = defaultChainWindow;
        enemiesDestroyed = 0;
        maxChainCount = 0;
        gemsCollected = 0;
        enemiesDestroyedByType.clear();
        enemiesDestroyedByGroup.clear();
    }

    public int getScore() { return score; }
    public int getHighScore() { return highScore; }
    public int getChainCount() { return chainCount; }
    public float getChainTimerFraction() { return currentChainWindow > 0 ? chainTimer / currentChainWindow : 0; }
    public int getEnemiesDestroyed() { return enemiesDestroyed; }
    public int getMaxChainCount() { return maxChainCount; }
    public int getGemsCollected() { return gemsCollected; }
    public int getEnemiesDestroyedByType(String definitionId) { return enemiesDestroyedByType.get(definitionId, 0); }

    /** Counts a kill toward its spawn group (Trigger.id). */
    public void registerGroupDestroyed(String group) {
        enemiesDestroyedByGroup.put(group, enemiesDestroyedByGroup.get(group, 0) + 1);
    }

    public int getGroupDestroyed(String group) { return enemiesDestroyedByGroup.get(group, 0); }

    /** Resets a group's tally when its spawn trigger fires, so kills from an earlier attempt (a
     *  checkpoint restart) don't count. */
    public void clearGroupDestroyed(String group) { enemiesDestroyedByGroup.remove(group); }
}
