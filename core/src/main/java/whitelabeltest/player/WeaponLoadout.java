package whitelabeltest.player;

/** The three starting weapon-slot pairings offered on WeaponSelectScreen before a run begins -
 *  WaveBlastWeapon is deliberately left out of all three, same as it always has been: it's only
 *  ever obtained mid-run from a powerup, never a starting choice. Slot A/B map directly to
 *  Player.setSlotWeapon(0, ...)/setSlotWeapon(1, ...) in Player.reset(WeaponLoadout). */
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
