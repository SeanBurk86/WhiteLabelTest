package whitelabeltest.player.weapons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.player.Player;

public class ThunderboltWeapon extends BaseWeapon {
    private static final float STRIKE_DURATION = 0.1f;
    private static final float FADE_DURATION = 0.7f;
    // Grows the bolt's visual thickness/jitter envelope with level - purely cosmetic now (targeting
    // is by nearest-enemy selection, not an area the bolt has to geometrically cover).
    private static final float WIDTH_GROWTH_PER_LEVEL = 0.75f;
    private static final float HIT_BOLT_THICKNESS = 0.5f;
    private static final float FORK_ALPHA_SCALE = 0.75f;

    private static final float GLOW_OUTER_WIDTH_SCALE = 2.0f;
    private static final float GLOW_INNER_WIDTH_SCALE = 1.3f;
    private static final float OUTER_TINT_G = 0.15f, OUTER_TINT_B = 0.15f;
    private static final float INNER_TINT_G = 0.2f, INNER_TINT_B = 0.2f;

    private static final float CORE_ALPHA_SCALE = 1.0f;
    private static final float CORE_WIDTH_SCALE = 0.5f;

    // Same hue as the additive glow, just driven far down in value, and drawn with normal alpha
    // blending (see drawOutline()) instead of GL_MAX - MAX can only brighten a pixel, so against a
    // light background (bg1.png) a "darker" additive layer would just lose to the background and
    // vanish. This layer is what actually reads as dark, visible contrast there.
    private static final float OUTLINE_WIDTH_SCALE = 2.4f;
    private static final float OUTLINE_COLOR_R = 0.12f, OUTLINE_COLOR_G = 0.02f, OUTLINE_COLOR_B = 0.02f;
    private static final float OUTLINE_ALPHA_SCALE = 0.6f;

    private static final float FADE_BLUR_GROWTH = 2.0f;

    private static final float NOISE_AMPLITUDE = 0.04f;
    private static final float NOISE_SPEED_1 = 40f;
    private static final float NOISE_SPEED_2 = 23f;

    private static final int SPRITES_PER_LAYER = 5;
    // Layers 0-2 (outer glow, inner glow, core) are additive/GL_MAX; layer 3 (outline) is normal
    // alpha blend - see drawOutline()/drawGlowAndCore() for why they're drawn in separate passes.
    private static final int LAYER_COUNT = 4;
    private static final int OUTLINE_LAYER_OFFSET = SPRITES_PER_LAYER * 3;
    private static final int SPRITES_PER_SEGMENT = SPRITES_PER_LAYER * LAYER_COUNT;

    // ThunderboltWeapon's Hyper Attack: see Player.triggerThunderboltHyperAttack for the halo's
    // charge-then-detonate behavior this only kicks off - all of the actual charge/damage/arc
    // logic lives on Player/CollisionManager, the same split BasicWeapon's halo dash uses.
    // Uncooldowned, unlike WaveBlastWeapon's hyper attack - Player.triggerThunderboltHyperAttack
    // already refuses to start a second charge while the halo's still out on this one, so there's
    // nothing left for a cooldown to gate.

    // Public: EntityManager batches the GL_MAX blend section across every active strike instead
    // of each one flushing/toggling it independently - see drawBolts()/draw().
    public static final int GL_MAX = 0x8008;

    private WeaponDefinition def;
    private Texture texture;
    private Texture circleTexture;
    private final Vector2 origin = new Vector2();
    // The single enemy this bolt was aimed at when fired (see ThunderboltWeapon.spawn()'s
    // nearest-enemy selection) - no hitbox/direction/range check involved, hasDamaged() just
    // refuses to damage anything else. rectangle is bound to this enemy's own rect at init() time
    // so the normal per-frame CollisionManager overlap test resolves against it directly.
    private Enemy target;
    private boolean hit;
    // Whether the equipped instance's most recent spawn() found any enemy to strike - see
    // playFireSound(). Meaningless on a pooled per-bolt instance; only the equipped weapon-slot
    // instance's spawn()/playFireSound() pair reads and writes it.
    private boolean foundTarget;
    // Scratch buffer for spawn()'s nearest-enemy sort - only ever used on the equipped weapon
    // instance (spawn() is called on `this`, never on a pooled bolt), not per-bolt state.
    private final Array<Enemy> nearestEnemies = new Array<>(false, 16);
    private final Array<Sprite> bolts = new Array<>(false, 96);

    private final FloatArray segX1 = new FloatArray(16);
    private final FloatArray segY1 = new FloatArray(16);
    private final FloatArray segX2 = new FloatArray(16);
    private final FloatArray segY2 = new FloatArray(16);
    private final FloatArray segWidth = new FloatArray(16);
    private final FloatArray segAlpha = new FloatArray(16);
    private final FloatArray segPhase = new FloatArray(16);
    private int segCount = 0;
    private float lifeTime = 0f;

    /** Configures the weapon-slot instance's definition only - unlike the full init() below (used
     *  for an actual fired bolt), this instance is never itself drawn or collided (see Player's
     *  currentWeapon dispatch); spawn()/getFireRate() just read its def. */
    public void initDefinition(WeaponDefinition def) {
        this.def = def;
    }

    public void init(WeaponDefinition def, Texture texture, Texture circleTexture, Vector2 origin, Enemy target, float width) {
        this.def = def;
        this.texture = texture;
        this.circleTexture = circleTexture;
        this.origin.set(origin);
        this.lifeTime = 0f;
        this.target = target;
        this.hit = false;
        this.segCount = 0;

        this.damage = def.getDamage(level);
        this.chainWindow = def.chainWindow;
        this.shootSpeedMultiplier = def.shootSpeedMultiplier;
        this.hitAnimation = def.hitAnimation;
        this.hitEffectSize = def.hitSize;
        this.velocity.setZero();
        this.path = null;

        // Bound to the target's own rect (not a swept area) so the generic bullet/enemy overlap
        // test in CollisionManager resolves this strike against exactly the enemy it was aimed at.
        rectangle.set(target.getRectangle());

        float targetX = rectangle.x + rectangle.width / 2f;
        float targetY = rectangle.y + rectangle.height / 2f;
        long seed = System.nanoTime() ^ ((long) System.identityHashCode(this) << 32);
        addArc(targetX, targetY, width / 2f, seed);
    }

    private void addArc(float targetX, float targetY, float jitterHalfWidth, long seed) {
        float dx = targetX - origin.x, dy = targetY - origin.y;
        float length = Math.max((float) Math.sqrt(dx * dx + dy * dy), 0.01f);
        float dirX = dx / length, dirY = dy / length;

        Array<LightningBolt.Segment> segments = LightningBolt.generate(0f, 0f, length, 0f, seed, HIT_BOLT_THICKNESS,
            0f, length, -jitterHalfWidth, jitterHalfWidth);
        for (LightningBolt.Segment seg : segments) {
            float x1 = origin.x + dirX * seg.x1 - dirY * seg.y1;
            float y1 = origin.y + dirY * seg.x1 + dirX * seg.y1;
            float x2 = origin.x + dirX * seg.x2 - dirY * seg.y2;
            float y2 = origin.y + dirY * seg.x2 + dirX * seg.y2;
            addBoltSegment(x1, y1, x2, y2, seg.width, seg.isFork ? FORK_ALPHA_SCALE : 1.0f);
        }
    }

    private void addBoltSegment(float x1, float y1, float x2, float y2, float width, float alpha) {
        int i = segCount++;
        setOrAdd(segX1, i, x1);
        setOrAdd(segY1, i, y1);
        setOrAdd(segX2, i, x2);
        setOrAdd(segY2, i, y2);
        setOrAdd(segWidth, i, width);
        setOrAdd(segAlpha, i, alpha);
        setOrAdd(segPhase, i, MathUtils.random(0f, 1000f));

        int base = i * SPRITES_PER_SEGMENT;
        prepareLayer(base);
        prepareLayer(base + SPRITES_PER_LAYER);
        prepareLayer(base + SPRITES_PER_LAYER * 2);
        prepareLayer(base + OUTLINE_LAYER_OFFSET);

        repositionSegment(i, fadeProgress());
    }

    private float fadeProgress() {
        return MathUtils.clamp(lifeTime / FADE_DURATION, 0f, 1f);
    }

    // Smoothstep falloff: 1 at t=0 (just spawned), 0 at t=1 (fully faded).
    private static float easeFade(float t) {
        float eased = t * t * (3f - 2f * t);
        return 1f - eased;
    }

    private static void setOrAdd(FloatArray arr, int index, float value) {
        if (index < arr.size) arr.set(index, value);
        else arr.add(value);
    }

    private void prepareLayer(int base) {
        for (int k = 0; k < SPRITES_PER_LAYER; k++) {
            Sprite bolt = obtainBoltSprite(base + k);
            // k 0,1 = the two half-lines (square/rectangular); k 2,3,4 = the joint caps (round).
            bolt.setRegion(k < 2 ? texture : circleTexture);
        }
    }

    private Sprite obtainBoltSprite(int index) {
        while (bolts.size <= index) {
            bolts.add(new Sprite(texture));
        }
        return bolts.get(index);
    }

    private void repositionSegment(int i, float t) {
        float x1 = segX1.get(i), y1 = segY1.get(i);
        float x2 = segX2.get(i), y2 = segY2.get(i);
        float width = segWidth.get(i);
        float baseAlpha = segAlpha.get(i) * easeFade(t);
        float phase = segPhase.get(i);

        float dx = x2 - x1, dy = y2 - y1;
        float len = Math.max((float) Math.sqrt(dx * dx + dy * dy), 0.0001f);
        float perpX = -dy / len, perpY = dx / len;

        float wobble = (MathUtils.sin(lifeTime * NOISE_SPEED_1 + phase) + MathUtils.sin(lifeTime * NOISE_SPEED_2 + phase * 1.7f))
            * 0.5f * NOISE_AMPLITUDE;
        float midX = (x1 + x2) / 2f + perpX * wobble;
        float midY = (y1 + y2) / 2f + perpY * wobble;

        // The three glow/core layers are drawn with a MAX blend equation (see draw()) so
        // overlapping joints - e.g. where one segment's end cap sits on top of the next segment's
        // start cap - don't compound into a brighter seam the way normal alpha blending would;
        // max(a, a) is just a, no matter how many times the same spot gets drawn. MAX ignores
        // blend factors entirely, so their fade has to be baked into RGB brightness instead of the
        // alpha channel. The glow layers also spread wider as they fade (blurScale), a cheap
        // stand-in for a real blur. The outline layer is normal alpha blend instead (see
        // drawOutline()), so its fade is carried in the alpha channel like usual.
        float blurScale = 1f + FADE_BLUR_GROWTH * t;
        float coreBrightness = baseAlpha * CORE_ALPHA_SCALE;

        int base = i * SPRITES_PER_SEGMENT;
        positionLayer(base, x1, y1, midX, midY, x2, y2, width * GLOW_OUTER_WIDTH_SCALE * blurScale,
            baseAlpha, OUTER_TINT_G * baseAlpha, OUTER_TINT_B * baseAlpha, 1f);
        positionLayer(base + SPRITES_PER_LAYER, x1, y1, midX, midY, x2, y2, width * GLOW_INNER_WIDTH_SCALE * blurScale,
            baseAlpha, INNER_TINT_G * baseAlpha, INNER_TINT_B * baseAlpha, 1f);
        positionLayer(base + SPRITES_PER_LAYER * 2, x1, y1, midX, midY, x2, y2, width * CORE_WIDTH_SCALE,
            coreBrightness, coreBrightness, coreBrightness, 1f);
        positionLayer(base + OUTLINE_LAYER_OFFSET, x1, y1, midX, midY, x2, y2, width * OUTLINE_WIDTH_SCALE * blurScale,
            OUTLINE_COLOR_R, OUTLINE_COLOR_G, OUTLINE_COLOR_B, baseAlpha * OUTLINE_ALPHA_SCALE);
    }

    private void positionLayer(int base, float x1, float y1, float midX, float midY, float x2, float y2, float width, float r, float g, float b, float a) {
        positionHalf(base, x1, y1, midX, midY, width, r, g, b, a);
        positionHalf(base + 1, midX, midY, x2, y2, width, r, g, b, a);
        positionCap(base + 2, x1, y1, width, r, g, b, a);
        positionCap(base + 3, midX, midY, width, r, g, b, a);
        positionCap(base + 4, x2, y2, width, r, g, b, a);
    }

    private void positionHalf(int spriteIndex, float ax, float ay, float bx, float by, float width, float r, float g, float b, float a) {
        Sprite bolt = bolts.get(spriteIndex);
        float dx = bx - ax, dy = by - ay;
        float dist = Math.max((float) Math.sqrt(dx * dx + dy * dy), 0.01f);

        bolt.setSize(width, dist);
        bolt.setOrigin(width / 2f, 0f);
        bolt.setPosition(ax - width / 2f, ay);
        bolt.setRotation((float) Math.toDegrees(Math.atan2(dy, dx)) - 90f);
        bolt.setColor(r, g, b, a);
    }

    private void positionCap(int spriteIndex, float cx, float cy, float width, float r, float g, float b, float a) {
        Sprite bolt = bolts.get(spriteIndex);
        bolt.setSize(width, width);
        bolt.setOrigin(width / 2f, width / 2f);
        bolt.setPosition(cx - width / 2f, cy - width / 2f);
        bolt.setRotation(0f);
        bolt.setColor(r, g, b, a);
    }

    @Override
    public void update(float delta) {
        lifeTime += delta;
        float t = fadeProgress();

        for (int i = 0; i < segCount; i++) {
            repositionSegment(i, t);
        }
    }

    /** Draws just this strike's dark outline layer, with no blend-state changes of its own - it
     *  needs normal alpha blending (not the glow/core layers' GL_MAX), so callers must draw it
     *  before opening a GL_MAX section. See EntityManager.drawThunderboltBolts for the batched
     *  multi-strike version this exists for. */
    public void drawOutline(SpriteBatch batch) {
        for (int i = 0; i < segCount; i++) {
            int base = i * SPRITES_PER_SEGMENT + OUTLINE_LAYER_OFFSET;
            for (int k = 0; k < SPRITES_PER_LAYER; k++) {
                bolts.get(base + k).draw(batch);
            }
        }
    }

    /** Draws just this strike's outer glow, inner glow, and core sprites, with no blend-state
     *  changes of its own - callers that have several active strikes (the common case: a level-4
     *  fire spawns up to 6 at once) must wrap the whole batch of them in a single GL_MAX blend
     *  section themselves (see EntityManager.drawThunderboltBolts), so the flush()/
     *  glBlendEquation() cost - each a forced GPU sync point - is paid once per frame instead of
     *  once per strike. */
    public void drawGlowAndCore(SpriteBatch batch) {
        for (int i = 0; i < segCount; i++) {
            int base = i * SPRITES_PER_SEGMENT;
            for (int k = 0; k < OUTLINE_LAYER_OFFSET; k++) {
                bolts.get(base + k).draw(batch);
            }
        }
    }

    /** Self-contained fallback for callers that draw a single strike in isolation - draws the
     *  outline with the batch's normal blending, then wraps drawGlowAndCore() in its own GL_MAX
     *  section. EntityManager doesn't use this path; it calls drawOutline()/drawGlowAndCore()
     *  directly inside its own shared sections instead (see drawThunderboltBolts). */
    @Override
    public void draw(SpriteBatch batch) {
        int srcFunc = batch.getBlendSrcFunc();
        int dstFunc = batch.getBlendDstFunc();

        drawOutline(batch);

        batch.flush();
        Gdx.gl.glBlendEquation(GL_MAX);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        drawGlowAndCore(batch);

        batch.flush();
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        batch.setBlendFunction(srcFunc, dstFunc);
    }

    @Override
    public boolean isOffScreen(float worldHeight) {
        return lifeTime >= FADE_DURATION;
    }

    @Override
    public boolean shouldDestroyOnCollision() {
        return false;
    }

    @Override
    public boolean hasDamaged(Enemy enemy) {
        if (lifeTime >= STRIKE_DURATION) return true;
        if (enemy != target) return true;
        return hit;
    }

    @Override
    public void markDamaged(Enemy enemy) {
        hit = true;
    }

    /** Fires one bolt per target - up to 2 at level 1, 4/6/8 at higher levels - at whichever
     *  enemies are currently closest to the player, full screen, no direction or range check.
     *  With fewer active enemies than bolts to fire, extra bolts double up on the closest ones
     *  again (round-robin from the front of the sorted list) instead of going unfired, so each
     *  stacks its own instance of the weapon's damage onto the same target. */
    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        nearestEnemies.clear();
        for (int i = 0; i < enemies.size; i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.isActive() && enemy.isTargetableByHoming()) nearestEnemies.add(enemy);
        }
        // Read by playFireSound() right after this call returns (see Player.handleShooting) to
        // decide between the normal weapon sound and the nulllightning.mp3 whiff.
        foundTarget = nearestEnemies.size > 0;
        if (!foundTarget) return;

        Vector2 origin = new Vector2(player.getCenterX(), player.getCenterY());
        nearestEnemies.sort((a, b) -> Float.compare(distanceSqTo(origin, a), distanceSqTo(origin, b)));

        // Grows a little with every level (not just a single jump at level 2), purely cosmetic now.
        float width = def.size * (1f + (level - 1) * WIDTH_GROWTH_PER_LEVEL);
        int boltCount = MathUtils.clamp(level, 1, 4) * 2;
        Texture pixel = assets.pixelTexture;
        Texture circle = assets.circleTexture;

        for (int i = 0; i < boltCount; i++) {
            Enemy boltTarget = nearestEnemies.get(i % nearestEnemies.size);
            strike(activeWeapons, pixel, circle, origin, boltTarget, width);
        }
    }

    private static float distanceSqTo(Vector2 origin, Enemy enemy) {
        Rectangle r = enemy.getRectangle();
        float dx = r.x + r.width / 2f - origin.x;
        float dy = r.y + r.height / 2f - origin.y;
        return dx * dx + dy * dy;
    }

    private void strike(Array<Weapon> activeWeapons, Texture texture, Texture circleTexture, Vector2 origin, Enemy target, float width) {
        ThunderboltWeapon w = ObjectPools.thunderboltWeaponPool.obtain();
        w.setLevel(this.level);
        w.init(def, texture, circleTexture, origin, target, width);
        activeWeapons.add(w);
    }

    @Override
    public float getFireRate() { return def.getFireRate(level); }

    /** Only the normal weapon sound if the shot that just fired (see spawn()) actually found an
     *  enemy to strike - otherwise the nulllightning.mp3 whiff, since a bolt with nothing to
     *  target never even spawns. */
    @Override
    public void playFireSound(AudioManager audio, int level) {
        if (foundTarget) audio.playThunderboltWeaponSound(level);
        else audio.playThunderboltNullSound();
    }

    /** Starts the halo's charge-then-detonate sequence (see Player.triggerThunderboltHyperAttack)
     *  - no cooldown of its own; Player already refuses to start a new charge while the halo's
     *  still out from the last one. */
    @Override
    public void hyperAttack(Player player, Array<Weapon> activeWeapons, Array<Enemy> enemies, AssetManager assets, AudioManager audio) {
        player.triggerThunderboltHyperAttack();
    }

    @Override
    public void reset() {
        super.reset();
        lifeTime = 0f;
        target = null;
        hit = false;
        segCount = 0;
    }
}
