package whitelabeltest.gamemanagers;

import com.badlogic.gdx.utils.Pool;
import whitelabeltest.enemy.*;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.BasicEnemyBullet;
import whitelabeltest.enemy.bullets.DrifterBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.LaserBullet;
import whitelabeltest.enemy.bullets.OrbitingBullet;
import whitelabeltest.enemy.bullets.SineBullet;
import whitelabeltest.enemy.firingpatterns.ExplodingAimedBullet;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.powerups.WeaponPowerup;
import whitelabeltest.player.weapons.*;

public class ObjectPools {
    public static final Pool<BasicWeapon> weaponPool = new Pool<BasicWeapon>() {
        @Override protected BasicWeapon newObject() { return new BasicWeapon(); }
    };
    public static final Pool<WaveBlastWeapon> fastWeaponPool = new Pool<WaveBlastWeapon>() {
        @Override protected WaveBlastWeapon newObject() { return new WaveBlastWeapon(); }
    };
    public static final Pool<ThunderWhipWeapon> waveWeaponPool = new Pool<ThunderWhipWeapon>() {
        @Override protected ThunderWhipWeapon newObject() { return new ThunderWhipWeapon(); }
    };
    public static final Pool<OrbitWeapon> orbitWeaponPool = new Pool<OrbitWeapon>() {
        @Override protected OrbitWeapon newObject() { return new OrbitWeapon(); }
    };
    public static final Pool<HomingWeapon> homingWeaponPool = new Pool<HomingWeapon>() {
        @Override protected HomingWeapon newObject() { return new HomingWeapon(); }
    };

    public static final Pool<BasicEnemyBullet> basicEnemyBulletPool = new Pool<BasicEnemyBullet>() {
        @Override protected BasicEnemyBullet newObject() { return new BasicEnemyBullet(); }
    };
    public static final Pool<AimedEnemyBullet> aimedBulletPool = new Pool<AimedEnemyBullet>() {
        @Override protected AimedEnemyBullet newObject() { return new AimedEnemyBullet(); }
    };
    public static final Pool<ExplodingAimedBullet> explodingAimedBulletPool = new Pool<ExplodingAimedBullet>() {
        @Override protected ExplodingAimedBullet newObject() { return new ExplodingAimedBullet(); }
    };
    public static final Pool<DrifterBullet> drifterBulletPool = new Pool<DrifterBullet>() {
        @Override protected DrifterBullet newObject() { return new DrifterBullet(); }
    };
    public static final Pool<SineBullet> sineBulletPool = new Pool<SineBullet>() {
        @Override protected SineBullet newObject() { return new SineBullet(); }
    };
    public static final Pool<OrbitingBullet> orbitingBulletPool = new Pool<OrbitingBullet>() {
        @Override protected OrbitingBullet newObject() { return new OrbitingBullet(); }
    };
    public static final Pool<LaserBullet> laserBulletPool = new Pool<LaserBullet>() {
        @Override protected LaserBullet newObject() { return new LaserBullet(); }
    };
    public static final Pool<GenericEnemy> genericEnemyPool = new Pool<GenericEnemy>() {
        @Override protected GenericEnemy newObject() { return new GenericEnemy(); }
    };
    public static final Pool<WeaponPowerup> weaponPowerupPool = new Pool<WeaponPowerup>() {
        @Override protected WeaponPowerup newObject() { return new WeaponPowerup(); }
    };
    public static final Pool<ExplosionEffect> explosionPool = new Pool<ExplosionEffect>() {
        @Override protected ExplosionEffect newObject() { return new ExplosionEffect(); }
    };
    public static final Pool<PlayerTrailEffect> trailPool = new Pool<PlayerTrailEffect>() {
        @Override protected PlayerTrailEffect newObject() { return new PlayerTrailEffect(); }
    };

    public static void freeWeapon(Weapon w) {
        if (w instanceof BasicWeapon) weaponPool.free((BasicWeapon)w);
        else if (w instanceof WaveBlastWeapon) fastWeaponPool.free((WaveBlastWeapon)w);
        else if (w instanceof ThunderWhipWeapon) waveWeaponPool.free((ThunderWhipWeapon)w);
        else if (w instanceof OrbitWeapon) orbitWeaponPool.free((OrbitWeapon)w);
        else if (w instanceof HomingWeapon) homingWeaponPool.free((HomingWeapon)w);
    }

    public static void freeEnemyBullet(EnemyBullet b) {
        if (b instanceof BasicEnemyBullet) basicEnemyBulletPool.free((BasicEnemyBullet)b);
        else if (b instanceof AimedEnemyBullet) aimedBulletPool.free((AimedEnemyBullet)b);
        else if (b instanceof ExplodingAimedBullet) explodingAimedBulletPool.free((ExplodingAimedBullet)b);
        else if (b instanceof DrifterBullet) drifterBulletPool.free((DrifterBullet)b);
        else if (b instanceof SineBullet) sineBulletPool.free((SineBullet)b);
        else if (b instanceof OrbitingBullet) orbitingBulletPool.free((OrbitingBullet)b);
        else if (b instanceof LaserBullet) laserBulletPool.free((LaserBullet)b);
    }

    public static void freeEnemy(Enemy e) {
        if (e instanceof GenericEnemy) genericEnemyPool.free((GenericEnemy)e);
    }

    public static void freePowerup(Powerup p) {
        if (p instanceof WeaponPowerup) weaponPowerupPool.free((WeaponPowerup)p);
    }

    public static void freeExplosion(ExplosionEffect e) {
        explosionPool.free(e);
    }

    public static void freeTrail(PlayerTrailEffect t) {
        trailPool.free(t);
    }
}
