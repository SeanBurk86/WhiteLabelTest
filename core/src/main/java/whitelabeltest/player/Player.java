package whitelabeltest.player;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.InputManager;
import whitelabeltest.player.weapons.*;

public class Player {
    private final Sprite sprite;
    private final Rectangle hitbox;
    private final float movementSpeed = 7.5f;
    private final float worldWidth;
    private final float worldHeight;
    private final Vector2 direction = new Vector2();

    private final Vector2 bulletSpawnOffset = new Vector2(0.25f, 0f);

    private final BasicWeapon basicWeapon;
    private final WaveBlastWeapon waveBlastWeapon;
    private final ThunderWhipWeapon thunderWhipWeapon;
    private final OrbitWeapon orbitWeapon;

    private Weapon currentWeapon;
    private float shootTimer;

    private final Animation<TextureRegion> animation;
    private float animationTime = 0;

    private static final int MAX_WEAPON_LEVEL = 4;

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

        hitbox = new Rectangle();
        updateHitbox();

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
    }

    public void update(float delta, InputManager input, AssetManager assets, AudioManager audio, Array<Weapon> bullets, Array<Enemy> enemies) {
        animationTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        handleMovement(delta, input.getMoveDirection());
        handleShooting(delta, input.isShooting(), assets, audio, bullets, enemies);
        updateHitbox();
    }

    private void handleMovement(float delta, Vector2 moveDirection) {
        if (moveDirection.x != 0 || moveDirection.y != 0) {
            sprite.translateX(moveDirection.x * movementSpeed * delta);
            sprite.translateY(moveDirection.y * movementSpeed * delta);
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
            currentWeapon.playFireSound(audio);
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
        hitbox.set(sprite.getX() + (sprite.getWidth() * 0.2f),
                   sprite.getY() + (sprite.getHeight() * 0.2f),
                   sprite.getWidth() * 0.6f,
                   sprite.getHeight() * 0.6f);
    }

    public void draw(SpriteBatch batch) { sprite.draw(batch); }

    public void reset() {
        sprite.setPosition(worldWidth / 2f - sprite.getWidth() / 2f, 0);
        updateHitbox();
        basicWeapon.setLevel(1);
        waveBlastWeapon.setLevel(0);
        thunderWhipWeapon.setLevel(0);
        orbitWeapon.setLevel(0);
        currentWeapon = basicWeapon;
        shootTimer = 0;
        animationTime = 0;
    }

    public Rectangle getHitbox() { return hitbox; }
    public float getCenterX() { return sprite.getX() + sprite.getWidth() / 2; }
    public float getCenterY() { return sprite.getY() + sprite.getHeight() / 2; }
    public Vector2 getBulletSpawnPoint() { return new Vector2(sprite.getX() + bulletSpawnOffset.x, sprite.getY() + bulletSpawnOffset.y); }
    public Weapon getWeaponPrototype() { return currentWeapon; }

    public void levelUpWeapon(String weaponId) {
        Weapon target = null;
        if (weaponId.equals("BasicWeapon")) target = basicWeapon;
        else if (weaponId.equals("WaveBlastWeapon")) target = waveBlastWeapon;
        else if (weaponId.equals("ThunderWhipWeapon")) target = thunderWhipWeapon;
        else if (weaponId.equals("OrbitWeapon")) target = orbitWeapon;

        if (target != null) {
            target.setLevel(Math.min(target.getLevel() + 1, MAX_WEAPON_LEVEL));
            currentWeapon = target;
        }
    }

    public int getWeaponLevel(String weaponId) {
        if (weaponId.equals("BasicWeapon")) return basicWeapon.getLevel();
        if (weaponId.equals("WaveBlastWeapon")) return waveBlastWeapon.getLevel();
        if (weaponId.equals("ThunderWhipWeapon")) return thunderWhipWeapon.getLevel();
        if (weaponId.equals("OrbitWeapon")) return orbitWeapon.getLevel();
        return 0;
    }
}
