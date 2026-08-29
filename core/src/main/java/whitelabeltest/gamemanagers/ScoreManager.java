package whitelabeltest.gamemanagers;

import com.badlogic.gdx.utils.ObjectMap;

public class ScoreManager {
    // Fallback chain window used until the first addScore(basePoints, chainWindow)/registerWeaponHit
    // call establishes a real per-weapon one - see GameBalance.defaultChainWindow (balance.json).
    private final float defaultChainWindow;

    private int score;
    private int highScore;
    private int chainCount;
    private int chainValueSum;
    private float chainTimer;
    private float currentChainWindow;
    // Total enemies killed this run - see GameController.destroyEnemy(), the single choke point
    // every kill (bullet, halo dash, thunderbolt, bomb) passes through. Distinct from enemies that
    // merely fly off-screen alive, so it reflects actual kills for UIManager.drawLevelComplete's
    // "ICE DELETED" row.
    private int enemiesDestroyed;
    // Highest chainCount reached this run, tracked alongside it in addScore() - see
    // UIManager.drawLevelComplete's "PEAK CHAIN" row.
    private int maxChainCount;
    // Total point gems collected this run - see CollisionManager.checkPlayerGemCollisions()
    // (the only call site) and SpawnScheduler's "gemsCollected" gate condition, which is the
    // reason this is tracked at all (nothing else currently reads it).
    private int gemsCollected;
    // Kills broken down by EnemyDefinition id, alongside the flat enemiesDestroyed total above - see
    // whitelabeltest.gamemanagers.trigger.Condition's "enemyTypeDestroyed" type, the reason this
    // exists at all (nothing else currently reads it).
    private final ObjectMap<String, Integer> enemiesDestroyedByType = new ObjectMap<>();

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

    public void registerEnemyDestroyed(String definitionId) {
        enemiesDestroyed++;
        if (definitionId != null) {
            enemiesDestroyedByType.put(definitionId, enemiesDestroyedByType.get(definitionId, 0) + 1);
        }
    }

    public void registerGemCollected() {
        gemsCollected++;
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
    }

    public int getScore() { return score; }
    public int getHighScore() { return highScore; }
    public int getChainCount() { return chainCount; }
    public float getChainTimerFraction() { return currentChainWindow > 0 ? chainTimer / currentChainWindow : 0; }
    public int getEnemiesDestroyed() { return enemiesDestroyed; }
    public int getMaxChainCount() { return maxChainCount; }
    public int getGemsCollected() { return gemsCollected; }
    public int getEnemiesDestroyedByType(String definitionId) { return enemiesDestroyedByType.get(definitionId, 0); }
}
