package whitelabeltest.player;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.InputManager;
import whitelabeltest.player.weapons.*;

public class Player {
    private final Sprite sprite;
    private final Circle hitbox;
    private final Circle grazeHitbox;
    private final float movementSpeed = 7.5f;
    private final float worldWidth;
    private final float worldHeight;

    private final Vector2 bulletSpawnOffset = new Vector2(0.25f, 0f);

    private final BasicWeapon basicWeapon;
    private final WaveBlastWeapon waveBlastWeapon;
    private final ThunderWhipWeapon thunderWhipWeapon;
    private final OrbitWeapon orbitWeapon;

    private Weapon currentWeapon;
    private float shootTimer;
    private int numBombs;
    private int numLives;
    private float grazePoints;

    private final Animation<TextureRegion> animation;
    private float animationTime = 0;

    private final Animation<TextureRegion> deathAnimation;
    private final float deathDrawWidth, deathDrawHeight;

    private final Animation<TextureRegion> haloAnimation;
    private final float haloDrawWidth, haloDrawHeight;
    private float haloAnimationTime = 0;

    private float invincibleFrameTime = 2f;
    private float invincibilityTimer = 0f;
    private boolean isInvincible;

    private boolean isDead;
    private float deathTimer;
    private float deathX, deathY;
    private static final float DEATH_WAIT = 2f;

    private float grazeFlashTimer;
    private static final float GRAZE_FLASH_DURATION = 0.12f;

    private static final int MAX_WEAPON_LEVEL = 4;
    private static final float BLINK_INTERVAL = 0.1f;

    public Player(AssetManager assets, float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

        Texture texture = assets.playerTexture;
        int frameWidth = texture.getWidth() / 24;
        int frameHeight = texture.getHeight();
        TextureRegion[][] tmp = TextureRegion.split(texture, frameWidth, frameHeight);
        TextureRegion[] frames = new TextureRegion[24];
        System.arraycopy(tmp[0], 0, frames, 0, 24);
        animation = new Animation<>(0.04f, frames);
        animation.setPlayMode(Animation.PlayMode.LOOP);

        sprite = new Sprite(frames[0]);
        sprite.setSize(0.5f, 0.5f * ((float) frameHeight / frameWidth));
        sprite.setX(worldWidth / 2f - sprite.getWidth() / 2f);
        sprite.setY(0);

        Texture deathTexture = assets.playerDeathTexture;
        int deathFrameWidth = deathTexture.getWidth() / 30;
        int deathFrameHeight = deathTexture.getHeight();
        TextureRegion[][] deathTmp = TextureRegion.split(deathTexture, deathFrameWidth, deathFrameHeight);
        TextureRegion[] deathFrames = new TextureRegion[30];
        System.arraycopy(deathTmp[0], 0, deathFrames, 0, 30);
        deathAnimation = new Animation<>(1f / 30f, deathFrames);
        deathAnimation.setPlayMode(Animation.PlayMode.NORMAL);
        deathDrawWidth = 3f;
        deathDrawHeight = 3f;

        Texture haloTexture = assets.playerHaloTexture;
        int haloFrameWidth = haloTexture.getWidth() / 94;
        int haloFrameHeight = haloTexture.getHeight();
        TextureRegion[][] haloTmp = TextureRegion.split(haloTexture, haloFrameWidth, haloFrameHeight);
        TextureRegion[] haloFrames = new TextureRegion[94];
        System.arraycopy(haloTmp[0], 0, haloFrames, 0, 94);
        haloAnimation = new Animation<>(1f / 24f, haloFrames);
        haloAnimation.setPlayMode(Animation.PlayMode.LOOP);
        haloDrawWidth = 2.225f;
        haloDrawHeight = 2.225f * ((float) haloFrameHeight / haloFrameWidth);

        hitbox = new Circle();
        updateHitbox();
        grazeHitbox = new Circle();
        updateGrazeHitbox();

        // Initialize weapons using the new dynamic AssetManager
        WeaponDefinition bDef = assets.getWeaponDefinition("BasicWeapon");
        basicWeapon = new BasicWeapon();
        basicWeapon.init(bDef, assets.getTexture(bDef.texture), 0, 0, new Vector2(0,1), bDef.speed);

        WeaponDefinition wDef = assets.getWeaponDefinition("WaveBlastWeapon");
        waveBlastWeapon = new WaveBlastWeapon();
        waveBlastWeapon.init(wDef, assets.getTexture(wDef.texture), 0, 0, new Vector2(0,1), wDef.speed);

        WeaponDefinition tDef = assets.getWeaponDefinition("ThunderWhipWeapon");
        thunderWhipWeapon = new ThunderWhipWeapon();
        thunderWhipWeapon.init(tDef, assets.getTexture(tDef.texture), 0, 0, new Vector2(0,1), tDef.speed);

        WeaponDefinition oDef = assets.getWeaponDefinition("OrbitWeapon");
        orbitWeapon = new OrbitWeapon();
        orbitWeapon.init(oDef, assets.getTexture(oDef.texture), this, 0f);

        currentWeapon = basicWeapon;
        numBombs = 1;
        numLives = 3;
        grazePoints = 0;
        isInvincible = false;
    }

    public void update(float delta, InputManager input, AssetManager assets, AudioManager audio, Array<Weapon> bullets, Array<Enemy> enemies) {
        if (isDead) {
            deathTimer += delta;
            if (deathTimer >= DEATH_WAIT) {
                isDead = false;
                deathTimer = 0f;
                sprite.setPosition(deathX, deathY);
                updateHitbox();
                updateGrazeHitbox();
                startIFrames();
            }
            return;
        }

        animationTime += delta;
        haloAnimationTime += delta;
        if (grazeFlashTimer > 0) grazeFlashTimer -= delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        handleMovement(delta, input.getMoveDirection(), input.isShooting());
        handleShooting(delta, input.isShooting(), assets, audio, bullets, enemies);
        updateHitbox();
        updateGrazeHitbox();
        resolveGrazePoints();
        resolveInvincibility(delta);
    }

    private void handleMovement(float delta, Vector2 moveDirection, boolean isShooting) {
        float speed = isShooting ? movementSpeed * currentWeapon.getShootSpeedMultiplier() : movementSpeed;
        if (moveDirection.x != 0 || moveDirection.y != 0) {
            sprite.translateX(moveDirection.x * speed * delta);
            sprite.translateY(moveDirection.y * speed * delta);
        }
        sprite.setX(MathUtils.clamp(sprite.getX(), 0, worldWidth - sprite.getWidth()));
        sprite.setY(MathUtils.clamp(sprite.getY(), 0, worldHeight - sprite.getHeight()));
    }

    private void handleShooting(float delta, boolean isShooting, AssetManager assets, AudioManager audio, Array<Weapon> bullets, Array<Enemy> enemies) {
        shootTimer += delta;
        if (isShooting && shootTimer > currentWeapon.getFireRate()) {
            shootTimer = 0;

            Texture bulletTex = resolveActiveTexture(assets);
            Vector2 spawnPoint = getBulletSpawnPoint();
            currentWeapon.spawn(bullets, bulletTex, spawnPoint.x, spawnPoint.y, this, enemies, assets);
            currentWeapon.playFireSound(audio, currentWeapon.getLevel());
        }
    }

    private Texture resolveActiveTexture(AssetManager assets) {
        String texPath = null;
        if (currentWeapon == basicWeapon) texPath = assets.getWeaponDefinition("BasicWeapon").texture;
        else if (currentWeapon == waveBlastWeapon) texPath = assets.getWeaponDefinition("WaveBlastWeapon").texture;
        else if (currentWeapon == thunderWhipWeapon) texPath = assets.getWeaponDefinition("ThunderWhipWeapon").texture;
        else if (currentWeapon == orbitWeapon) texPath = assets.getWeaponDefinition("OrbitWeapon").texture;

        return (texPath != null) ? assets.getTexture(texPath) : assets.bulletTexture;
    }

    private void updateHitbox() {
        hitbox.set(getCenterX(), getCenterY(), Math.min(sprite.getWidth(), sprite.getHeight()) * 0.15f);
    }

    private void updateGrazeHitbox() {
        grazeHitbox.set(getCenterX(), getCenterY(), Math.min(sprite.getWidth(), sprite.getHeight()) * 1.3f);
    }

    private void resolveGrazePoints() {
        if (grazePoints > 100) {
            grazePoints %= 100;
            numBombs++;
        }
    }

    private void resolveInvincibility(float delta) {
        if(isInvincible) {
            invincibilityTimer += delta;
            if (invincibilityTimer > invincibleFrameTime) {
                isInvincible = false;
                invincibilityTimer = 0f;
            }
        }
    }

    public void draw(SpriteBatch batch) {
        if (isDead) {
            if (!deathAnimation.isAnimationFinished(deathTimer)) {
                TextureRegion frame = deathAnimation.getKeyFrame(deathTimer);
                batch.draw(frame, sprite.getX() - (deathDrawWidth/2.5f), sprite.getY()  - (deathDrawHeight/2.5f), deathDrawWidth, deathDrawHeight);
            }
            return;
        }

        // Halo drawn under the player sprite, centered on the player
        TextureRegion haloFrame = haloAnimation.getKeyFrame(haloAnimationTime);
        float haloX = sprite.getX() + sprite.getWidth() / 2f - haloDrawWidth / 2f;
        float haloY = sprite.getY() + sprite.getHeight() / 2f - haloDrawHeight / 2f;
        boolean grazeFlashing = grazeFlashTimer > 0;

        if (isInvincible) {
            boolean visible = ((int) (invincibilityTimer / BLINK_INTERVAL) % 2) == 0;
            float alpha = visible ? 1f : 0f;
            batch.setColor(grazeFlashing ? 0.3f : 1f, grazeFlashing ? 0.6f : 1f, 1f, alpha);
            batch.draw(haloFrame, haloX, haloY, haloDrawWidth, haloDrawHeight);
            batch.setColor(1f, 1f, 1f, 1f);
            sprite.setAlpha(visible ? 1f : 0f);
            sprite.draw(batch);
            sprite.setAlpha(1f);
        } else {
            batch.setColor(grazeFlashing ? 0.3f : 1f, grazeFlashing ? 0.6f : 1f, 1f, 1f);
            batch.draw(haloFrame, haloX, haloY, haloDrawWidth, haloDrawHeight);
            batch.setColor(1f, 1f, 1f, 1f);
            sprite.draw(batch);
        }
    }

    public void reset() {
        sprite.setPosition(worldWidth / 2f - sprite.getWidth() / 2f, 0);
        updateHitbox();
        updateGrazeHitbox();
        basicWeapon.setLevel(1);
        waveBlastWeapon.setLevel(0);
        thunderWhipWeapon.setLevel(0);
        orbitWeapon.setLevel(0);
        currentWeapon = basicWeapon;
        shootTimer = 0;
        animationTime = 0;
        numBombs = 1;
        numLives = 3;
        isInvincible = false;
        isDead = false;
        deathTimer = 0f;
    }

    public Circle getHitbox() { return hitbox; }
    public Circle getGrazeHitbox() { return grazeHitbox; }
    public float getGrazePoints() { return grazePoints; }
    public void setGrazePoints(float grazePoints) { this.grazePoints = grazePoints;}

    public float getCenterX() { return sprite.getX() + sprite.getWidth() / 2; }
    public float getCenterY() { return sprite.getY() + sprite.getHeight() / 2; }
    public float getX() { return sprite.getX(); }
    public float getY() { return sprite.getY(); }
    public float getWidth() { return sprite.getWidth(); }
    public float getHeight() { return sprite.getHeight(); }
    public TextureRegion getCurrentFrame() { return sprite; }
    public Vector2 getBulletSpawnPoint() { return new Vector2(sprite.getX() + bulletSpawnOffset.x, sprite.getY() + bulletSpawnOffset.y); }
    public Weapon getWeaponPrototype() { return currentWeapon; }

    public void levelUpWeapon(String weaponId) {
        Weapon target = switch (weaponId) {
            case "BasicWeapon" -> basicWeapon;
            case "WaveBlastWeapon" -> waveBlastWeapon;
            case "ThunderWhipWeapon" -> thunderWhipWeapon;
            case "OrbitWeapon" -> orbitWeapon;
            default -> null;
        };

        if (target != null) {
            target.setLevel(Math.min(target.getLevel() + 1, MAX_WEAPON_LEVEL));
            currentWeapon = target;
        }
    }

    public int getWeaponLevel(String weaponId) {
        return switch (weaponId) {
            case "BasicWeapon" -> basicWeapon.getLevel();
            case "WaveBlastWeapon" -> waveBlastWeapon.getLevel();
            case "ThunderWhipWeapon" -> thunderWhipWeapon.getLevel();
            case "OrbitWeapon" -> orbitWeapon.getLevel();
            default -> 0;
        };
    }

    public void triggerGrazeFlash() {
        grazeFlashTimer = GRAZE_FLASH_DURATION;
    }

    public void disableGrazeHitbox() {
        grazeHitbox.radius = 0f;
    }

    public void startDeath() {
        deathX = sprite.getX();
        deathY = sprite.getY();
        isDead = true;
        deathTimer = 0f;
        isInvincible = false;
        invincibilityTimer = 0f;
        disableGrazeHitbox();
    }

    public boolean isDead() { return isDead; }

    public void startIFrames() {
        isInvincible = true;
    }

    public int getNumBombs() { return numBombs;}

    public void setNumBombs(int numBombs) {
        this.numBombs = numBombs;
    }

    public int getNumLives() { return numLives; }

    public void setNumLives(int numLives) { this.numLives = numLives; }

    public boolean isInvincible() {
        return isInvincible;
    }

    public void setInvincible(boolean invincible) {
        isInvincible = invincible;
    }
}
