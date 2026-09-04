package whitelabeltest.gamemanagers.trigger;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.movementpatterns.MovementPattern;

/** Expands ONE enemy-spawn Trigger's wave* fields (see Trigger's own doc on that group) into the
 *  full list of member spawn points/facings it describes - shared by TriggerManager.fireWave() (the
 *  real spawning) and the editor's own canvas preview, so the preview can never drift out of sync
 *  with actual gameplay the way this codebase has already been bitten by twice before (background
 *  rendering, entrance movement) - see EnemyEntranceMovement's own doc on that pattern.
 *
 * A no-op (Trigger.waveShape == null) simply isn't called - this class only exists to be asked "what
 * does this wave look like", not to also own the "is there even a wave here" check. */
public final class WaveSpawnPlanner {
    private WaveSpawnPlanner() {}

    /** One member's absolute spawn position and facing/movement angle (degrees, same convention as
     *  MovementPatternDef.movementAngle - 0 = +X/right, 90 = +Y/up, DEFAULT_ANGLE_DEG=270 = -Y/down,
     *  "toward the player"). Always an ABSOLUTE world position (trigger.x/y + this shape's own
     *  offset from it) regardless of Trigger.waveKeepFormation - see TriggerManager.fireWave()'s own
     *  doc on how that flag instead only changes WHICH movement mechanism turns this position into
     *  motion, not how the position itself is computed. */
    public static final class Slot {
        public final float x, y, angleDeg;
        public Slot(float x, float y, float angleDeg) { this.x = x; this.y = y; this.angleDeg = angleDeg; }
    }

    /** Positions are returned EXACTLY as the shape math computes them - never clamped or otherwise
     *  moved to fit inside the play area. A member landing outside it (a wide shape, or an anchor
     *  trigger authored off to one side) is legitimate: it's expected to fly onto screen via its own
     *  movement/waypoint path, the same way any entrance-from-above spawn already starts out above
     *  the visible area on purpose - see GenericEnemy.isOffScreen()'s own hasBeenOnScreen doc for the
     *  matching removal-side rule (an off-axis spawn is never stripped as "off-screen" before it's
     *  ever actually been seen, no matter how far off-axis or for how long).
     *  @param nominalPlayerX,nominalPlayerY the reference point "to the player" orientation aims
     *  at - this is a static, authoring-time computation (there's no live player position yet at
     *  either trigger-fire time in the real game or preview-render time in the editor), so both
     *  callers pass the SAME nominal stand-in point (StageCanvas.DEFAULT_SPAWN_X/Y in the editor) to
     *  stay identical. */
    public static Array<Slot> plan(Trigger trigger, float nominalPlayerX, float nominalPlayerY) {
        Array<Slot> slots = new Array<>();
        if (trigger.waveShape == null) return slots;
        collectPositions(trigger, trigger.x, trigger.y, slots);

        // waveRotation turns the whole shape around the anchor BEFORE orientation is computed
        // against it, so e.g. "to the exterior" still means "away from the anchor" for the rotated
        // positions, not the shape's own pre-rotation layout.
        double rotRad = Math.toRadians(trigger.waveRotation);
        float cos = (float) Math.cos(rotRad);
        float sin = (float) Math.sin(rotRad);

        for (int i = 0; i < slots.size; i++) {
            Slot slot = slots.get(i);
            float dx = slot.x - trigger.x;
            float dy = slot.y - trigger.y;
            float x = trigger.x + dx * cos - dy * sin;
            float y = trigger.y + dx * sin + dy * cos;
            float angle = computeAngle(trigger.waveOrientation, x, y, trigger.x, trigger.y, nominalPlayerX, nominalPlayerY);
            slots.set(i, new Slot(x, y, angle));
        }
        return slots;
    }

    private static void collectPositions(Trigger t, float anchorX, float anchorY, Array<Slot> out) {
        switch (t.waveShape) {
            case "circle" -> circleMembers(t, anchorX, anchorY, out);
            case "plane" -> planeMembers(t, anchorX, anchorY, out);
            case "triangle" -> triangleMembers(t, anchorX, anchorY, out);
            default -> pointMembers(t, anchorX, anchorY, out); // "point"
        }
    }

    // point: every member spawns at the exact same spot - only timing (waveStartDelay/
    // waveSpawnInterval) staggers them, a stream of spawns from one location.
    private static void pointMembers(Trigger t, float anchorX, float anchorY, Array<Slot> out) {
        int count = Math.max(1, t.waveNumberOfSpawns);
        for (int i = 0; i < count; i++) out.add(new Slot(anchorX, anchorY, 0f));
    }

    // circle: waveNumberOfSpawns members evenly spread across [waveStartAngle, waveEndAngle] +
    // waveCircleOffset around an ellipse (waveWidth/waveHeight are the FULL width/height, so radii
    // are half of each) centered on the anchor. A full 360 sweep divides evenly by count (no
    // duplicate point at both ends); a partial arc instead divides by (count-1) so the first/last
    // member land exactly on waveStartAngle/waveEndAngle.
    private static void circleMembers(Trigger t, float anchorX, float anchorY, Array<Slot> out) {
        int count = Math.max(1, t.waveNumberOfSpawns);
        float sweep = t.waveEndAngle - t.waveStartAngle;
        boolean fullSweep = Math.abs(Math.abs(sweep) - 360f) < 0.01f;
        float step = count <= 1 ? 0f : sweep / (fullSweep ? count : (count - 1));
        float rx = t.waveWidth / 2f;
        float ry = t.waveHeight / 2f;
        for (int i = 0; i < count; i++) {
            float angleDeg = t.waveStartAngle + t.waveCircleOffset + i * step;
            double rad = Math.toRadians(angleDeg);
            out.add(new Slot(anchorX + rx * (float) Math.cos(rad), anchorY + ry * (float) Math.sin(rad), 0f));
        }
    }

    // plane: a grid spanning waveWidth x waveHeight, centered on the anchor - waveLines is the
    // count ACROSS THE WIDTH (a horizontal row of waveLines members), waveColumns the count DOWN
    // THE HEIGHT (how many such rows stack vertically) - matches the reference tool's own
    // lines=7/columns=1/width=70/height=14 producing a single horizontal row of 7, not 7 stacked
    // rows of 1.
    private static void planeMembers(Trigger t, float anchorX, float anchorY, Array<Slot> out) {
        int acrossWidth = Math.max(1, t.waveLines);
        int acrossHeight = Math.max(1, t.waveColumns);
        float spacingX = acrossWidth > 1 ? t.waveWidth / (acrossWidth - 1) : 0f;
        float spacingY = acrossHeight > 1 ? t.waveHeight / (acrossHeight - 1) : 0f;
        for (int row = 0; row < acrossHeight; row++) {
            float y = anchorY + (row - (acrossHeight - 1) / 2f) * spacingY;
            for (int col = 0; col < acrossWidth; col++) {
                float x = anchorX + (col - (acrossWidth - 1) / 2f) * spacingX;
                out.add(new Slot(x, y, 0f));
            }
        }
    }

    // triangle: a waveColumns-wide V/pyramid spanning waveWidth x waveHeight - row 0 (the apex, 1
    // member) sits at the FRONT (anchorY - height/2, i.e. the leading edge of a formation flying
    // downward toward lower Y - see BaseEnemy/this game's own Y-decreases-toward-the-player
    // convention), widening row by row to waveColumns members at the back (anchorY + height/2).
    // Total members = waveColumns*(waveColumns+1)/2.
    private static void triangleMembers(Trigger t, float anchorX, float anchorY, Array<Slot> out) {
        int columns = Math.max(1, t.waveColumns);
        for (int row = 0; row < columns; row++) {
            int countInRow = row + 1;
            float y = columns > 1 ? anchorY - t.waveHeight / 2f + row * (t.waveHeight / (columns - 1)) : anchorY;
            float rowWidth = t.waveWidth * countInRow / (float) columns;
            float spacingX = countInRow > 1 ? rowWidth / (countInRow - 1) : 0f;
            for (int p = 0; p < countInRow; p++) {
                float x = anchorX - rowWidth / 2f + p * spacingX;
                out.add(new Slot(x, y, 0f));
            }
        }
    }

    /** See Slot's own doc for the angle convention.
     *  - "in front": always DEFAULT_ANGLE_DEG, regardless of position - every member flies the same
     *    way, whatever the shape.
     *  - "to the center": faces the anchor (trigger.x/y - the shape's own center point).
     *  - "to the exterior": faces directly away from the anchor - the opposite of "to the center".
     *  - "to the player": faces (nominalPlayerX, nominalPlayerY).
     *  A member that sits exactly ON its reference point (a "point"-shape member facing its own
     *  anchor) has no direction to compute at all; that falls back to DEFAULT_ANGLE_DEG rather than
     *  propagating a NaN angle. */
    private static float computeAngle(String orientation, float memberX, float memberY, float anchorX, float anchorY,
                                       float nominalPlayerX, float nominalPlayerY) {
        Vector2 direction = switch (orientation) {
            case "to the center" -> new Vector2(anchorX - memberX, anchorY - memberY);
            case "to the exterior" -> new Vector2(memberX - anchorX, memberY - anchorY);
            case "to the player" -> new Vector2(nominalPlayerX - memberX, nominalPlayerY - memberY);
            default -> null; // "in front"
        };
        if (direction == null || direction.len2() < 0.0001f) return MovementPattern.DEFAULT_ANGLE_DEG;
        return direction.angleDeg();
    }
}
