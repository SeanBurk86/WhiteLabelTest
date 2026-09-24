package whitelabeltest.gamemanagers.spawning;

/** Data-driven scoring/difficulty tuning - end-of-level bonuses, rank thresholds, bomb damage,
 *  point-gem value, default chain window - loaded from balance.json the same way weapons/enemies/
 *  player are (see AssetManager), rather than scattered as `private static final` constants across
 *  GameController/CollisionManager/ScoreManager. A plain reflection-parsed POJO (see
 *  PlayerDefinition) since it's a single fixed-shape object with no variant arrays to hand-parse. */
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
    // Most gems one enemy actually spawns. A big enemy's full share (health / gemsPerEnemyHealth - 1,200 for a
    // 12,000-health boss) would otherwise all be simulated and drawn at once; past this cap each gem stands for
    // several instead (see PointGem.getRepresents()), so the points and the gems-collected count are unchanged.
    public int maxGemsPerEnemy = 60;
    // Gems dropped by a dying enemy scale with how close the player was to it when it died - both their
    // size and their point value (see gemScaleForDistance()): gemMaxScale with the player right up against
    // the enemy, easing linearly down to gemMinScale at gemFullDistance world units away or more.
    // Defaulted here so a balance.json without them behaves the same as one that spells the defaults out.
    public float gemMinScale = 0.75f;
    public float gemMaxScale = 1.75f;
    public float gemFullDistance = 8f;
    public RankThresholds rankThresholds;

    public GameBalance() {}

    /** The size/value multiplier for a gem dropped when the player was `distance` world units from the dying
     *  enemy (measured to the enemy's nearest edge, so 0 = touching): gemMaxScale at 0, gemMinScale at
     *  gemFullDistance or beyond, linear in between. */
    public float gemScaleForDistance(float distance) {
        float t = gemFullDistance <= 0f ? 1f : Math.max(0f, Math.min(1f, distance / gemFullDistance));
        return gemMaxScale + (gemMinScale - gemMaxScale) * t;
    }
}
