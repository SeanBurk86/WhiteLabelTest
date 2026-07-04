package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

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
    private final int particleCount = 8;

    public void init(Texture[] textures, float originX, float originY, float baseSize) {
        for (int i = 0; i < particleCount; i++) {
            Particle p;
            if (i < particles.size) {
                p = particles.get(i);
            } else {
                p = new Particle();
                particles.add(p);
            }

            // Randomly select one of the explosion textures (11 frames each)
            Texture tex = textures[MathUtils.random(0, textures.length - 1)];
            p.anim = AnimationCache.get(tex, 11, 0.05f, Animation.PlayMode.NORMAL);

            p.x = originX;
            p.y = originY;
            p.stateTime = MathUtils.random(0f, 0.1f);
            p.size = baseSize * MathUtils.random(0.5f, 1.2f);

            float angle = MathUtils.random(0f, 360f);
            float speed = MathUtils.random(2f, 5f);
            p.velocity.set(speed, 0).setAngleDeg(angle);
        }
    }

    public void update(float delta) {
        for (Particle p : particles) {
            p.stateTime += delta;
            p.x += p.velocity.x * delta;
            p.y += p.velocity.y * delta;
            p.velocity.scl(0.95f);
        }
    }

    public void draw(SpriteBatch batch) {
        for (Particle p : particles) {
            TextureRegion currentFrame = p.anim.getKeyFrame(p.stateTime);
            batch.draw(currentFrame, p.x - p.size/2, p.y - p.size/2, p.size, p.size);
        }
    }

    public boolean isFinished() {
        for (Particle p : particles) {
            if (!p.anim.isAnimationFinished(p.stateTime)) return false;
        }
        return true;
    }

    @Override
    public void reset() {
        // Particle objects are intentionally kept (not cleared) so init() can reuse them
        // on the next obtain() instead of reallocating; init() overwrites every field.
    }
}
