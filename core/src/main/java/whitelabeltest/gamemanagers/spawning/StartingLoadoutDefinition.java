package whitelabeltest.gamemanagers.spawning;

import com.badlogic.gdx.utils.ObjectMap;

/** A fixed starting loadout for a stage sequence (see GameController.applyStartingLoadout()).
 *  Weapon ids: "BasicWeapon", "WaveBlastWeapon", "OrbitWeapon", "Thunderbolt". Levels are clamped to
 *  [0, maxWeaponLevel]; a weapon without an entry starts at level 1. */
public class StartingLoadoutDefinition {
    public String slotAWeaponId;
    public String slotBWeaponId;
    public ObjectMap<String, Integer> weaponLevels;
    // 0 keeps player.json's baseMaxBombs; set only if numBombs must exceed it.
    public int maxBombs;
    public int numBombs;
    public int numLives;

    public StartingLoadoutDefinition() {}
}
