package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Path;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;

public abstract class BaseWeapon implements Weapon {
    protected Sprite sprite;
    protected Rectangle rectangle;
    protected Vector2 velocity = new Vector2();
    protected int damage;
    protected int level = 1;
    protected float chainWindow = 2.0f;
    protected float shootSpeedMultiplier = 0.75f;
    protected float shootTimer;

    protected Path<Vector2> path;
    protected float pathTime = 0;
    protected float pathDuration;
    protected Vector2 tempPos = new Vector2();

    protected Animation<TextureRegion> animation;
    protected float animationTime = 0;

    public BaseWeapon() {
        this.rectangle = new Rectangle();
    }

    @Override
    public void update(float delta) {
        if (animation != null) {
            animationTime += delta;
            sprite.setRegion(animation.getKeyFrame(animationTime));
        }

        if (path != null) {
            pathTime += delta;
            float t = pathTime / pathDuration;
            if (t > 1f) t = 1f;
            path.valueAt(tempPos, t);
            sprite.setCenterX(tempPos.x);
            sprite.setY(tempPos.y);
        } else {
            sprite.translate(velocity.x * delta, velocity.y * delta);
        }
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void updateWithEnemies(float delta, Array<Enemy> enemies) {
        update(delta);
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null) sprite.draw(batch);
    }

    @Override
    public boolean isOffScreen(float worldHeight) {
        if (sprite == null) return true;
        if (path != null) return pathTime >= pathDuration;
        return sprite.getY() > worldHeight || sprite.getY() < -2f || sprite.getX() + sprite.getWidth() < 0f || sprite.getX() > 9f;
    }

    @Override
    public Rectangle getRectangle() { return rectangle; }
    @Override
    public int getDamage() { return damage; }
    @Override
    public void setPath(Path<Vector2> path, float duration) {
        this.path = path;
        this.pathDuration = duration;
        this.pathTime = 0;
    }

    @Override
    public void setLevel(int level) { this.level = level; }
    @Override
    public int getLevel() { return level; }
    @Override
    public float getChainWindow() { return chainWindow; }
    @Override
    public float getShootSpeedMultiplier() { return shootSpeedMultiplier; }

    @Override
    public float getShootTimer() { return shootTimer; }
    @Override
    public void addShootTimer(float delta) { shootTimer += delta; }
    @Override
    public void resetShootTimer() { shootTimer = 0f; }

    @Override
    public void reset() {
        path = null;
        pathTime = 0;
        animationTime = 0;
        velocity.setZero();
        if (sprite != null) {
            sprite.setRotation(0);
            sprite.setColor(1, 1, 1, 1);
        }
    }
}
