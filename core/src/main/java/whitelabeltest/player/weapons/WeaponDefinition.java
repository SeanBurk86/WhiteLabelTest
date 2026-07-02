package whitelabeltest.player.weapons;

public class WeaponDefinition {
    public String id;
    public String type; // e.g., "Direct", "Spline", "Orbit", "Homing"
    public String texture;
    public int frameCount;
    public float size;
    public int baseDamage;
    public int damagePerLevel;
    public float baseFireRate;
    public float fireRatePerLevel;
    public float speed;

    public float chainWindow = 2.0f;

    // Orbit specific
    public float radius;
    public float rotationSpeed;

    public WeaponDefinition() {}
}
