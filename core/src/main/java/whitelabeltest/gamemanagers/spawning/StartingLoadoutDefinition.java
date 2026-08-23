package whitelabeltest.gamemanagers.spawning;

import com.badlogic.gdx.utils.ObjectMap;

/** Optional fixed starting loadout for a StageSequenceDefinition (e.g. "tutorial" in
 *  assets/data/stage_sequences.json) - see GameController.applyStartingLoadout(). null (the
 *  default/unset value on a sequence) means the run instead uses whatever loadout the player
 *  picked on WeaponSelectScreen, same as "campaign" always has.
 *
 *  Weapon ids (slotAWeaponId/slotBWeaponId/weaponLevels' keys) are Player.weaponById()'s: exactly
 *  one of "BasicWeapon", "WaveBlastWeapon", "OrbitWeapon", "Thunderbolt". Levels are clamped to
 *  [0, player.json's maxWeaponLevel] by Player.setWeaponLevel() - a weapon doesn't need an entry
 *  in weaponLevels to be equipped in a slot; it just starts at level 1 like an ordinary new pickup. */
public class StartingLoadoutDefinition {
    public String slotAWeaponId;
    public String slotBWeaponId;
    public ObjectMap<String, Integer> weaponLevels;
    // 0 (the default) leaves Player's ordinary bomb cap (player.json's baseMaxBombs) untouched -
    // only set this if numBombs needs to exceed that cap.
    public int maxBombs;
    public int numBombs;
    public int numLives;

    public StartingLoadoutDefinition() {}
}
