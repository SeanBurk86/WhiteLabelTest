package whitelabeltest.player.weapons;

public class WeaponDefinition {
    public String id;
    public String type; // e.g., "Direct", "Spline", "Orbit"
    public String texture;
    public int frameCount;
    public int columns = 0;
    public int rows = 1;
    public float frameDuration = 0.05f;
    public float size;

    // Indexed by level - 1. Each must have one entry per weapon level.
    public int[] damageByLevel;
    public float[] fireRateByLevel;
    public float[] speedByLevel;

    public float chainWindow = 2.0f;
    public float shootSpeedMultiplier = 0.75f;

    // Orbit specific
    public float radius;
    public float rotationSpeed;

    public WeaponDefinition() {}

    public int getDamage(int level) { return damageByLevel[levelIndex(level, damageByLevel.length)]; }
    public float getFireRate(int level) { return fireRateByLevel[levelIndex(level, fireRateByLevel.length)]; }
    public float getSpeed(int level) { return speedByLevel[levelIndex(level, speedByLevel.length)]; }

    private static int levelIndex(int level, int arrayLength) {
        int index = level - 1;
        if (index < 0) return 0;
        if (index >= arrayLength) return arrayLength - 1;
        return index;
    }
}
