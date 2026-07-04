package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.player.Player;

public class ThunderWhipWeapon extends BaseWeapon {
    private WeaponDefinition def;
    private final Array<Sprite> segments = new Array<>();
    private static final int NUM_SEGMENTS = 15;
    private final Vector2[] history = new Vector2[NUM_SEGMENTS + 1];

    public ThunderWhipWeapon() {
        super();
        for (int i = 0; i <= NUM_SEGMENTS; i++) {
            history[i] = new Vector2();
        }
    }

    private void setupBase(WeaponDefinition def, Texture texture, float x, float y) {
        this.def = def;
        int frameHeight = texture.getHeight();
        int frameWidth = texture.getWidth() / def.frameCount;

        this.animation = AnimationCache.get(texture, def.frameCount, 0.08f, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        sprite.setSize(def.size, def.size * ((float) frameHeight / frameWidth));
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setY(y);

        this.chainWindow = def.chainWindow;
        this.shootSpeedMultiplier = def.shootSpeedMultiplier;
        for (Vector2 p : history) p.set(x, y);

        // Reuse existing segment sprites across re-inits (this weapon is pool-recycled on every spawn)
        // instead of reallocating NUM_SEGMENTS Sprite objects every time.
        for (int i = 0; i < NUM_SEGMENTS; i++) {
            Sprite s;
            if (i < segments.size) {
                s = segments.get(i);
                s.setRegion(frames[0]);
            } else {
                s = new Sprite(frames[0]);
                segments.add(s);
            }
            s.setSize(sprite.getWidth(), sprite.getHeight());
            s.setOrigin(s.getWidth() / 2, 0);

            float alpha = 1.0f - ((float) i / NUM_SEGMENTS);
            s.setAlpha(alpha);
        }
    }

    public void init(WeaponDefinition def, Texture texture, float x, float y, Vector2 dir, float speed) {
        setupBase(def, texture, x, y);
        this.velocity.set(dir).scl(speed);
        this.path = null;
        this.damage = def.baseDamage + (level - 1) * def.damagePerLevel;
    }

    public void initSpline(WeaponDefinition def, Texture texture, float x, float y, Vector2 dir, float amplitude) {
        setupBase(def, texture, x, y);
        this.def = def;

        float rotationAngle = dir.angleDeg() - 90;

        Vector2[] controlPoints = {
            new Vector2(x, y), new Vector2(x, y),
            new Vector2(x + amplitude, y + 2f), new Vector2(x - amplitude, y + 4f),
            new Vector2(x + amplitude, y + 6f),
            new Vector2(x, y + 10f), new Vector2(x, y + 10f)
        };

        for(Vector2 p : controlPoints) {
            if (p != controlPoints[0] && p != controlPoints[1]) {
                p.sub(x, y).rotateDeg(rotationAngle).add(x, y);
            }
        }

        this.path = new CatmullRomSpline<>(controlPoints, false);
        this.pathDuration = Math.max(0.4f, 1.0f - (level - 1) * 0.1f);
        this.damage = def.baseDamage + (level - 1) * def.damagePerLevel;
    }

    @Override
    public void update(float delta) {
        animationTime += delta;
        // Protection against negative animation time causing crashes in getKeyFrame
        float safeTime = Math.max(0, animationTime);
        TextureRegion currentFrame = animation.getKeyFrame(safeTime);
        sprite.setRegion(currentFrame);

        if (path != null) {
            pathTime += delta;
            float t = Math.min(1f, pathTime / pathDuration);
            path.valueAt(tempPos, t);
            sprite.setCenterX(tempPos.x);
            sprite.setY(tempPos.y);
        } else {
            sprite.translate(velocity.x * delta, velocity.y * delta);
        }

        for (int i = history.length - 1; i > 0; i--) {
            history[i].set(history[i - 1]);
        }
        history[0].set(sprite.getX() + sprite.getWidth() / 2, sprite.getY());

        for (int i = 0; i < segments.size; i++) {
            Sprite s = segments.get(i);
            // Stagger animation safely
            float staggerTime = Math.max(0, animationTime - (i * 0.01f));
            s.setRegion(animation.getKeyFrame(staggerTime));

            Vector2 p1 = history[i];
            Vector2 p2 = history[i + 1];

            float dx = p1.x - p2.x;
            float dy = p1.y - p2.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            s.setPosition(p2.x - s.getWidth() / 2, p2.y);

            if (dist > 0.001f) {
                s.setSize(s.getWidth(), dist + 0.1f);
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx)) - 90;
                s.setRotation(angle);
            }
        }

        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void draw(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        for (Sprite s : segments) {
            s.draw(batch);
        }
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        float baseSpeed = def.speed;
        if(level >= 1) spawnStraight(activeWeapons, texture, x, y, new Vector2(0, 1), baseSpeed);
        if (level >= 2) {
            spawnStraight(activeWeapons, texture, x, y, new Vector2(-1, 1), baseSpeed);
            spawnStraight(activeWeapons, texture, x, y, new Vector2(1, 1), baseSpeed);
        }
        if (level >= 3) {
            spawnSpline(activeWeapons, texture, x, y,new Vector2(0, 1), 1.5f);
            spawnSpline(activeWeapons, texture, x, y,new Vector2(0, 1), -1.5f);
        }
        if (level >= 4) {
            spawnSpline(activeWeapons, texture, x, y,new Vector2( -1f, -1f), 1.5f);
            spawnSpline(activeWeapons, texture, x, y,new Vector2(-1f, -1f), -1.5f);
            spawnSpline(activeWeapons, texture, x, y,new Vector2(1f, -1f), 1.5f);
            spawnSpline(activeWeapons, texture, x, y,new Vector2(1f, -1f), -1.5f);
        }
    }

    private void spawnStraight(Array<Weapon> activeWeapons, Texture texture, float x, float y, Vector2 dir, float speed) {
        ThunderWhipWeapon w = ObjectPools.waveWeaponPool.obtain();
        w.setLevel(this.level);
        w.init(def, texture, x, y, dir.nor(), speed);
        activeWeapons.add(w);
    }

    private void spawnSpline(Array<Weapon> activeWeapons, Texture texture, float x, float y, Vector2 dir, float amplitude) {
        ThunderWhipWeapon w = ObjectPools.waveWeaponPool.obtain();
        w.setLevel(this.level);
        w.initSpline(def, texture, x, y, dir, amplitude);
        activeWeapons.add(w);
    }

    @Override
    public float getFireRate() { return def.baseFireRate - (level - 1) * def.fireRatePerLevel; }
    @Override
    public void playFireSound(AudioManager audio, int level) {
        audio.playThunderWhipWeaponSound(level);
    }

    @Override
    public void reset() {
        super.reset();
        for (Vector2 p : history) p.set(0, 0);
    }
}
