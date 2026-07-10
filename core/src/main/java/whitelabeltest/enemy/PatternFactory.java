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
    public static MovementPattern createMovement(String type, float speed, float worldHeight, float movementAngle, float stopDistance, float spawnCenterX) {
        if (type == null) return new StraightMovement(speed, movementAngle);

        switch (type) {
            case "ZigZag": return new ZigZagMovement(speed * 1.5f, speed, movementAngle);
            case "Seeking": return new SeekingMovement(speed, stopDistance, movementAngle);
            case "Spline": return new SplineMovement(worldHeight, 6.0f, movementAngle, spawnCenterX);
            default: return new StraightMovement(speed, movementAngle);
        }
    }

    public static MovementPattern createMovement(EnemyDefinition enemyDef, MovementPatternDef def, float worldHeight, float spawnCenterX) {
        if (def == null) return new NoMovement();

        if ("Sequence".equals(def.type)) {
            if (def.patterns == null || def.patterns.size == 0) return new NoMovement();
            Array<MovementPattern> mps = new Array<>();
            float[] durations = new float[def.patterns.size];
            for (int i = 0; i < def.patterns.size; i++) {
                MovementPatternDef sub = def.patterns.get(i);
                mps.add(createMovement(enemyDef, sub, worldHeight, spawnCenterX));
                durations[i] = sub.duration > 0 ? sub.duration : 3.0f;
            }
            return new SequencedMovementPattern(mps, durations);
        }

        if ("Squadron".equals(def.type)) {
            if (def.pattern == null) return new NoMovement();
            MovementPattern leader = createMovement(enemyDef, def.pattern, worldHeight, spawnCenterX - def.offsetX);
            return new SquadronMovement(leader, def.offsetX, def.offsetY);
        }

        if ("MoveToPoint".equals(def.type)) {
            float speed = def.speed > 0 ? def.speed : enemyDef.speed;
            float targetX = !Float.isNaN(def.targetX) ? def.targetX : spawnCenterX;
            float targetY = !Float.isNaN(def.targetY) ? def.targetY : 0f;
            float stopDistance = def.stopDistance > 0 ? def.stopDistance : MoveToPointMovement.DEFAULT_STOP_DISTANCE;
            return new MoveToPointMovement(speed, targetX, targetY, stopDistance);
        }

        float speed = def.speed > 0 ? def.speed : enemyDef.speed;
        float angle = !Float.isNaN(def.movementAngle) ? def.movementAngle : enemyDef.movementAngle;
        float stopDistance = def.stopDistance > 0 ? def.stopDistance : enemyDef.stopDistance;
        return createMovement(def.type, speed, worldHeight, angle, stopDistance, spawnCenterX);
    }

    public static FiringPattern createFiring(String type, float fireRate) {
        return createFiring(type, fireRate, -1f, -1f);
    }

    public static FiringPattern createFiring(String type, float fireRate, float bulletSize) {
        return createFiring(type, fireRate, bulletSize, -1f);
    }

    public static FiringPattern createFiring(String type, float fireRate, float bulletSize, float bulletSpeed) {
        return createFiring(type, fireRate, bulletSize, bulletSpeed, null);
    }

    public static FiringPattern createFiring(String type, float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        return createFiring(type, fireRate, bulletSize, bulletSpeed, spriteOverride, -1f, -1);
    }

    public static FiringPattern createFiring(String type, float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float spreadDegrees, int numBullets) {
        if (type == null) return new NoFiring();

        switch (type) {
            case "Aimed": return new AimedFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride);
            case "SelfDestruct": return new SelfDestructFiring(3.0f, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 4f), spriteOverride);
            case "ExplodingAimed": return new ExplodingAimedFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 6f), spriteOverride);
            case "BurstAimed": return new BurstAimedFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride);
            case "QuarterCircle": return new QuarterCircleFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride,
                resolve(spreadDegrees, 90f), resolve(numBullets, 9));
            case "Sweep": return new SweepFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride);
            case "SineWave": return new SineWaveFiring(fireRate, resolve(bulletSize, 0.2f), resolve(bulletSpeed, 5f), spriteOverride);
            case "Orbiting": return new OrbitingFiring(fireRate, resolve(bulletSize, 0.5f), resolve(bulletSpeed, 4f), spriteOverride);
            default: return new NoFiring();
        }
    }

    private static float resolve(float value, float defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static int resolve(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
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
                return new SpawnEnemyFiring(def.spawnType, def.fireRate);
            default:
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def);
                return createFiring(def.type, def.fireRate, def.bulletSize, def.bulletSpeed, spriteOverride, def.spreadDegrees, def.numBullets);
        }
    }

    private static Animation<TextureRegion> buildBulletAnimation(EnemyDefinition enemyDef, FiringPatternDef def) {
        if (def.bulletTexture == null) return null;
        Texture texture = EnemySpawnRegistry.getTexture(def.bulletTexture);
        if (texture == null) return null;

        int frameCount = def.bulletFrameCount > 0 ? def.bulletFrameCount : (enemyDef != null ? enemyDef.bulletFrameCount : 1);
        int columns = def.bulletColumns >= 0 ? def.bulletColumns : (enemyDef != null ? enemyDef.bulletColumns : 0);
        int rows = def.bulletRows > 0 ? def.bulletRows : (enemyDef != null ? enemyDef.bulletRows : 1);
        float frameDuration = def.bulletFrameDuration > 0 ? def.bulletFrameDuration : (enemyDef != null ? enemyDef.bulletFrameDuration : 0.1f);

        return AnimationCache.get(texture, columns > 0 ? columns : frameCount, rows, frameCount, frameDuration, Animation.PlayMode.LOOP);
    }
}
