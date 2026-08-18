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
import whitelabeltest.enemy.firingpatterns.*;
import whitelabeltest.enemy.movementpatterns.ZigZagMovement;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.EnemySpawnRegistry;

public class PatternFactory {
    public static MovementPattern createMovement(MovementPatternDef def, float worldHeight, float spawnCenterX) {
        return createMovement(def, worldHeight, spawnCenterX, Float.NaN, Float.NaN);
    }

    /** @param formationOffsetX, formationOffsetY where this specific spawn sits in its formation -
     *  NaN means "no override", so a Squadron pattern falls back to its own offsetX/offsetY (the
     *  old way of baking one offset into the pattern, still supported for a formation that only
     *  ever spawns one member at that slot). Passing a real value here is what lets a single
     *  shared Squadron pattern be reused by every member of a squad, each supplying its own slot's
     *  offset at spawn time instead of needing its own copy of the pattern (see
     *  GenericEnemy.initWithDefinition and SpawnScheduler.SpawnEvent.offsetX/offsetY). */
    public static MovementPattern createMovement(MovementPatternDef def, float worldHeight, float spawnCenterX, float formationOffsetX, float formationOffsetY) {
        if (def == null || "None".equals(def.type)) return new NoMovement();

        if ("Sequence".equals(def.type)) {
            if (def.patterns == null || def.patterns.size == 0) return new NoMovement();
            Array<MovementPattern> mps = new Array<>();
            float[] durations = new float[def.patterns.size];
            for (int i = 0; i < def.patterns.size; i++) {
                MovementPatternDef sub = def.patterns.get(i);
                mps.add(createMovement(sub, worldHeight, spawnCenterX, formationOffsetX, formationOffsetY));
                durations[i] = sub.duration > 0 ? sub.duration : 3.0f;
            }
            return new SequencedMovementPattern(mps, durations);
        }

        if ("Squadron".equals(def.type)) {
            if (def.pattern == null) return new NoMovement();
            float offsetX = !Float.isNaN(formationOffsetX) ? formationOffsetX : def.offsetX;
            float offsetY = !Float.isNaN(formationOffsetY) ? formationOffsetY : def.offsetY;
            MovementPattern leader = createMovement(def.pattern, worldHeight, spawnCenterX - offsetX);
            return new SquadronMovement(leader, offsetX, offsetY);
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
            // Orbiting's "speed" bootstraps a constant center-drift vector, not a travel speed
            // that ramps over time the way the other bullet types here do - acceleration doesn't
            // apply to it.
            case "Orbiting": return new OrbitingFiring(fireRate, resolve(bulletSize, 0.5f), resolve(bulletSpeed, 4f), spriteOverride, offsetX, offsetY, bulletDamage);
            default: return new NoFiring();
        }
    }

    public static FiringPattern createFiring(EnemyDefinition enemyDef, FiringPatternDef def) {
        return createFiring(enemyDef, def, Float.NaN, Float.NaN);
    }

    /** @param worldWidth, worldHeight only consulted by "Wall" (see WallFiring) and "RadialNearMiss"
     *  (see RadialNearMissFiring) respectively - every other pattern fires relative to the enemy's
     *  own position and doesn't need either. NaN is fine for those. */
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
                return new SpawnEnemyFiring(def.spawnType, def.fireRate, def.offsetX, def.offsetY);
            case "Aimed": {
                BulletDef bulletDef = PatternRegistry.getBullet(def.bulletId);
                Animation<TextureRegion> spriteOverride = buildBulletAnimation(enemyDef, def, bulletDef);
                return new AimedFiring(def.fireRate, resolve(bulletSize(def, bulletDef), 0.25f), resolve(bulletSpeed(def, bulletDef), 5f), spriteOverride, def.offsetX, def.offsetY, bulletDamage(def, bulletDef), def.targetOffsetX, def.targetOffsetY,
                    speedProfile(def, bulletDef), hitboxSpec(def, bulletDef));
            }
            // Pulled out of the generic small-helper dispatch (see the other createFiring overload
            // above) so def.phaseOffset can reach BurstAimedFiring - see its javadoc for why two
            // side-by-side BurstAimed emitters use this to land in opposite phase.
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
            // Orbiting's "speed" bootstraps a constant center-drift vector, not a travel speed
            // that ramps over time the way the other bullet types here do - acceleration doesn't
            // apply to it.
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

    /** A pattern's own bulletSize/bulletSpeed always win; otherwise fall back to the referenced
     *  bullet definition's values, if any. */
    private static float bulletSize(FiringPatternDef def, BulletDef bulletDef) {
        return def.bulletSize > 0 ? def.bulletSize : (bulletDef != null ? bulletDef.bulletSize : -1f);
    }

    private static float bulletSpeed(FiringPatternDef def, BulletDef bulletDef) {
        return def.bulletSpeed > 0 ? def.bulletSpeed : (bulletDef != null ? bulletDef.bulletSpeed : -1f);
    }

    private static float bulletAcceleration(FiringPatternDef def, BulletDef bulletDef) {
        return def.bulletAcceleration != 0f ? def.bulletAcceleration : (bulletDef != null ? bulletDef.bulletAcceleration : 0f);
    }

    /** Floor currentSpeed can't ramp below - 0 (the default, whether unset here or on the
     *  referenced BulletDef) means "can decelerate to a stop but not reverse past 0". */
    private static float bulletMinSpeed(FiringPatternDef def, BulletDef bulletDef) {
        if (def.bulletMinSpeed > 0) return def.bulletMinSpeed;
        if (bulletDef != null && bulletDef.bulletMinSpeed > 0) return bulletDef.bulletMinSpeed;
        return 0f;
    }

    /** Ceiling currentSpeed can't ramp above - unbounded (the default) unless explicitly set. */
    private static float bulletMaxSpeed(FiringPatternDef def, BulletDef bulletDef) {
        if (def.bulletMaxSpeed > 0) return def.bulletMaxSpeed;
        if (bulletDef != null && bulletDef.bulletMaxSpeed > 0) return bulletDef.bulletMaxSpeed;
        return Float.MAX_VALUE;
    }

    /** Builds the shared speed-ramp spec bullets from this pattern will ramp along. A pattern's
     *  own bulletSpeedPhases list always wins wholesale over its referenced BulletDef's (the two
     *  are never merged); when neither sets a phase list, falls back to the classic single
     *  bulletAcceleration ramp (see bulletAcceleration/bulletMinSpeed/bulletMaxSpeed above). */
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

    /** A pattern's own hitboxShape always wins; otherwise fall back to the referenced bullet
     *  definition's, if any. Null (neither sets one) lets each bullet type keep its own default
     *  shape - see HitboxSpec.shape. */
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

    /** Builds the shared hitbox spec bullets from this pattern will use for collision, resolving
     *  shape/scale/offsetX/offsetY independently against the referenced BulletDef's (same
     *  per-field fallback as bulletSize/bulletSpeed above, unlike bulletSpeedPhases' wholesale
     *  override). */
    private static HitboxSpec hitboxSpec(FiringPatternDef def, BulletDef bulletDef) {
        HitboxSpec.Shape shape = hitboxShape(def, bulletDef);
        float scale = hitboxScale(def, bulletDef);
        float offsetX = hitboxOffsetX(def, bulletDef);
        float offsetY = hitboxOffsetY(def, bulletDef);
        if (shape == null && scale == 1f && offsetX == 0f && offsetY == 0f) return HitboxSpec.DEFAULT;
        return new HitboxSpec(shape, scale, offsetX, offsetY);
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