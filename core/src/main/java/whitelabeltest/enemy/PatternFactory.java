package whitelabeltest.enemy;

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
            default: return new NoFiring();
        }
    }
}
