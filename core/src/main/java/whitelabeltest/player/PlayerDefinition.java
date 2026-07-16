package whitelabeltest.player;

/** Data-driven player appearance: texture/size/sprite-sheet layout for the player, its halo,
 *  its death animation and the orbit weapon's reflect shield, plus the player's own hitbox and
 *  halo (graze) hitbox sizes. Loaded from player.json the same way weapons/enemies are - a plain
 *  reflection-parsed POJO (see WeaponDefinition/EnemyDefinition) rather than a Json.Serializable,
 *  since it's a single fixed-shape object with no variant arrays to hand-parse. */
public class PlayerDefinition {
    public static class SpriteDef {
        public String texture;
        public float size;
        public int frameCount;
        public int columns;
        public int rows = 1;
    }

    public SpriteDef player;
    public SpriteDef playerHalo;
    public SpriteDef playerDeath;
    public SpriteDef reflectShield;

    public float hitboxSize;
    public float haloHitboxSize;

    public PlayerDefinition() {}
}
