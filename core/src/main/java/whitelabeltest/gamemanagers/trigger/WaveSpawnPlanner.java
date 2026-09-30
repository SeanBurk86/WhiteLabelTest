package whitelabeltest.gamemanagers.trigger;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.movementpatterns.MovementPattern;

/** Expands a wave trigger's wave* fields into member spawn points and facings. Shared by
 *  TriggerManager.fireWave() and the editor preview so the two always agree. */
public final class WaveSpawnPlanner {
    private WaveSpawnPlanner() {}

    /** One member's absolute spawn position and facing angle (degrees: 0 = right, 90 = up,
     *  MovementPattern.DEFAULT_ANGLE_DEG = 270 = down toward the player). */
    public static final class Slot {
        public final float x, y, angleDeg;
        public Slot(float x, float y, float angleDeg) { this.x = x; this.y = y; this.angleDeg = angleDeg; }
    }

    /** Member positions exactly as the shape math gives them, never clamped to the play area
     *  (off-screen members are expected to fly in). The nominal player point is a fixed stand-in for
     *  "to the player" orientation, since no live player position exists at authoring time. */
    public static Array<Slot> plan(Trigger trigger, float nominalPlayerX, float nominalPlayerY) {
        Array<Slot> slots = new Array<>();
        if (trigger.waveShape == null) return slots;
        collectPositions(trigger, trigger.x, trigger.y, slots);

        // Rotate around the anchor before computing orientation, so "to the exterior" etc. apply to
        // the rotated positions.
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

    // point: every member at the anchor; only the spawn timing staggers them.
    private static void pointMembers(Trigger t, float anchorX, float anchorY, Array<Slot> out) {
        int count = Math.max(1, t.waveNumberOfSpawns);
        for (int i = 0; i < count; i++) out.add(new Slot(anchorX, anchorY, 0f));
    }

    // circle: members spread over [waveStartAngle, waveEndAngle] + waveCircleOffset on an ellipse of
    // full size waveWidth x waveHeight. A full 360 sweep divides by count (no duplicate endpoint); a
    // partial arc divides by count-1 so members land on both ends.
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

    // plane: a grid centered on the anchor. waveLines = members across the width, waveColumns = rows
    // down the height (so lines=7, columns=1 is a single horizontal row of 7).
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

    // triangle: apex (1 member) at the front/bottom edge, widening to waveColumns members at the
    // back. Total members = waveColumns*(waveColumns+1)/2.
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

    /** "in front" = straight down; "to the center" = toward the anchor; "to the exterior" = away
     *  from it; "to the player" = toward the nominal player point. A member sitting on its reference
     *  point falls back to straight down instead of producing NaN. */
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
