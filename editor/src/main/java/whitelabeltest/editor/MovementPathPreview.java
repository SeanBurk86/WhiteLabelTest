package whitelabeltest.editor;

import com.badlogic.gdx.math.Vector2;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.enemy.movementpatterns.WaypointSpline;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.util.ArrayList;
import java.util.List;

/** Resolves an enemy-spawn Trigger's real, real-world-scale movement path - a sequence of
 *  {worldX, worldY} points starting at the trigger's own spawn position - by walking its resolved
 *  movement pattern the same way PatternFactory.createMovement() does in the actual game (see
 *  appendSegments()'s own doc), just producing points instead of live MovementPattern objects.
 *  Shown on StageCanvas at true scale (PIXELS_PER_UNIT_X, the same real-world-unit conversion the
 *  canvas already uses for a sprite's own width/height) rather than normalized into a small
 *  thumbnail, so the shape AND distance genuinely match how far/which way the enemy actually
 *  travels in-game - see StageCanvas.drawPathPreviews(). */
public final class MovementPathPreview {
    // Matches MovementPattern.DEFAULT_ANGLE_DEG - the game's own fallback when a leg sets a speed
    // but no angle.
    private static final float DEFAULT_ANGLE_DEG = 270f;
    // Matches PatternFactory.createMovement()'s Sequence-building fallback for a leg with no
    // duration of its own.
    private static final float DEFAULT_LEG_DURATION = 3.0f;
    // Matches Main.PLAY_AREA_WIDTH/HEIGHT - see StageCanvas.WORLD_WIDTH's own "not read from the
    // game module since it's private there" doc; used only to mirror a WaypointPath's flipX/flipY
    // the same way PatternFactory.createMovement()'s "WaypointPath" case does.
    private static final float WORLD_WIDTH = 9f;
    private static final float WORLD_HEIGHT = 12f;

    private MovementPathPreview() {}

    /** Null if this trigger isn't an enemy spawn, has no spawn position, has no movement pattern
     *  resolved (purely trigger.movementPattern - movement isn't part of EnemyDefinition at all, see
     *  that class's own doc, so there's no type-level fallback to try here), or that pattern
     *  produces no real displacement (e.g. "None"/Stationary) - otherwise at least 2 points (the
     *  spawn point plus wherever the pattern actually takes it). */
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

    /** Appends however many points `def` contributes to the path, starting from `points`'s current
     *  last entry - mirrors PatternFactory.createMovement()'s own dispatch/fallback rules (see that
     *  method) so the shape roughly matches what the game would actually do, just as points instead
     *  of live MovementPattern objects:
     *  - "None" contributes nothing (matches NoMovement).
     *  - "Sequence" recurses into each sub-pattern in order, threading the cursor through.
     *  - "Squadron" recurses into its wrapped leader pattern (formation offset ignored here - a
     *    minor position error, not a shape error, for this preview).
     *  - "MoveToPoint" adds its target, substituting spawnCenterX/0 for an unset axis exactly like
     *    PatternFactory does.
     *  - anything else (Straight, and the approximated fallback for Seeking/ZigZag/Bounce/Spline/
     *    an unrecognized type - PatternFactory itself falls back to Straight for those) adds a
     *    single straight-line leg of length speed*duration in movementAngle's direction. */
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

    /** Samples the SAME Cardinal-spline curve (see WaypointSpline) WaypointPathMovement actually
     *  flies at runtime - not straight segments between waypoints, so tension genuinely previews as
     *  curvature here rather than just being a number with no visible effect until the game runs.
     *  Ignores `inverse` (Trigger.inverseMovement) here, same as every other branch above already
     *  does - see this class's own limitations doc. flipX/flipY are applied to the target points the
     *  same way PatternFactory.createMovement()'s "WaypointPath" case does, mirroring around the
     *  play area's own center rather than the spawn point (see that method's own doc on why). */
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
