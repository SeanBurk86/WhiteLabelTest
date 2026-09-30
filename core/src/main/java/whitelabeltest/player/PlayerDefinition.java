package whitelabeltest.player;

/** player.json: sprites (ship, halo states, death, reflect shield), hitbox sizes, speed and
 *  starting lives/bombs/max weapon level. */
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
    public SpriteDef basicHaloDetach;
    public SpriteDef thunderHyperHaloShrink1;
    public SpriteDef thunderHyperHaloShrink2;
    public SpriteDef thunderHyperHaloShrink3;
    public SpriteDef thunderHyperHaloShrink4;
    public SpriteDef thunderHaloBomb;
    public SpriteDef playerDeath;
    public SpriteDef reflectShield;

    public float hitboxSize;
    public float haloHitboxSize;
    public float movementSpeed;

    public int baseMaxBombs;
    public int startingLives;
    public int maxWeaponLevel;

    public PlayerDefinition() {}
}
