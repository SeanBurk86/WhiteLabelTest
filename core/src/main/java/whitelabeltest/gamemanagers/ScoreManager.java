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
    // Kills per spawn group (Trigger.id) - see registerGroupDestroyed(). Kept apart from the by-type counts since a
    // group is one specific spawn (a single enemy or one wave), not every enemy of that type.
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
        registerGemCollected(1);
    }

    /** @param count how many gems were just collected - see PointGem.getRepresents(). */
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

    /** Counts a kill toward the spawn group (Trigger.id) its enemy came from. */
    public void registerGroupDestroyed(String group) {
        enemiesDestroyedByGroup.put(group, enemiesDestroyedByGroup.get(group, 0) + 1);
    }

    public int getGroupDestroyed(String group) { return enemiesDestroyedByGroup.get(group, 0); }

    /** Starts a group's count over - called when its spawn trigger fires, so a kill tally left from an earlier
     *  attempt at the same spawn (a checkpoint restart replays it) never counts toward this one. */
    public void clearGroupDestroyed(String group) { enemiesDestroyedByGroup.remove(group); }
}
