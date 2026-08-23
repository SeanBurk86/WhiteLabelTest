package whitelabeltest.gamemanagers.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/** "Hacking movie" digital-rain effect: columns of falling, flickering letters/digits with a
 *  bright head and a fading trail - drawn as real glyphs via the HUD's own BitmapFont (not solid
 *  blocks). Each column's fall speed/trail length/phase, and each cell's displayed character,
 *  come from a small hash noise (same style as ChainFireEffect's shader noise, ported to Java) so
 *  the whole grid is a pure function of elapsed time - no per-cell state to track or reset. */
public class DataStreamEffect {
    private static final char[] CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final float FLICKER_RATE = 8f; // character swaps per second, per cell
    // VT323's baked cap-height at UIManager's base font scale (0.009375f) - see glyphScale below.
    private static final float BASE_GLYPH_HEIGHT = 0.3f;

    private float time;

    /** Call once per frame regardless of visibility, so the fall animation stays smooth. */
    public void update(float delta) {
        time += delta;
    }

    /** Draws a columns x rows grid of falling letters/digits over [x, x+width] x [y, y+height]
     *  (y is the rect's bottom edge, matching SpriteBatch's own draw() convention). */
    public void render(SpriteBatch batch, BitmapFont font, float x, float y, float width, float height,
                        int columns, int rows, Color color, Color headColor, float alpha) {
        float cellWidth = width / columns;
        float cellHeight = height / rows;

        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        float glyphScale = cellHeight / BASE_GLYPH_HEIGHT;
        font.getData().setScale(originalScaleX * glyphScale, originalScaleY * glyphScale);

        for (int col = 0; col < columns; col++) {
            float colSeed = hash(col, 1f);
            float speed = MathUtils.lerp(4f, 12f, colSeed);
            float trailLen = MathUtils.lerp(4f, 11f, hash(col, 5.3f));
            float cycle = rows + trailLen;
            float headRow = ((time * speed * 0.2f + colSeed * cycle * 7f) % cycle) - trailLen;

            int rowStart = Math.max(0, MathUtils.floor(headRow - trailLen));
            int rowEnd = Math.min(rows - 1, MathUtils.ceil(headRow));

            for (int row = rowStart; row <= rowEnd; row++) {
                float distBehindHead = headRow - row;
                if (distBehindHead < 0f || distBehindHead > trailLen) continue;

                float trailT = distBehindHead / trailLen;
                float brightness = 1f - trailT;
                boolean isHead = distBehindHead <= 1f;

                char c = CHARSET[(int) (hash(col, row + (float) Math.floor(time * FLICKER_RATE)) * CHARSET.length) % CHARSET.length];

                Color drawColor = isHead ? headColor : color;
                font.setColor(drawColor.r, drawColor.g, drawColor.b, brightness * alpha);
                float cx = x + col * cellWidth;
                float cy = y + height - row * cellHeight; // row 0 at the top, falling toward larger rows
                font.draw(batch, String.valueOf(c), cx, cy);
            }
        }

        font.setColor(Color.WHITE);
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    // Same cheap sin-hash trick as ChainFireEffect's GLSL hash(vec2), just evaluated on the CPU.
    // Uses Math.floor (not MathUtils.floor) deliberately: MathUtils.floor's fast int-cast trick
    // only holds for inputs roughly within +/-16384, and s here regularly reaches +/-43758 (the
    // sin(...) * 43758.5453123f magic constant), which broke it into returning values outside
    // [0, 1) - including negative ones that overflowed the CHARSET index below.
    private static float hash(float x, float y) {
        float s = (float) Math.sin(x * 127.1f + y * 311.7f) * 43758.5453123f;
        return (float) (s - Math.floor(s));
    }
}
