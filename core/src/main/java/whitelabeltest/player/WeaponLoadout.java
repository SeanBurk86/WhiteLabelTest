package whitelabeltest.player;

/** The starting weapon pairs offered on WeaponSelectScreen (slot A, slot B). */
public enum WeaponLoadout {
    BASIC_THUNDERBOLT("RAIN.sh & LIGHTNING.bat", "BasicWeapon", "Thunderbolt"),
    BASIC_ORBIT("RAIN.sh & MOON.cmd", "BasicWeapon", "OrbitWeapon"),
    THUNDERBOLT_ORBIT("LIGHTNING.bat & MOON.cmd", "Thunderbolt", "OrbitWeapon");

    public final String label;
    public final String slotAWeaponId;
    public final String slotBWeaponId;

    WeaponLoadout(String label, String slotAWeaponId, String slotBWeaponId) {
        this.label = label;
        this.slotAWeaponId = slotAWeaponId;
        this.slotBWeaponId = slotBWeaponId;
    }
}
