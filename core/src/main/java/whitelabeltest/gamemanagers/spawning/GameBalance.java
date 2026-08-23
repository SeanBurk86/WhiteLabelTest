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
    public RankThresholds rankThresholds;

    public GameBalance() {}
}
