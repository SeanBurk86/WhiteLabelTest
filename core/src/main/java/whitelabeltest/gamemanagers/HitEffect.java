package whitelabeltest.gamemanagers;

/** A one-shot animation played wherever a weapon's bullet lands a hit - see
 *  WeaponDefinition.hitTexture/hitSize/etc and CollisionManager.checkBulletEnemyCollisions,
 *  which spawns one per hit for any weapon whose definition sets a hit animation. */
public class HitEffect extends SingleShotAnimation {
}
