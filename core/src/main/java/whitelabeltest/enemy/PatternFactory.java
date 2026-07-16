package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.movementpatterns.StraightMovement;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.enemy.movementpatterns.MoveToPointMovement;
import whitelabeltest.enemy.movementpatterns.NoMovement;
import whitelabeltest.enemy.movementpatterns.SeekingMovement;
import whitelabeltest.enemy.movementpatterns.SequencedMovementPattern;
import whitelabeltest.enemy.movementpatterns.SplineMovement;
import whitelabeltest.enemy.movementpatterns.SquadronMovement;
import whitelabeltest.enemy.firingpatterns.*;
import whitelabeltest.enemy.movementpatterns.ZigZagMovement;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.EnemySpawnRegistry;

public class PatternFactory {
    public static MovementPattern createMovement(MovementPatternDef def, float worldHeight, float spawnCenterX) {
        if (def == null || "None".equals(def.type)) return new NoMovement();

        if ("Sequence".equals(def.type)) {
            if (def.patterns == null || def.patterns.size == 0) return new NoMovement();
            Array<MovementPattern> mps = new Array<>();
            float[] durations = new float[def.patterns.size];
            for (int i = 0; i < def.patterns.size; i++) {
                MovementPatternDef sub = def.patterns.get(i);
                mps.add(createMovement(sub, worldHeight, spawnCenterX));
                durations[i] = sub.duration > 0 ? sub.duration : 3.0f;
            }
            return new SequencedMovementPattern(mps, durations);
        }

        if ("Squadron".equals(def.type)) {
            if (def.pattern == null) return new NoMovement();
            MovementPattern leader = createMovement(def.pattern, worldHeight, spawnCenterX - def.offsetX);
            return new SquadronMovement(leader, def.offsetX, def.offsetY);
        }

        float speed = def.speed > 0 ? def.speed : 0f;
        float angle = !Float.isNaN(def.movementAngle) ? def.movementAngle : MovementPattern.DEFAULT_ANGLE_DEG;

        if ("MoveToPoint".equals(def.type)) {
            float targetX = !Float.isNaN(def.targetX) ? def.targetX : spawnCenterX;
            float targetY = !Float.isNaN(def.targetY) ? def.targetY : 0f;
            float stopDistance = def.stopDistance > 0 ? def.stopDistance : MoveToPointMovement.DEFAULT_STOP_DISTANCE;
            return new MoveToPointMovement(speed, targetX, targetY, stopDistance);
        }

        switch (def.type) {
            case "ZigZag": return new ZigZagMovement(speed * 1.5f, speed, angle);
            case "Seeking": {
                float stopDistance = def.stopDistance > 0 ? def.stopDistance : SeekingMovement.DEFAULT_STOP_DISTANCE;
                return new SeekingMovement(speed, stopDistance, angle);
            }
            case "Spline": return new SplineMovement(worldHeight, 6.0f, angle, spawnCenterX);
            default: return new StraightMovement(speed, angle);
        }
    }

    private static float resolve(float value, float defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static int resolve(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static FiringPattern createFiring(String type, float fireRate, float bulletSize, float bulletSpeed, int bulletDamage, Animation<TextureRegion> spriteOverride, float spreadDegrees, int numBullets, float offsetX, float offsetY) {
        if (type == null) return new NoFiring();

        switch (type) {
            case "Aimed": return new AimedFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride, offsetX, offsetY, bulletDamage);
            case "SelfDestruct": return new SelfDestructFiring(3.0f, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 4f), spriteOverride, offsetX, offsetY, bulletDamage);
            case "ExplodingAimed": return new ExplodingAimedFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 6f), spriteOverride, offsetX, offsetY, bulletDamage);
            case "BurstAimed": return new BurstAimedFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride, offsetX, offsetY, bulletDamage);
            case "QuarterCircle": return new QuarterCircleFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride,
                resolve(spreadDegrees, 90f), resolve(numBullets, 9), offsetX, offsetY, bulletDamage);
            case "Sweep": return new SweepFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride, offsetX, offsetY, bulletDamage);
            case "SineWave": return new SineWaveFiring(fireRate, resolve(bulletSize, 0.2f), resolve(bulletSpeed, 5f), spriteOverride, offsetX, offsetY, bulletDamage);
            case "Orbiting": return new OrbitingFiring(fireRate, resolve(bulletSize, 0.5f), resolve(bulletSpeed, 4f), spriteOverride, offsetX, offsetY, bulletDamage);
            default: return new NoFiring();
        }
    }

    public static FiringPattern createFiring(EnemyDefinition enemyDef, FiringPatternDef def) {
        if (def == null) return new NoFiring();

        switch (def.type) {
            case "Sequence": {
                if (def.patterns == null || def.patterns.size == 0) return new NoFiring();
                Array<FiringPattern> fps = new Array<>();
                float[] durations = new float[def.patterns.size];
                for (int i = 0; i < def.patterns.size; i++) {
                    FiringPatternDef sub = def.patterns.get(i);
                    fps.add(createFiring(enemyDef, sub));
                    durations[i] = sub.duration > 0 ? sub.duration : 3.0f;
                }
                return new SequencedFiringPattern(fps, durations);
            }
            case "Combined": {
                if (def.patterns == null || def.patterns.size == 0) return new NoFiring();
                Array<FiringPattern> fps = new Array<>();
                for (FiringPatternDef sub : def.patterns) {
                    fps.add(createFiring(enemyDef, sub));
                }
                return new CombinedFiringPattern(fps);
            }
            case "SpawnEnemy":
                return new SpawnEnemyFiring(def.spawnType, def.fireRate, def.offsetX, def.offsetY);
            case "AimedAtPoint": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float targetX = !Float.isNaN(def.targetX) ? def.targetX : 0f;
                float targetY = !Float.isNaN(def.targetY) ? def.targetY : 0f;
                return new PointAimedFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), spriteOverride, targetX, targetY, def.offsetX, def.offsetY, bulletDamage(def, bulletDef));
            }
            case "Laser": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float thickness = resolve(bulletSize(def, bulletDef), 0.3f);
                float length = def.length > 0 ? def.length : LaserFiring.DEFAULT_LENGTH;
                return new LaserFiring(def.fireRate, thickness, length, def.angularSpeed, def.fireAngle, def.duration, spriteOverride, def.offsetX, def.offsetY, bulletDamage(def, bulletDef));
            }
            case "Sweep": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float sweepDuration = def.sweepDuration > 0 ? def.sweepDuration : SweepFiring.DEFAULT_SWEEP_DURATION;
                float startAngle = !Float.isNaN(def.sweepStartAngle) ? def.sweepStartAngle : SweepFiring.DEFAULT_START_ANGLE;
                float endAngle = !Float.isNaN(def.sweepEndAngle) ? def.sweepEndAngle : SweepFiring.DEFAULT_END_ANGLE;
                return new SweepFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), spriteOverride, def.offsetX, def.offsetY, sweepDuration, startAngle, endAngle, bulletDamage(def, bulletDef));
            }
            default:
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                return createFiring(def.type, def.fireRate, bulletSize(def, bulletDef), bulletSpeed(def, bulletDef), bulletDamage(def, bulletDef), spriteOverride, def.spreadDegrees, def.numBullets, def.offsetX, def.offsetY);
        }
    }

    /** A pattern's own bulletSize/bulletSpeed always win; otherwise fall back to the referenced
     *  bullet definition's values, if any. */
    private static float bulletSize(FiringPatternDef def, BulletDef bulletDef) {
        return def.bulletSize > 0 ? def.bulletSize : (bulletDef != null ? bulletDef.bulletSize : -1f);
    }

    private static float bulletSpeed(FiringPatternDef def, BulletDef bulletDef) {
        return def.bulletSpeed > 0 ? def.bulletSpeed : (bulletDef != null ? bulletDef.bulletSpeed : -1f);
    }

    /** A pattern's own bulletDamage always wins; otherwise fall back to the referenced bullet
     *  definition's damage, defaulting to 1 if neither sets one. This is also the value a
     *  reflected bullet (see CollisionManager.checkShieldReflections) hits its target for. */
    private static int bulletDamage(FiringPatternDef def, BulletDef bulletDef) {
        if (def.bulletDamage > 0) return def.bulletDamage;
        return bulletDef != null ? bulletDef.damage : BulletDef.DEFAULT_DAMAGE;
    }

    /** Builds this pattern's own bullet animation — reusing the enemy's default bulletTexture
     *  when neither the pattern nor its referenced bullet definition sets one, but otherwise
     *  entirely self-contained: sheet layout and animation speed are resolved purely from this
     *  pattern (falling back to its bulletDef, if any), never from the enemy definition, so two
     *  patterns sharing a texture can still animate independently. */
    private static Animation<TextureRegion> buildBulletAnimation(EnemyDefinition enemyDef, FiringPatternDef def, BulletDef bulletDef) {
        String texturePath = def.bulletTexture != null ? def.bulletTexture
            : (bulletDef != null && bulletDef.bulletTexture != null ? bulletDef.bulletTexture
            : (enemyDef != null ? enemyDef.bulletTexture : null));
        if (texturePath == null) return null;
        Texture texture = EnemySpawnRegistry.getTexture(texturePath);
        if (texture == null) return null;

        int frameCount = def.bulletFrameCount > 0 ? def.bulletFrameCount
            : (bulletDef != null && bulletDef.bulletFrameCount > 0 ? bulletDef.bulletFrameCount : FiringPatternDef.DEFAULT_BULLET_FRAME_COUNT);
        int columns = def.bulletColumns >= 0 ? def.bulletColumns
            : (bulletDef != null && bulletDef.bulletColumns >= 0 ? bulletDef.bulletColumns : FiringPatternDef.DEFAULT_BULLET_COLUMNS);
        int rows = def.bulletRows > 0 ? def.bulletRows
            : (bulletDef != null && bulletDef.bulletRows > 0 ? bulletDef.bulletRows : FiringPatternDef.DEFAULT_BULLET_ROWS);
        float frameDuration = def.bulletFrameDuration > 0 ? def.bulletFrameDuration
            : (bulletDef != null && bulletDef.bulletFrameDuration > 0 ? bulletDef.bulletFrameDuration : FiringPatternDef.DEFAULT_BULLET_FRAME_DURATION);

        return AnimationCache.get(texture, columns > 0 ? columns : frameCount, rows, frameCount, frameDuration, Animation.PlayMode.LOOP);
    }
}