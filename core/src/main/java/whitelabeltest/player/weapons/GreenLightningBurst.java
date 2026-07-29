package whitelabeltest.player.weapons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.Pool;

public class GreenLightningBurst implements Pool.Poolable {
    private static final float FADE_DURATION = 0.5f;
    private static final float BOLT_THICKNESS = 0.22f;
    private static final float JITTER_HALF_WIDTH_SCALE = 0.18f;
    private static final float MIN_JITTER_HALF_WIDTH = 0.15f;
    private static final float FORK_ALPHA_SCALE = 0.7f;

    private static final float GLOW_WIDTH_SCALE = 2.2f;
    private static final float GLOW_ALPHA_SCALE = 0.5f;
    private static final float CORE_WIDTH_SCALE = 0.45f;
    private static final float TINT_R = 0.1f, TINT_G = 0.9f, TINT_B = 0.1f;

    private static final int SPRITES_PER_LAYER = 5;
    private static final int LAYER_COUNT = 2;
    private static final int SPRITES_PER_SEGMENT = SPRITES_PER_LAYER * LAYER_COUNT;

    public static final int GL_MAX = 0x8008;

    private Texture lineTexture;
    private Texture capTexture;
    private final Array<Sprite> bolts = new Array<>(false, 64);

    private final FloatArray segX1 = new FloatArray(16);
    private final FloatArray segY1 = new FloatArray(16);
    private final FloatArray segX2 = new FloatArray(16);
    private final FloatArray segY2 = new FloatArray(16);
    private final FloatArray segWidth = new FloatArray(16);
    private final FloatArray segAlpha = new FloatArray(16);
    private int segCount;
    private float lifeTime;

    public void init(Texture lineTexture, Texture capTexture, float originX, float originY, Array<Vector2> targets) {
        this.lineTexture = lineTexture;
        this.capTexture = capTexture;
        this.segCount = 0;
        this.lifeTime = 0f;

        long baseSeed = System.nanoTime();
        for (int t = 0; t < targets.size; t++) {
            Vector2 target = targets.get(t);
            addArc(originX, originY, target.x, target.y, baseSeed ^ ((long) t * 0x9E3779B97F4A7C15L));
        }
        repositionAll();
    }

    private void addArc(float originX, float originY, float targetX, float targetY, long seed) {
        float dx = targetX - originX, dy = targetY - originY;
        float dist = Math.max((float) Math.sqrt(dx * dx + dy * dy), 0.01f);
        float dirX = dx / dist, dirY = dy / dist;
        float halfWidth = Math.max(dist * JITTER_HALF_WIDTH_SCALE, MIN_JITTER_HALF_WIDTH);

        Array<LightningBolt.Segment> segments = LightningBolt.generate(0f, 0f, dist, 0f, seed, BOLT_THICKNESS,
            0f, dist, -halfWidth, halfWidth);
        for (LightningBolt.Segment seg : segments) {
            float x1 = originX + dirX * seg.x1 - dirY * seg.y1;
            float y1 = originY + dirY * seg.x1 + dirX * seg.y1;
            float x2 = originX + dirX * seg.x2 - dirY * seg.y2;
            float y2 = originY + dirY * seg.x2 + dirX * seg.y2;
            addSegment(x1, y1, x2, y2, seg.width, seg.isFork ? FORK_ALPHA_SCALE : 1f);
        }
    }

    private void addSegment(float x1, float y1, float x2, float y2, float width, float alpha) {
        int i = segCount++;
        setOrAdd(segX1, i, x1);
        setOrAdd(segY1, i, y1);
        setOrAdd(segX2, i, x2);
        setOrAdd(segY2, i, y2);
        setOrAdd(segWidth, i, width);
        setOrAdd(segAlpha, i, alpha);

        int base = i * SPRITES_PER_SEGMENT;
        prepareLayer(base);
        prepareLayer(base + SPRITES_PER_LAYER);
    }

    private static void setOrAdd(FloatArray arr, int index, float value) {
        if (index < arr.size) arr.set(index, value);
        else arr.add(value);
    }

    private void prepareLayer(int base) {
        for (int k = 0; k < SPRITES_PER_LAYER; k++) {
            Sprite bolt = obtainBoltSprite(base + k);
            // k 0,1 = the two half-lines (square/rectangular); k 2,3,4 = the joint caps (round).
            bolt.setRegion(k < 2 ? lineTexture : capTexture);
        }
    }

    private Sprite obtainBoltSprite(int index) {
        while (bolts.size <= index) {
            bolts.add(new Sprite(lineTexture));
        }
        return bolts.get(index);
    }

    public void update(float delta) {
        lifeTime += delta;
        repositionAll();
    }

    private void repositionAll() {
        float t = MathUtils.clamp(lifeTime / FADE_DURATION, 0f, 1f);
        float eased = t * t * (3f - 2f * t);
        float fade = 1f - eased;
        for (int i = 0; i < segCount; i++) repositionSegment(i, fade);
    }

    private void repositionSegment(int i, float fade) {
        float x1 = segX1.get(i), y1 = segY1.get(i);
        float x2 = segX2.get(i), y2 = segY2.get(i);
        float width = segWidth.get(i);
        float alpha = segAlpha.get(i) * fade;

        float midX = (x1 + x2) / 2f;
        float midY = (y1 + y2) / 2f;

        int base = i * SPRITES_PER_SEGMENT;
        positionLayer(base, x1, y1, midX, midY, x2, y2, width * GLOW_WIDTH_SCALE, alpha * GLOW_ALPHA_SCALE);
        positionLayer(base + SPRITES_PER_LAYER, x1, y1, midX, midY, x2, y2, width * CORE_WIDTH_SCALE, alpha);
    }

    private void positionLayer(int base, float x1, float y1, float midX, float midY, float x2, float y2, float width, float brightness) {
        positionHalf(base, x1, y1, midX, midY, width, brightness);
        positionHalf(base + 1, midX, midY, x2, y2, width, brightness);
        positionCap(base + 2, x1, y1, width, brightness);
        positionCap(base + 3, midX, midY, width, brightness);
        positionCap(base + 4, x2, y2, width, brightness);
    }

    private void positionHalf(int spriteIndex, float ax, float ay, float bx, float by, float width, float brightness) {
        Sprite bolt = bolts.get(spriteIndex);
        float dx = bx - ax, dy = by - ay;
        float dist = Math.max((float) Math.sqrt(dx * dx + dy * dy), 0.01f);

        bolt.setSize(width, dist);
        bolt.setOrigin(width / 2f, 0f);
        bolt.setPosition(ax - width / 2f, ay);
        bolt.setRotation((float) Math.toDegrees(Math.atan2(dy, dx)) - 90f);
        bolt.setColor(TINT_R * brightness, TINT_G * brightness, TINT_B * brightness, 1f);
    }

    private void positionCap(int spriteIndex, float cx, float cy, float width, float brightness) {
        Sprite bolt = bolts.get(spriteIndex);
        bolt.setSize(width, width);
        bolt.setOrigin(width / 2f, width / 2f);
        bolt.setPosition(cx - width / 2f, cy - width / 2f);
        bolt.setRotation(0f);
        bolt.setColor(TINT_R * brightness, TINT_G * brightness, TINT_B * brightness, 1f);
    }

    public void draw(SpriteBatch batch) {
        int srcFunc = batch.getBlendSrcFunc();
        int dstFunc = batch.getBlendDstFunc();

        batch.flush();
        Gdx.gl.glBlendEquation(GL_MAX);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        int spriteCount = segCount * SPRITES_PER_SEGMENT;
        for (int i = 0; i < spriteCount; i++) bolts.get(i).draw(batch);

        batch.flush();
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        batch.setBlendFunction(srcFunc, dstFunc);
    }

    public boolean isFinished() {
        return lifeTime >= FADE_DURATION;
    }

    @Override
    public void reset() {
        segCount = 0;
        lifeTime = 0f;
    }
}
