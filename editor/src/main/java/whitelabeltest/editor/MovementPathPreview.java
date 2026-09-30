package whitelabeltest.editor;

import com.badlogic.gdx.math.Vector2;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.enemy.movementpatterns.WaypointSpline;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.util.ArrayList;
import java.util.List;

/** A spawn trigger's movement path as world points from its spawn position, following
 *  PatternFactory.createMovement()'s rules. Drawn at true scale by StageCanvas. */
public final class MovementPathPreview {
    // As MovementPattern.DEFAULT_ANGLE_DEG.
    private static final float DEFAULT_ANGLE_DEG = 270f;
    // As PatternFactory's Sequence leg default.
    private static final float DEFAULT_LEG_DURATION = 3.0f;
    // For flipX/flipY about the play area center. Keep in sync with Main.
    private static final float WORLD_WIDTH = 9f;
    private static final float WORLD_HEIGHT = 12f;

    private MovementPathPreview() {}

    /** At least 2 points, or null if it isn't a spawn with a position and a pattern that moves. */
    public static List<double[]> resolve(Trigger trigger, MovementPatternLibrary patternLibrary) {
        boolean isEnemySpawn = trigger.type != null && trigger.sound == null && trigger.spriteTexture == null
            && trigger.setSpeed == null && !trigger.silence && !trigger.despawn && !trigger.waypointGem
            && trigger.swapWeaponId == null;
        if (!isEnemySpawn || Float.isNaN(trigger.x) || Float.isNaN(trigger.y)) return null;

        String patternId = trigger.movementPattern;
        if (patternId == null || patternId.isBlank() || !patternLibrary.exists(patternId)) return null;

        MovementPatternDef pattern = patternLibrary.load(patternId);
        List<double[]> points = new ArrayList<>();
        points.add(new double[] { trigger.x, trigger.y });
        appendSegments(pattern, points, trigger.x, trigger.inverseMovement);
        return points.size() >= 2 ? points : null;
    }

    /** Appends def's points from the current last point:
     *  - None: nothing.
     *  - Sequence: each child in turn.
     *  - Squadron: the leader pattern (formation offset ignored).
     *  - MoveToPoint: the target (spawnCenterX / 0 for an unset axis).
     *  - anything else: one straight leg of speed * duration (an approximation for Seeking,
     *    ZigZag, etc.). */
    private static void appendSegments(MovementPatternDef def, List<double[]> points, float spawnCenterX, boolean inverse) {
        if (def == null || "None".equals(def.type)) return;

        if ("Sequence".equals(def.type)) {
            if (def.patterns != null) {
                for (MovementPatternDef sub : def.patterns) appendSegments(sub, points, spawnCenterX, inverse);
            }
            return;
        }
        if ("Squadron".equals(def.type)) {
            appendSegments(def.pattern, points, spawnCenterX, inverse);
            return;
        }
        if ("WaypointPath".equals(def.type)) {
            appendWaypointPath(def, points, inverse);
            return;
        }
        if ("MoveToPoint".equals(def.type)) {
            float targetX = !Float.isNaN(def.targetX) ? def.targetX : spawnCenterX;
            float targetY = !Float.isNaN(def.targetY) ? def.targetY : 0f;
            points.add(new double[] { targetX, targetY });
            return;
        }

        float speed = def.speed > 0 ? def.speed : 0f;
        if (speed <= 0) return;
        float angle = !Float.isNaN(def.movementAngle) ? def.movementAngle : DEFAULT_ANGLE_DEG;
        float duration = def.duration > 0 ? def.duration : DEFAULT_LEG_DURATION;
        double distance = speed * duration * (inverse ? -1 : 1);
        double rad = Math.toRadians(angle);
        double[] cursor = points.get(points.size() - 1);
        points.add(new double[] { cursor[0] + Math.cos(rad) * distance, cursor[1] + Math.sin(rad) * distance });
    }

    /** Samples the same Cardinal spline (WaypointSpline) the game flies, so tension shows. flipX/Y
     *  mirror about the play area center; `inverse` is ignored. */
    private static void appendWaypointPath(MovementPatternDef def, List<double[]> points, boolean inverse) {
        if (def.patterns == null || def.patterns.size == 0) return;
        double[] cursor = points.get(points.size() - 1);

        List<MovementPatternDef> legs = new ArrayList<>();
        for (MovementPatternDef leg : def.patterns) {
            if ("MoveToPoint".equals(leg.type)) legs.add(leg);
        }
        if (legs.isEmpty()) return;

        Vector2[] curvePoints = new Vector2[legs.size() + 1];
        float[] tensions = new float[legs.size() + 1];
        curvePoints[0] = new Vector2((float) cursor[0], (float) cursor[1]);
        tensions[0] = legs.get(0).tension;
        for (int i = 0; i < legs.size(); i++) {
            MovementPatternDef leg = legs.get(i);
            float targetX = !Float.isNaN(leg.targetX) ? leg.targetX : (float) cursor[0];
            float targetY = !Float.isNaN(leg.targetY) ? leg.targetY : 0f;
            if (def.flipX) targetX = WORLD_WIDTH - targetX;
            if (def.flipY) targetY = WORLD_HEIGHT - targetY;
            curvePoints[i + 1] = new Vector2(targetX, targetY);
            tensions[i + 1] = leg.tension;
        }

        int segments = def.closePath ? curvePoints.length : curvePoints.length - 1;
        Vector2 sample = new Vector2();
        for (int seg = 0; seg < segments; seg++) {
            for (int step = 1; step <= WaypointSpline.SAMPLES_PER_SEGMENT; step++) {
                float t = seg + step / (float) WaypointSpline.SAMPLES_PER_SEGMENT;
                WaypointSpline.evaluate(sample, curvePoints, tensions, def.closePath, t);
                points.add(new double[] { sample.x, sample.y });
            }
        }
    }
}
