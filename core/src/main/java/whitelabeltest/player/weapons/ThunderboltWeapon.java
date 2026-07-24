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
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.player.Player;

public class ThunderboltWeapon extends BaseWeapon {
    private static final float STRIKE_DURATION = 0.1f;
    private static final float FADE_DURATION = 0.7f;
    private static final float HITBOX_GROWTH_PER_LEVEL = 0.75f;
    private static final float MISS_BOLT_THICKNESS = 0.0625f;
    private static final float HIT_BOLT_THICKNESS = 0.33f;
    private static final float FORK_ALPHA_SCALE = 0.75f;
    private static final float MISS_ALPHA_SCALE = 0.75f;
    private static final float MISS_WIDTH_FALLOFF = 0.75f;
    private static final int MISS_ARC_COUNT = 18;

    private static final int ROUGHEN_DETAIL = 2;
    private static final float ROUGHEN_JITTER = 0.4f;
    private static final float ROUGHEN_ROUGHNESS = 0.5f;

    private static final float GLOW_OUTER_WIDTH_SCALE = 2.0f;
    private static final float GLOW_INNER_WIDTH_SCALE = 1.3f;
    private static final float OUTER_TINT_G = 0.15f, OUTER_TINT_B = 0.15f;
    private static final float INNER_TINT_G = 0.2f, INNER_TINT_B = 0.2f;

    // Hyper Attack strikes tint the same white-hot core toward the project's established
    // "mystic" purple (see ChainFireEffect's MYSTIC_* ramp) instead of the normal bolt's
    // red/orange glow - unlike the normal tints, R is also pulled down so the glow reads as
    // violet rather than white.
    private static final float HYPER_OUTER_TINT_R = 0.55f, HYPER_OUTER_TINT_G = 0.12f, HYPER_OUTER_TINT_B = 0.95f;
    private static final float HYPER_INNER_TINT_R = 0.65f, HYPER_INNER_TINT_G = 0.22f, HYPER_INNER_TINT_B = 1.0f;

    private static final float CORE_ALPHA_SCALE = 1.0f;
    private static final float CORE_WIDTH_SCALE = 0.5f;

    // Hyper Attack: fires a full strike volley (same level-scaled shape as a normal spawn()) on
    // its own cooldown; any enemy it hits also has every bullet it has currently fired arced to
    // and destroyed (see markDamaged()).
    private static final float HYPER_ATTACK_COOLDOWN = 6f;
    private static final float CHAIN_BOLT_THICKNESS = 0.2f;
    private float hyperAttackCooldownTimer;

    private static final float FADE_BLUR_GROWTH = 2.0f;

    private static final float NOISE_AMPLITUDE = 0.04f;
    private static final float NOISE_SPEED_1 = 40f;
    private static final float NOISE_SPEED_2 = 23f;

    private static final int SPRITES_PER_LAYER = 5;
    private static final int LAYER_COUNT = 3;
    private static final int SPRITES_PER_SEGMENT = SPRITES_PER_LAYER * LAYER_COUNT;

    // Public: EntityManager batches the GL_MAX blend section across every active strike instead
    // of each one flushing/toggling it independently - see drawBolts()/draw().
    public static final int GL_MAX = 0x8008;

    private WeaponDefinition def;
    private Texture texture;
    private Texture circleTexture;
    private final Vector2 origin = new Vector2();
    private final Array<Enemy> hitEnemies = new Array<>(false, 4);
    private final Array<Sprite> bolts = new Array<>(false, 96);

    private final FloatArray segX1 = new FloatArray(16);
    private final FloatArray segY1 = new FloatArray(16);
    private final FloatArray segX2 = new FloatArray(16);
    private final FloatArray segY2 = new FloatArray(16);
    private final FloatArray segWidth = new FloatArray(16);
    private final FloatArray segAlpha = new FloatArray(16);
    private final FloatArray segPhase = new FloatArray(16);
    private int segCount = 0;
    private boolean hasRealHit = false;
    private boolean isHyper = false;
    private float lifeTime = 0f;
    private float rotationDeg = 0f;

    private float dirX, dirY;
    private float halfWidth;
    private float endReach;

    public void init(WeaponDefinition def, Texture texture, Texture circleTexture, Vector2 origin, Vector2 dir, float width, float worldHeight) {
        init(def, texture, circleTexture, origin, dir, width, worldHeight, false);
    }

    public void init(WeaponDefinition def, Texture texture, Texture circleTexture, Vector2 origin, Vector2 dir, float width, float worldHeight, boolean isHyper) {
        this.def = def;
        this.texture = texture;
        this.circleTexture = circleTexture;
        this.origin.set(origin);
        this.lifeTime = 0f;
        this.hitEnemies.clear();
        this.segCount = 0;
        this.hasRealHit = false;
        this.isHyper = isHyper;

        float startReach = def.radius;
        float endReach = Math.max(edgeDistance(origin, dir, worldHeight), startReach + 0.1f);

        float startX = origin.x + dir.x * startReach;
        float startY = origin.y + dir.y * startReach;
        float endX = origin.x + dir.x * endReach;
        float endY = origin.y + dir.y * endReach;

        this.damage = def.getDamage(level);
        this.chainWindow = def.chainWindow;
        this.shootSpeedMultiplier = def.shootSpeedMultiplier;
        this.hitAnimation = def.hitAnimation;
        this.hitEffectSize = def.hitSize;
        this.velocity.setZero();
        this.path = null;

        float halfWidth = width / 2f;
        float perpUnitX = -dir.y;
        float perpUnitY = dir.x;
        this.dirX = dir.x;
        this.dirY = dir.y;
        this.halfWidth = halfWidth;
        this.endReach = endReach;

        // Un-rotated hitbox, same size as a straight (0,1) strike (e.g. level 2's), pivoted at the
        // origin; getRotation()/getRotationPivotX/Y() let collision test it as a true rotated rect
        // instead of inflating an axis-aligned box around the rotated corners.
        rectangle.set(origin.x - halfWidth, origin.y + startReach, width, endReach - startReach);
        rotationDeg = dir.angleDeg() - 90f;

        generateMissArcs(startX, startY, dir.x, dir.y, perpUnitX, perpUnitY, endReach - startReach, halfWidth);
    }

    private static float edgeDistance(Vector2 origin, Vector2 dir, float worldHeight) {
        if (dir.y > 0.0001f) return (worldHeight - origin.y) / dir.y;
        if (dir.y < -0.0001f) return -origin.y / dir.y;
        return 4f;
    }

    private void generateMissArcs(float startX, float startY, float dirX, float dirY, float perpUnitX, float perpUnitY, float length, float halfWidth) {
        long seed = System.nanoTime() ^ ((long) System.identityHashCode(this) << 32);
        Array<RRTLightning.Edge> edges = RRTLightning.generate(MISS_ARC_COUNT, length, halfWidth, seed);

        for (int e = 0; e < edges.size; e++) {
            RRTLightning.Edge edge = edges.get(e);
            float width = MISS_BOLT_THICKNESS * (float) Math.pow(MISS_WIDTH_FALLOFF, edge.depth);

            // Roughened in beam-local (along, perp) space and clamped to [0, length] x [-halfWidth,
            // halfWidth] so the jagged crackle can't bulge past the hitbox it's meant to depict.
            long edgeSeed = seed ^ ((long) e * 0x9E3779B97F4A7C15L);
            Array<Vector2> jagged = RRTLightning.roughen(edge.alongStart, edge.perpStart, edge.alongEnd, edge.perpEnd,
                ROUGHEN_DETAIL, ROUGHEN_JITTER, ROUGHEN_ROUGHNESS, edgeSeed, 0f, length, -halfWidth, halfWidth);
            for (int p = 0; p < jagged.size - 1; p++) {
                Vector2 a = jagged.get(p);
                Vector2 b = jagged.get(p + 1);
                float x1 = startX + dirX * a.x + perpUnitX * a.y;
                float y1 = startY + dirY * a.x + perpUnitY * a.y;
                float x2 = startX + dirX * b.x + perpUnitX * b.y;
                float y2 = startY + dirY * b.x + perpUnitY * b.y;
                addBoltSegment(x1, y1, x2, y2, width, MISS_ALPHA_SCALE);
            }
        }
    }

    private void addArc(float targetX, float targetY, long seed) {
        float dx = targetX - origin.x, dy = targetY - origin.y;
        float targetAlong = dx * dirX + dy * dirY;
        float targetPerp = dy * dirX - dx * dirY;

        Array<LightningBolt.Segment> segments = LightningBolt.generate(0f, 0f, targetAlong, targetPerp, seed, HIT_BOLT_THICKNESS,
            0f, endReach, -halfWidth, halfWidth);
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

        // Every layer is drawn with a MAX blend equation (see draw()) so overlapping joints -
        // e.g. where one segment's end cap sits on top of the next segment's start cap - don't
        // compound into a brighter seam the way normal alpha blending would; max(a, a) is just a,
        // no matter how many times the same spot gets drawn. MAX ignores blend factors entirely,
        // so the fade has to be baked into RGB brightness instead of the alpha channel. The glow
        // layers also spread wider as they fade (blurScale), a cheap stand-in for a real blur.
        float blurScale = 1f + FADE_BLUR_GROWTH * t;
        float coreBrightness = baseAlpha * CORE_ALPHA_SCALE;

        float outerTintR = isHyper ? HYPER_OUTER_TINT_R : 1f;
        float outerTintG = isHyper ? HYPER_OUTER_TINT_G : OUTER_TINT_G;
        float outerTintB = isHyper ? HYPER_OUTER_TINT_B : OUTER_TINT_B;
        float innerTintR = isHyper ? HYPER_INNER_TINT_R : 1f;
        float innerTintG = isHyper ? HYPER_INNER_TINT_G : INNER_TINT_G;
        float innerTintB = isHyper ? HYPER_INNER_TINT_B : INNER_TINT_B;

        int base = i * SPRITES_PER_SEGMENT;
        positionLayer(base, x1, y1, midX, midY, x2, y2, width * GLOW_OUTER_WIDTH_SCALE * blurScale,
            outerTintR * baseAlpha, outerTintG * baseAlpha, outerTintB * baseAlpha);
        positionLayer(base + SPRITES_PER_LAYER, x1, y1, midX, midY, x2, y2, width * GLOW_INNER_WIDTH_SCALE * blurScale,
            innerTintR * baseAlpha, innerTintG * baseAlpha, innerTintB * baseAlpha);
        positionLayer(base + SPRITES_PER_LAYER * 2, x1, y1, midX, midY, x2, y2, width * CORE_WIDTH_SCALE,
            coreBrightness, coreBrightness, coreBrightness);
    }

    private void positionLayer(int base, float x1, float y1, float midX, float midY, float x2, float y2, float width, float r, float g, float b) {
        positionHalf(base, x1, y1, midX, midY, width, r, g, b);
        positionHalf(base + 1, midX, midY, x2, y2, width, r, g, b);
        positionCap(base + 2, x1, y1, width, r, g, b);
        positionCap(base + 3, midX, midY, width, r, g, b);
        positionCap(base + 4, x2, y2, width, r, g, b);
    }

    private void positionHalf(int spriteIndex, float ax, float ay, float bx, float by, float width, float r, float g, float b) {
        Sprite bolt = bolts.get(spriteIndex);
        float dx = bx - ax, dy = by - ay;
        float dist = Math.max((float) Math.sqrt(dx * dx + dy * dy), 0.01f);

        bolt.setSize(width, dist);
        bolt.setOrigin(width / 2f, 0f);
        bolt.setPosition(ax - width / 2f, ay);
        bolt.setRotation((float) Math.toDegrees(Math.atan2(dy, dx)) - 90f);
        bolt.setColor(r, g, b, 1f);
    }

    private void positionCap(int spriteIndex, float cx, float cy, float width, float r, float g, float b) {
        Sprite bolt = bolts.get(spriteIndex);
        bolt.setSize(width, width);
        bolt.setOrigin(width / 2f, width / 2f);
        bolt.setPosition(cx - width / 2f, cy - width / 2f);
        bolt.setRotation(0f);
        bolt.setColor(r, g, b, 1f);
    }

    @Override
    public void update(float delta) {
        lifeTime += delta;
        float t = fadeProgress();

        for (int i = 0; i < segCount; i++) {
            repositionSegment(i, t);
        }
    }

    /** Draws just this strike's sprites, with no blend-state changes of its own - callers that
     *  have several active strikes (the common case: a level-4 fire spawns up to 6 at once) must
     *  wrap the whole batch of them in a single GL_MAX blend section themselves (see
     *  EntityManager.drawThunderboltBolts), so the flush()/glBlendEquation() cost - each a forced
     *  GPU sync point - is paid once per frame instead of once per strike. */
    public void drawBolts(SpriteBatch batch) {
        int spriteCount = segCount * SPRITES_PER_SEGMENT;
        for (int i = 0; i < spriteCount; i++) {
            bolts.get(i).draw(batch);
        }
    }

    /** Self-contained fallback for callers that draw a single strike in isolation - wraps
     *  drawBolts() in its own GL_MAX blend section. EntityManager doesn't use this path; it calls
     *  drawBolts() directly inside one shared section instead (see drawThunderboltBolts). */
    @Override
    public void draw(SpriteBatch batch) {
        int srcFunc = batch.getBlendSrcFunc();
        int dstFunc = batch.getBlendDstFunc();

        batch.flush();
        Gdx.gl.glBlendEquation(GL_MAX);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        drawBolts(batch);

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
    public float getRotation() {
        return rotationDeg;
    }

    @Override
    public float getRotationPivotX() {
        return origin.x;
    }

    @Override
    public float getRotationPivotY() {
        return origin.y;
    }

    @Override
    public boolean hasDamaged(Enemy enemy) {
        if (lifeTime >= STRIKE_DURATION) return true;
        return hitEnemies.contains(enemy, true);
    }

    @Override
    public void markDamaged(Enemy enemy, EntityManager entityManager) {
        hitEnemies.add(enemy);
        if (!hasRealHit) {
            hasRealHit = true;
            segCount = 0;
        }

        float px = enemy.getRectangle().x + enemy.getRectangle().width / 2f;
        float py = enemy.getRectangle().y + enemy.getRectangle().height / 2f;

        long seed = System.nanoTime() ^ ((long) System.identityHashCode(enemy) << 32);
        addArc(px, py, seed);

        if (isHyper) chainDestroyBullets(enemy, px, py, entityManager);
    }

    /** Hyper Attack only: arcs from the struck enemy to every bullet it has currently fired
     *  (matched via EnemyBullet.getSourceEnemy(), the same link the reflect shield uses - see
     *  CollisionManager.checkShieldReflections) and destroys each one. */
    private void chainDestroyBullets(Enemy enemy, float fromX, float fromY, EntityManager entityManager) {
        Array<EnemyBullet> enemyBullets = entityManager.getEnemyBullets();
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet bullet = enemyBullets.get(i);
            if (bullet.getSourceEnemy() != enemy) continue;

            Rectangle rect = bullet.getRectangle();
            float bx = rect.x + rect.width / 2f;
            float by = rect.y + rect.height / 2f;
            long seed = System.nanoTime() ^ ((long) System.identityHashCode(bullet) << 32);
            addChainArc(fromX, fromY, bx, by, seed);

            enemyBullets.removeIndex(i);
            ObjectPools.freeEnemyBullet(bullet);
        }
    }

    /** Draws a lightning arc directly between two arbitrary world points - unlike addArc(), which
     *  is anchored to this strike's own origin/direction/reach, this builds its own local
     *  along/perp frame from fromX/fromY toward toX/toY so it can connect an enemy to each of its
     *  bullets regardless of where this strike's hitbox actually is. */
    private void addChainArc(float fromX, float fromY, float toX, float toY, long seed) {
        float dx = toX - fromX, dy = toY - fromY;
        float dist = Math.max((float) Math.sqrt(dx * dx + dy * dy), 0.01f);
        float dirX = dx / dist, dirY = dy / dist;
        float perpX = -dirY, perpY = dirX;
        float perpBound = MathUtils.clamp(dist * 0.2f, 0.15f, 0.6f);

        Array<LightningBolt.Segment> segments = LightningBolt.generate(0f, 0f, dist, 0f, seed, CHAIN_BOLT_THICKNESS,
            0f, dist, -perpBound, perpBound);
        for (LightningBolt.Segment seg : segments) {
            float x1 = fromX + dirX * seg.x1 + perpX * seg.y1;
            float y1 = fromY + dirY * seg.x1 + perpY * seg.y1;
            float x2 = fromX + dirX * seg.x2 + perpX * seg.y2;
            float y2 = fromY + dirY * seg.x2 + perpY * seg.y2;
            addBoltSegment(x1, y1, x2, y2, seg.width, seg.isFork ? FORK_ALPHA_SCALE : 1.0f);
        }
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        Vector2 origin = new Vector2(player.getCenterX(), player.getCenterY());
        float width = strikeWidth();
        float worldHeight = player.getWorldHeight();
        spawnLevelStrikes(activeWeapons, assets.pixelTexture, assets.circleTexture, origin, width, worldHeight, false);
    }

    // Grows a little with every level (not just a single jump at level 2) so an enemy caught
    // where multiple strikes overlap is more likely to sit in several hitboxes at once.
    private float strikeWidth() {
        return def.size * (1f + (level - 1) * HITBOX_GROWTH_PER_LEVEL);
    }

    /** Shared by both the normal attack and the Hyper Attack so the Hyper Attack always fires the
     *  same level-scaled volley (1 strike at level 1-2, 3 at level 3, 6 at level 4) the weapon's
     *  normal attack would - just tinted purple and, on hit, chaining into the target's bullets. */
    private void spawnLevelStrikes(Array<Weapon> activeWeapons, Texture pixel, Texture circle, Vector2 origin, float width, float worldHeight, boolean isHyper) {
        strike(activeWeapons, pixel, circle, origin, new Vector2(0, 1), width, worldHeight, isHyper);

        if (level >= 3) {
            strike(activeWeapons, pixel, circle, origin, new Vector2(0, 1).rotateDeg(45), width, worldHeight, isHyper);
            strike(activeWeapons, pixel, circle, origin, new Vector2(0, 1).rotateDeg(-45), width, worldHeight, isHyper);
        }
        if (level >= 4) {
            strike(activeWeapons, pixel, circle, origin, new Vector2(0, -1), width, worldHeight, isHyper);
            strike(activeWeapons, pixel, circle, origin, new Vector2(0, -1).rotateDeg(45), width, worldHeight, isHyper);
            strike(activeWeapons, pixel, circle, origin, new Vector2(0, -1).rotateDeg(-45), width, worldHeight, isHyper);
        }
    }

    private void strike(Array<Weapon> activeWeapons, Texture texture, Texture circleTexture, Vector2 origin, Vector2 dir, float width, float worldHeight, boolean isHyper) {
        ThunderboltWeapon w = ObjectPools.thunderboltWeaponPool.obtain();
        w.setLevel(this.level);
        w.init(def, texture, circleTexture, origin, dir.nor(), width, worldHeight, isHyper);
        activeWeapons.add(w);
    }

    @Override
    public float getFireRate() { return def.getFireRate(level); }

    @Override
    public void playFireSound(AudioManager audio, int level) {
        audio.playThunderboltWeaponSound(level);
    }

    /** Thunderbolt's Hyper Attack: fires the same level-scaled strike volley as a normal attack,
     *  tinted purple, on its own cooldown (independent of the normal fire-rate cooldown). Any
     *  enemy one of these strikes hits also has every bullet it has currently fired arced to and
     *  destroyed (see markDamaged()/chainDestroyBullets()). */
    @Override
    public void hyperAttack(Player player, Array<Weapon> activeWeapons, Array<Enemy> enemies, AssetManager assets, AudioManager audio) {
        if (hyperAttackCooldownTimer > 0f) return;
        hyperAttackCooldownTimer = HYPER_ATTACK_COOLDOWN;

        Vector2 origin = new Vector2(player.getCenterX(), player.getCenterY());
        spawnLevelStrikes(activeWeapons, assets.pixelTexture, assets.circleTexture, origin, strikeWidth(), player.getWorldHeight(), true);

        playFireSound(audio, level);
    }

    // Ticks down regardless of which slot is active, matching every other weapon's own cooldown
    // convention (see Player.advanceWeaponTimers).
    @Override
    public void addShootTimer(float delta) {
        super.addShootTimer(delta);
        if (hyperAttackCooldownTimer > 0f) hyperAttackCooldownTimer -= delta;
    }

    /** Called on a full game reset, not on pool reuse (see reset()) - the prototype instance is
     *  never pooled, so this cooldown would otherwise survive a restart. */
    public void resetHyperAttackCooldown() {
        hyperAttackCooldownTimer = 0f;
    }

    @Override
    public void reset() {
        super.reset();
        lifeTime = 0f;
        hitEnemies.clear();
        segCount = 0;
        hasRealHit = false;
        isHyper = false;
    }
}
