package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.movementpatterns.BounceMovement;
import whitelabeltest.enemy.movementpatterns.StraightMovement;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.enemy.movementpatterns.MoveToPointMovement;
import whitelabeltest.enemy.movementpatterns.NoMovement;
import whitelabeltest.enemy.movementpatterns.SeekingMovement;
import whitelabeltest.enemy.movementpatterns.SequencedMovementPattern;
import whitelabeltest.enemy.movementpatterns.SplineMovement;
import whitelabeltest.enemy.movementpatterns.SquadronMovement;
import whitelabeltest.enemy.movementpatterns.WaypointPathMovement;
import whitelabeltest.enemy.firingpatterns.*;
import whitelabeltest.enemy.movementpatterns.ZigZagMovement;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.spawning.EnemySpawnRegistry;

/** Builds live MovementPatterns and FiringPatterns from their definitions, resolving every unset
 *  field against the referenced BulletDef and then the type's default. */
public class PatternFactory {
    public static MovementPattern createMovement(MovementPatternDef def, float worldHeight, float spawnCenterX) {
        return createMovement(def, worldHeight, spawnCenterX, Float.NaN, Float.NaN);
    }

    /** @param formationOffsetX,formationOffsetY this spawn's slot in a squad, so one shared Squadron
     *  pattern serves every member; NaN falls back to the pattern's own offsetX/offsetY. */
    public static MovementPattern createMovement(MovementPatternDef def, float worldHeight, float spawnCenterX, float formationOffsetX, float formationOffsetY) {
        return createMovement(def, Float.NaN, worldHeight, spawnCenterX, formationOffsetX, formationOffsetY);
    }

    /** @param worldWidth only used by WaypointPath's flipX (mirrors across the centerline); NaN is fine
     *  otherwise. */
    public static MovementPattern createMovement(MovementPatternDef def, float worldWidth, float worldHeight, float spawnCenterX, float formationOffsetX, float formationOffsetY) {
        if (def == null || "None".equals(def.type)) return new NoMovement();

        if ("Sequence".equals(def.type)) {
            if (def.patterns == null || def.patterns.size == 0) return new NoMovement();
            Array<MovementPattern> mps = new Array<>();
            float[] durations = new float[def.patterns.size];
            for (int i = 0; i < def.patterns.size; i++) {
                MovementPatternDef sub = def.patterns.get(i);
                mps.add(createMovement(sub, worldWidth, worldHeight, spawnCenterX, formationOffsetX, formationOffsetY));
                durations[i] = sub.duration > 0 ? sub.duration : 3.0f;
            }
            return new SequencedMovementPattern(mps, durations);
        }

        if ("Squadron".equals(def.type)) {
            if (def.pattern == null) return new NoMovement();
            float offsetX = !Float.isNaN(formationOffsetX) ? formationOffsetX : def.offsetX;
            float offsetY = !Float.isNaN(formationOffsetY) ? formationOffsetY : def.offsetY;
            MovementPattern leader = createMovement(def.pattern, worldWidth, worldHeight, spawnCenterX - offsetX, Float.NaN, Float.NaN);
            return new SquadronMovement(leader, offsetX, offsetY);
        }

        if ("WaypointPath".equals(def.type)) {
            if (def.patterns == null || def.patterns.size == 0) return new NoMovement();
            Array<WaypointPathMovement.Leg> legs = new Array<>();
            float mirrorX = !Float.isNaN(worldWidth) ? worldWidth / 2f : spawnCenterX;
            for (MovementPatternDef leg : def.patterns) {
                if (!"MoveToPoint".equals(leg.type)) continue;
                float targetX = !Float.isNaN(leg.targetX) ? leg.targetX : spawnCenterX;
                float targetY = !Float.isNaN(leg.targetY) ? leg.targetY : 0f;
                if (def.flipX) targetX = 2f * mirrorX - targetX;
                if (def.flipY) targetY = worldHeight - targetY;
                float speed = leg.speed > 0 ? leg.speed : 0f;
                legs.add(new WaypointPathMovement.Leg(targetX, targetY, leg.tension, speed, leg.waitSeconds,
                    leg.orientation, leg.aimSpeed, leg.fixedAngle, leg.soundName, leg.soundVolume, leg.soundPitch,
                    leg.soundPitchVariation, leg.changeWeaponSet, leg.weaponSet));
            }
            if (legs.size == 0) return new NoMovement();
            return new WaypointPathMovement(legs, def.closePath, def.globalSpeed > 0 ? def.globalSpeed : 1f);
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
            case "Bounce": return new BounceMovement(speed, angle);
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

    private static FiringPattern createFiring(String type, float fireRate, float bulletSize, float bulletSpeed, int bulletDamage, Animation<TextureRegion> spriteOverride, float spreadDegrees, int numBullets, float offsetX, float offsetY, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
        if (type == null) return new NoFiring();

        switch (type) {
            case "SelfDestruct": return new SelfDestructFiring(3.0f, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 4f), spriteOverride, offsetX, offsetY, bulletDamage, speedProfile, hitboxSpec);
            case "ExplodingAimed": return new ExplodingAimedFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 6f), spriteOverride, offsetX, offsetY, bulletDamage, speedProfile, hitboxSpec);
            case "BurstAimed": return new BurstAimedFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride, offsetX, offsetY, bulletDamage, speedProfile, hitboxSpec);
            case "Sweep": return new SweepFiring(fireRate, resolve(bulletSize, 0.25f), resolve(bulletSpeed, 5f), spriteOverride, offsetX, offsetY, bulletDamage, speedProfile, hitboxSpec);
            case "SineWave": return new SineWaveFiring(fireRate, resolve(bulletSize, 0.2f), resolve(bulletSpeed, 5f), spriteOverride, offsetX, offsetY, bulletDamage, speedProfile, hitboxSpec);
            case "Feather": return new FeatherFiring(fireRate, resolve(bulletSize, 0.2f), resolve(bulletSpeed, 1.5f), spriteOverride, offsetX, offsetY, bulletDamage, speedProfile, hitboxSpec);
            // Orbiting's speed is a constant center drift, so acceleration doesn't apply.
            case "Orbiting": return new OrbitingFiring(fireRate, resolve(bulletSize, 0.5f), resolve(bulletSpeed, 4f), spriteOverride, offsetX, offsetY, bulletDamage);
            default: return new NoFiring();
        }
    }

    public static FiringPattern createFiring(EnemyDefinition enemyDef, FiringPatternDef def) {
        return createFiring(enemyDef, def, Float.NaN, Float.NaN);
    }

    /** @param worldWidth,worldHeight only used by Wall, PolkaDot and RadialNearMiss; NaN is fine otherwise. */
    public static FiringPattern createFiring(EnemyDefinition enemyDef, FiringPatternDef def, float worldWidth, float worldHeight) {
        if (def == null) return new NoFiring();

        switch (def.type) {
            case "Sequence": {
                if (def.patterns == null || def.patterns.size == 0) return new NoFiring();
                Array<FiringPattern> fps = new Array<>();
                float[] durations = new float[def.patterns.size];
                for (int i = 0; i < def.patterns.size; i++) {
                    FiringPatternDef sub = def.patterns.get(i);
                    fps.add(createFiring(enemyDef, sub, worldWidth, worldHeight));
                    durations[i] = sub.duration > 0 ? sub.duration : 3.0f;
                }
                return new SequencedFiringPattern(fps, durations);
            }
            case "Combined": {
                if (def.patterns == null || def.patterns.size == 0) return new NoFiring();
                Array<FiringPattern> fps = new Array<>();
                for (FiringPatternDef sub : def.patterns) {
                    fps.add(createFiring(enemyDef, sub, worldWidth, worldHeight));
                }
                return new CombinedFiringPattern(fps);
            }
            case "SpawnEnemy":
                return new SpawnEnemyFiring(def.spawnType, def.fireRate, def.offsetX, def.offsetY, def.spawnMovementPattern);
            case "Aimed": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                return new AimedFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), spriteOverride, def.offsetX, def.offsetY, bulletDamage(def, bulletDef), def.targetOffsetX, def.targetOffsetY,
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef));
            }
            case "BurstAimed": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                return new BurstAimedFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), spriteOverride, def.offsetX, def.offsetY, bulletDamage(def, bulletDef),
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef), def.phaseOffset, resolve(def.burstInterval, 0.15f));
            }
            case "QuarterCircle": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                return new QuarterCircleFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), spriteOverride,
                    resolve(def.spreadDegrees, 90f), resolve(def.numBullets, 9), def.offsetX, def.offsetY, bulletDamage(def, bulletDef), def.targetOffsetX, def.targetOffsetY,
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef), def.quarterCircleFixedAngle);
            }
            case "AimedAtPoint": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float targetX = !Float.isNaN(def.targetX) ? def.targetX : 0f;
                float targetY = !Float.isNaN(def.targetY) ? def.targetY : 0f;
                return new PointAimedFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), spriteOverride, targetX, targetY, def.offsetX, def.offsetY, bulletDamage(def, bulletDef),
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef));
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
                return new SweepFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), spriteOverride, def.offsetX, def.offsetY, sweepDuration, startAngle, endAngle, bulletDamage(def, bulletDef),
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef));
            }
            case "SineWave": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float amplitude = def.amplitude > 0 ? def.amplitude : SineWaveFiring.DEFAULT_AMPLITUDE;
                float frequency = def.frequency > 0 ? def.frequency : SineWaveFiring.DEFAULT_FREQUENCY;
                return new SineWaveFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.2f), resolve(bulletSpeed(def, bulletDef), 5f), spriteOverride, def.offsetX, def.offsetY, bulletDamage(def, bulletDef),
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef), amplitude, frequency);
            }
            case "Feather": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float amplitude = def.amplitude > 0 ? def.amplitude : FeatherFiring.DEFAULT_AMPLITUDE;
                float frequency = def.frequency > 0 ? def.frequency : FeatherFiring.DEFAULT_FREQUENCY;
                return new FeatherFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.2f), resolve(bulletSpeed(def, bulletDef), 1.5f), spriteOverride, def.offsetX, def.offsetY, bulletDamage(def, bulletDef),
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef), amplitude, frequency);
            }
            // Orbiting's speed is a constant center drift, so acceleration doesn't apply.
            case "Orbiting": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float orbitRadius = def.orbitRadius > 0 ? def.orbitRadius : OrbitingFiring.DEFAULT_ORBIT_RADIUS;
                float orbitSpeed = def.orbitSpeed > 0 ? def.orbitSpeed : OrbitingFiring.DEFAULT_ORBIT_SPEED;
                return new OrbitingFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.5f), resolve(bulletSpeed(def, bulletDef), 4f), spriteOverride, def.offsetX, def.offsetY, bulletDamage(def, bulletDef), orbitRadius, orbitSpeed);
            }
            case "Wall": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float marginX = def.wallMarginX > 0 ? def.wallMarginX : 0.25f;
                float spacing = def.wallSpacing > 0 ? def.wallSpacing : 0.4f;
                int gapLaneStart = def.gapLaneStart >= 0 ? def.gapLaneStart : 0;
                int gapLaneCount = def.gapLaneCount >= 0 ? def.gapLaneCount : 1;
                float wallFireRate = def.fireRate > 0 ? def.fireRate : 0.3f;
                return new WallFiring(resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), bulletDamage(def, bulletDef), spriteOverride,
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef), worldWidth, marginX, spacing, gapLaneStart, gapLaneCount, def.gapLaneSequence, wallFireRate);
            }
            case "PolkaDot": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float marginX = def.wallMarginX > 0 ? def.wallMarginX : 0.25f;
                float spacing = def.wallSpacing > 0 ? def.wallSpacing : 0.4f;
                float rowFireRate = def.fireRate > 0 ? def.fireRate : 0.3f;
                return new PolkaDotFiring(resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), bulletDamage(def, bulletDef), spriteOverride,
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef), worldWidth, marginX, spacing, rowFireRate);
            }
            case "Shape": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                float shapeFireRate = def.fireRate > 0 ? def.fireRate : 1f;
                return new ShapeFiring(shapeFireRate, resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 3f), spriteOverride, def.offsetX, def.offsetY, bulletDamage(def, bulletDef),
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef), def.shapePoints, resolve(def.numBullets, 1), Math.max(0f, def.spreadDegrees), def.fireAngle,
                    def.targetOffsetX, def.targetOffsetY, def.shapeScale > 0 ? def.shapeScale : 1f, def.shapeFormTime, def.shapeDriftRatio,
                    def.shapeFlipX, def.shapeRotateWithDirection);
            }
            case "RadialNearMiss": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                int numBullets = resolve(def.numBullets, 16);
                float missDistance = resolve(def.nearMissDistance, 0.35f);
                float fireRate = def.fireRate > 0 ? def.fireRate : 1.5f;
                int volleyCount = def.volleyCount >= 0 ? def.volleyCount : 3;
                return new RadialNearMissFiring(resolve(bulletSize(def, bulletDef), 0.3f), resolve(bulletSpeed(def, bulletDef), 4f), bulletDamage(def, bulletDef), spriteOverride,
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef), worldWidth, worldHeight, numBullets, missDistance, fireRate, volleyCount);
            }
            default:
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                return createFiring(def.type, def.fireRate, bulletSize(def, bulletDef), bulletSpeed(def, bulletDef), bulletDamage(def, bulletDef), spriteOverride, def.spreadDegrees, def.numBullets, def.offsetX, def.offsetY,
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef));
        }
    }

    // Field resolution: the pattern's own value, else the BulletDef's, else the default.

    /** -1 if neither sets it (the caller then applies the type's default). */
    private static float bulletSize(FiringPatternDef def, BulletDef bulletDef) {
        return def.bulletSize > 0 ? def.bulletSize : (bulletDef != null ? bulletDef.bulletSize : -1f);
    }

    private static float bulletSpeed(FiringPatternDef def, BulletDef bulletDef) {
        return def.bulletSpeed > 0 ? def.bulletSpeed : (bulletDef != null ? bulletDef.bulletSpeed : -1f);
    }

    private static float bulletAcceleration(FiringPatternDef def, BulletDef bulletDef) {
        return def.bulletAcceleration != 0f ? def.bulletAcceleration : (bulletDef != null ? bulletDef.bulletAcceleration : 0f);
    }

    /** Minimum ramp speed; defaults to 0 (can stop but not reverse). */
    private static float bulletMinSpeed(FiringPatternDef def, BulletDef bulletDef) {
        if (def.bulletMinSpeed > 0) return def.bulletMinSpeed;
        if (bulletDef != null && bulletDef.bulletMinSpeed > 0) return bulletDef.bulletMinSpeed;
        return 0f;
    }

    /** Maximum ramp speed; defaults to unbounded. */
    private static float bulletMaxSpeed(FiringPatternDef def, BulletDef bulletDef) {
        if (def.bulletMaxSpeed > 0) return def.bulletMaxSpeed;
        if (bulletDef != null && bulletDef.bulletMaxSpeed > 0) return bulletDef.bulletMaxSpeed;
        return Float.MAX_VALUE;
    }

    /** The pattern's phase list, else the BulletDef's (never merged), else a single constant
     *  acceleration. */
    private static SpeedProfile speedProfile(FiringPatternDef def, BulletDef bulletDef) {
        float minSpeed = bulletMinSpeed(def, bulletDef);
        float maxSpeed = bulletMaxSpeed(def, bulletDef);

        Array<BulletSpeedPhase> phases;
        boolean loop;
        if (def.bulletSpeedPhases != null && def.bulletSpeedPhases.size > 0) {
            phases = def.bulletSpeedPhases;
            loop = def.bulletSpeedPhasesLoop;
        } else if (bulletDef != null && bulletDef.bulletSpeedPhases != null && bulletDef.bulletSpeedPhases.size > 0) {
            phases = bulletDef.bulletSpeedPhases;
            loop = bulletDef.bulletSpeedPhasesLoop;
        } else {
            return SpeedProfile.constant(bulletAcceleration(def, bulletDef), minSpeed, maxSpeed);
        }

        float[] accelerations = new float[phases.size];
        float[] durations = new float[phases.size];
        for (int i = 0; i < phases.size; i++) {
            accelerations[i] = phases.get(i).acceleration;
            durations[i] = phases.get(i).duration;
        }
        return new SpeedProfile(accelerations, durations, loop, minSpeed, maxSpeed);
    }

    /** null if neither sets one (the bullet class's default shape applies). */
    private static HitboxSpec.Shape hitboxShape(FiringPatternDef def, BulletDef bulletDef) {
        String shape = def.hitboxShape != null ? def.hitboxShape : (bulletDef != null ? bulletDef.hitboxShape : null);
        if (shape == null) return null;
        return "Rectangle".equals(shape) ? HitboxSpec.Shape.RECTANGLE : HitboxSpec.Shape.CIRCLE;
    }

    private static float hitboxScale(FiringPatternDef def, BulletDef bulletDef) {
        if (def.hitboxScale > 0) return def.hitboxScale;
        if (bulletDef != null && bulletDef.hitboxScale > 0) return bulletDef.hitboxScale;
        return 1f;
    }

    private static float hitboxOffsetX(FiringPatternDef def, BulletDef bulletDef) {
        return def.hitboxOffsetX != 0f ? def.hitboxOffsetX : (bulletDef != null ? bulletDef.hitboxOffsetX : 0f);
    }

    private static float hitboxOffsetY(FiringPatternDef def, BulletDef bulletDef) {
        return def.hitboxOffsetY != 0f ? def.hitboxOffsetY : (bulletDef != null ? bulletDef.hitboxOffsetY : 0f);
    }

    /** The hitbox spec, each field resolved independently. */
    private static HitboxSpec hitboxSpec(FiringPatternDef def, BulletDef bulletDef) {
        HitboxSpec.Shape shape = hitboxShape(def, bulletDef);
        float scale = hitboxScale(def, bulletDef);
        float offsetX = hitboxOffsetX(def, bulletDef);
        float offsetY = hitboxOffsetY(def, bulletDef);
        if (shape == null && scale == 1f && offsetX == 0f && offsetY == 0f) return HitboxSpec.DEFAULT;
        return new HitboxSpec(shape, scale, offsetX, offsetY);
    }

    /** Defaults to 1. Also what a reflected bullet hits for. */
    private static int bulletDamage(FiringPatternDef def, BulletDef bulletDef) {
        if (def.bulletDamage > 0) return def.bulletDamage;
        return bulletDef != null ? bulletDef.damage : BulletDef.DEFAULT_DAMAGE;
    }

    /** The pattern's bullet animation. Texture: pattern, then BulletDef, then the enemy's
     *  bulletTexture. Sheet layout and timing come only from the pattern/BulletDef. */
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