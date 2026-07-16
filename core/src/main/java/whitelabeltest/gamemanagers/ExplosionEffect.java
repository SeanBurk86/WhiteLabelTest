package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.Pool;
import whitelabeltest.enemy.ExplosionPatternDef;

/** Plays one death-explosion pattern. A "Burst" pattern is a leaf: this object owns its own
 *  particles directly. A "Combined"/"Sequence" pattern makes this a composite node whose
 *  sub-patterns are played by child ExplosionEffect instances — mirroring how Combined/Sequence
 *  firing patterns wrap sub-FiringPatterns, just collapsed into one Poolable class since an
 *  explosion has no per-frame gameplay logic to specialize per node type. */
public class ExplosionEffect implements Pool.Poolable {

    private static class Particle {
        float x, y;
        Vector2 velocity = new Vector2();
        float stateTime;
        float size;
        Animation<TextureRegion> anim;
    }

    // Particle objects are kept across pool reuse cycles and overwritten in place by init(),
    // rather than reallocated on every explosion (this effect is obtained from a pool on every enemy kill).
    private final Array<Particle> particles = new Array<>();
    private int activeParticleCount;
    private float velocityDamping = ExplosionPatternDef.DEFAULT_VELOCITY_DAMPING;

    // Composite (Combined/Sequence) state. Child ExplosionEffect objects are likewise kept
    // across pool cycles and re-initialized in place rather than reallocated.
    private boolean isComposite;
    private boolean isSequence;
    private final Array<ExplosionEffect> children = new Array<>();
    private final Array<ExplosionPatternDef> childPatterns = new Array<>();
    private final FloatArray childDurations = new FloatArray();
    private int activeChildCount;
    private int sequenceIndex;
    private float sequenceTimer;
    private float originX, originY, enemyWidth;

    /** Builds this explosion purely from its pattern definition (particle count, sprite-sheet
     *  layout, size/speed ranges, timing and spread — or, for a Combined/Sequence pattern, its
     *  sub-patterns) plus the enemy's own width and where it died — the same "pattern describes
     *  it, caller only supplies world position" split used by firing/movement patterns. A
     *  missing pattern produces no particles rather than throwing. */
    public void init(ExplosionPatternDef pattern, float originX, float originY, float enemyWidth) {
        activeParticleCount = 0;
        isComposite = false;
        activeChildCount = 0;
        sequenceIndex = 0;
        sequenceTimer = 0;
        childPatterns.clear();
        childDurations.clear();

        if (pattern == null) return;

        float ox = originX + pattern.offsetX;
        float oy = originY + pattern.offsetY;

        if ("Combined".equals(pattern.type) || "Sequence".equals(pattern.type)) {
            isComposite = true;
            isSequence = "Sequence".equals(pattern.type);
            this.originX = ox;
            this.originY = oy;
            this.enemyWidth = enemyWidth;

            Array<ExplosionPatternDef> subPatterns = pattern.patterns;
            activeChildCount = subPatterns != null ? subPatterns.size : 0;

            for (int i = 0; i < activeChildCount; i++) {
                ExplosionPatternDef sub = subPatterns.get(i);
                childPatterns.add(sub);
                childDurations.add(sub.duration > 0 ? sub.duration : ExplosionPatternDef.DEFAULT_DURATION);

                if (!isSequence || i == 0) obtainChild(i).init(sub, ox, oy, enemyWidth);
            }
            return;
        }

        if (pattern.textures == null || pattern.textures.size == 0) return;

        activeParticleCount = pattern.particleCount;
        velocityDamping = pattern.velocityDamping;
        float baseSize = enemyWidth * pattern.sizeScale;

        for (int i = 0; i < activeParticleCount; i++) {
            Particle p = obtainParticle(i);

            String texturePath = pattern.textures.get(MathUtils.random(0, pattern.textures.size - 1));
            Texture tex = EnemySpawnRegistry.getTexture(texturePath);
            p.anim = AnimationCache.get(tex, pattern.columns, pattern.rows, pattern.frameCount, pattern.frameDuration, Animation.PlayMode.NORMAL);

            p.x = ox;
            p.y = oy;
            p.stateTime = MathUtils.random(0f, pattern.startDelayMax);
            p.size = baseSize * MathUtils.random(pattern.sizeMin, pattern.sizeMax);

            float angle = MathUtils.random(pattern.angleMin, pattern.angleMax);
            float speed = MathUtils.random(pattern.speedMin, pattern.speedMax);
            p.velocity.set(speed, 0).setAngleDeg(angle);
        }
    }

    private ExplosionEffect obtainChild(int index) {
        ExplosionEffect child;
        if (index < children.size) {
            child = children.get(index);
        } else {
            child = new ExplosionEffect();
            children.add(child);
        }
        return child;
    }

    private Particle obtainParticle(int index) {
        Particle p;
        if (index < particles.size) {
            p = particles.get(index);
        } else {
            p = new Particle();
            particles.add(p);
        }
        return p;
    }

    public void update(float delta) {
        if (isComposite) {
            if (activeChildCount == 0) return;

            if (!isSequence) {
                for (int i = 0; i < activeChildCount; i++) children.get(i).update(delta);
                return;
            }

            // Sequence: only the current stage runs; once its duration elapses, cut over to the
            // next stage immediately (not overlapped or faded) — the same "switch on a boundary"
            // semantics SequencedFiringPattern uses. The final stage has no next stage to cut to,
            // so it simply keeps running until its own particles finish.
            if (sequenceIndex < activeChildCount - 1) {
                sequenceTimer += delta;
                if (sequenceTimer >= childDurations.get(sequenceIndex)) {
                    sequenceTimer = 0;
                    sequenceIndex++;
                    obtainChild(sequenceIndex).init(childPatterns.get(sequenceIndex), originX, originY, enemyWidth);
                }
            }
            children.get(sequenceIndex).update(delta);
            return;
        }

        for (int i = 0; i < activeParticleCount; i++) {
            Particle p = particles.get(i);
            p.stateTime += delta;
            p.x += p.velocity.x * delta;
            p.y += p.velocity.y * delta;
            p.velocity.scl(velocityDamping);
        }
    }

    public void draw(SpriteBatch batch) {
        if (isComposite) {
            if (activeChildCount == 0) return;

            if (isSequence) {
                children.get(sequenceIndex).draw(batch);
            } else {
                for (int i = 0; i < activeChildCount; i++) children.get(i).draw(batch);
            }
            return;
        }

        for (int i = 0; i < activeParticleCount; i++) {
            Particle p = particles.get(i);
            TextureRegion currentFrame = p.anim.getKeyFrame(p.stateTime);
            batch.draw(currentFrame, p.x - p.size/2, p.y - p.size/2, p.size, p.size);
        }
    }

    public boolean isFinished() {
        if (isComposite) {
            if (activeChildCount == 0) return true;

            if (isSequence) {
                return sequenceIndex == activeChildCount - 1 && children.get(sequenceIndex).isFinished();
            }
            for (int i = 0; i < activeChildCount; i++) {
                if (!children.get(i).isFinished()) return false;
            }
            return true;
        }

        for (int i = 0; i < activeParticleCount; i++) {
            Particle p = particles.get(i);
            if (!p.anim.isAnimationFinished(p.stateTime)) return false;
        }
        return true;
    }

    @Override
    public void reset() {
        // Particle/child objects are intentionally kept (not cleared) so init() can reuse them
        // on the next obtain() instead of reallocating; init() overwrites every field.
    }
}
