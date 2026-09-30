package whitelabeltest.gamemanagers.spawning;

/** Scoring/difficulty tuning loaded from balance.json: bonuses, rank thresholds, bomb damage, gem
 *  value, chain window. */
public class GameBalance {
    public static class RankThresholds {
        public float s;
        public float a;
        public float b;
        public float c;
    }

    public float bombCooldown;
    public int bombDamage;
    public int bombBonusPerUnusedBomb;
    public float bossTimeBonusParSeconds;
    public int bossTimeBonusPerSecond;
    public float chainRankTarget;
    public float defaultChainWindow;
    public int gemPoints;
    public int gemsPerEnemyHealth;
    // Cap on gems physically spawned per enemy. Past it each gem represents several (see
    // PointGem.getRepresents()), so points and gem counts are unchanged.
    public int maxGemsPerEnemy = 60;
    // Dropped gems scale in size and value with how close the player was to the dying enemy; see
    // gemScaleForDistance().
    public float gemMinScale = 0.75f;
    public float gemMaxScale = 1.75f;
    public float gemFullDistance = 8f;
    public RankThresholds rankThresholds;

    public GameBalance() {}

    /** gemMaxScale at distance 0 (touching the enemy's nearest edge), falling linearly to gemMinScale
     *  at gemFullDistance or more. */
    public float gemScaleForDistance(float distance) {
        float t = gemFullDistance <= 0f ? 1f : Math.max(0f, Math.min(1f, distance / gemFullDistance));
        return gemMaxScale + (gemMinScale - gemMaxScale) * t;
    }
}
