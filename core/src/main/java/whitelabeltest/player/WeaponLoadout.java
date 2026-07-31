package whitelabeltest.player;

/** The three starting weapon-slot pairings offered on WeaponSelectScreen before a run begins -
 *  WaveBlastWeapon is deliberately left out of all three, same as it always has been: it's only
 *  ever obtained mid-run from a powerup, never a starting choice. Slot A/B map directly to
 *  Player.setSlotWeapon(0, ...)/setSlotWeapon(1, ...) in Player.reset(WeaponLoadout). */
public enum WeaponLoadout {
    BASIC_THUNDERBOLT("Basic & Thunderbolt", "BasicWeapon", "Thunderbolt"),
    BASIC_ORBIT("Basic & Orbit", "BasicWeapon", "OrbitWeapon"),
    THUNDERBOLT_ORBIT("Thunderbolt & Orbit", "Thunderbolt", "OrbitWeapon");

    public final String label;
    public final String slotAWeaponId;
    public final String slotBWeaponId;

    WeaponLoadout(String label, String slotAWeaponId, String slotBWeaponId) {
        this.label = label;
        this.slotAWeaponId = slotAWeaponId;
        this.slotBWeaponId = slotBWeaponId;
    }
}
