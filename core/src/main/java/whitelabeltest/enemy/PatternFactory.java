package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.movementpatterns.StraightMovement;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.enemy.movementpatterns.SeekingMovement;
import whitelabeltest.enemy.movementpatterns.SplineMovement;
import whitelabeltest.enemy.firingpatterns.*;
import whitelabeltest.enemy.movementpatterns.ZigZagMovement;

public class PatternFactory {
    public static MovementPattern createMovement(String type, float speed, float worldWidth, float worldHeight) {
        if (type == null) return new StraightMovement(speed);

        switch (type) {
            case "ZigZag": return new ZigZagMovement(speed * 1.5f, speed);
            case "Seeking": return new SeekingMovement(speed, 3.0f);
            case "Spline": return new SplineMovement(worldWidth, worldHeight, 6.0f);
            default: return new StraightMovement(speed);
        }
    }

    public static FiringPattern createFiring(String type, float fireRate) {
        if (type == null) return new NoFiring();

        switch (type) {
            case "Aimed": return new AimedFiring(fireRate);
            case "SelfDestruct": return new SelfDestructFiring(3.0f);
            case "ExplodingAimed": return new ExplodingAimedFiring(fireRate);
            case "BurstAimed": return new BurstAimedFiring(fireRate);
            case "QuarterCircle": return new QuarterCircleFiring(fireRate);
            case "Sweep": return new SweepFiring(fireRate);
            case "SineWave": return new SineWaveFiring(fireRate);
            case "Orbiting": return new OrbitingFiring(fireRate);
            default: return new NoFiring();
        }
    }

    public static FiringPattern createFiring(FiringPatternDef def) {
        if (def == null) return new NoFiring();

        switch (def.type) {
            case "Sequence": {
                if (def.patterns == null || def.patterns.size == 0) return new NoFiring();
                Array<FiringPattern> fps = new Array<>();
                float[] durations = new float[def.patterns.size];
                for (int i = 0; i < def.patterns.size; i++) {
                    FiringPatternDef sub = def.patterns.get(i);
                    fps.add(createFiring(sub));
                    durations[i] = sub.duration > 0 ? sub.duration : 3.0f;
                }
                return new SequencedFiringPattern(fps, durations);
            }
            case "Combined": {
                if (def.patterns == null || def.patterns.size == 0) return new NoFiring();
                Array<FiringPattern> fps = new Array<>();
                for (FiringPatternDef sub : def.patterns) {
                    fps.add(createFiring(sub));
                }
                return new CombinedFiringPattern(fps);
            }
            default:
                return createFiring(def.type, def.fireRate);
        }
    }
}
