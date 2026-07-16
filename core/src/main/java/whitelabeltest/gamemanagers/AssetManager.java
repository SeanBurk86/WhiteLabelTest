package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import whitelabeltest.enemy.BulletDef;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.ExplosionPatternDef;
import whitelabeltest.enemy.FiringPatternDef;
import whitelabeltest.enemy.PatternRegistry;
import whitelabeltest.player.PlayerDefinition;
import whitelabeltest.player.weapons.WeaponDefinition;

public class AssetManager implements Disposable {
    private final ObjectMap<String, Texture> textures = new ObjectMap<>();
    private final ObjectMap<String, WeaponDefinition> weaponDefinitions = new ObjectMap<>();
    private final ObjectMap<String, EnemyDefinition> enemyDefinitions = new ObjectMap<>();
    private final PlayerDefinition playerDefinition;

    public final Texture playerTexture;
    public final Texture playerDeathTexture;
    public final Texture playerHaloTexture;
    public final Texture playerReflectShieldTexture;
    public final Texture bombSpriteTexture;
    public final Texture bulletTexture;
    public final Texture pixelTexture;
    public final Texture circleTexture;
    public final Texture powerup1, powerup2, powerup3, powerup4;

    public AssetManager() {
        Json json = new Json();

        // Load Definitions
        @SuppressWarnings("unchecked")
        Array<WeaponDefinition> wDefs = json.fromJson(Array.class, WeaponDefinition.class, Gdx.files.internal("weapons.json"));
        for (WeaponDefinition def : wDefs) {
            weaponDefinitions.put(def.id, def);
            loadTexture(def.texture);
            if (def.hitTexture != null) {
                loadTexture(def.hitTexture);
                Texture hitTex = textures.get(def.hitTexture);
                int hitColumns = def.hitColumns > 0 ? def.hitColumns : def.hitFrameCount;
                def.hitAnimation = AnimationCache.get(hitTex, hitColumns, def.hitRows, def.hitFrameCount, def.hitFrameDuration, Animation.PlayMode.NORMAL);
            }
        }

        PatternRegistry.load(json);
        for (BulletDef bulletDef : PatternRegistry.getBulletDefs()) {
            if (bulletDef.bulletTexture != null) loadTexture(bulletDef.bulletTexture);
        }
        for (ExplosionPatternDef explosionDef : PatternRegistry.getExplosionDefs()) {
            loadExplosionPatternTextures(explosionDef);
        }

        @SuppressWarnings("unchecked")
        Array<EnemyDefinition> eDefs = json.fromJson(Array.class, EnemyDefinition.class, Gdx.files.internal("enemies.json"));
        for (EnemyDefinition def : eDefs) {
            enemyDefinitions.put(def.id, def);
            loadTexture(def.texture);
            if (def.bulletTexture != null) loadTexture(def.bulletTexture);
            if (def.spawnTexture != null) loadTexture(def.spawnTexture);
            if (def.deathTexture != null) loadTexture(def.deathTexture);
            loadFiringPatternTextures(PatternRegistry.getFiring(def.firingPattern));
        }

        playerDefinition = json.fromJson(PlayerDefinition.class, Gdx.files.internal("player.json"));

        // Setup common fixed assets
        playerTexture = new Texture(playerDefinition.player.texture);
        playerDeathTexture = new Texture(playerDefinition.playerDeath.texture);
        playerHaloTexture = new Texture(playerDefinition.playerHalo.texture);
        playerReflectShieldTexture = new Texture(playerDefinition.reflectShield.texture);
        bombSpriteTexture = new Texture("BombSprite.png");

        powerup1 = new Texture("RainPowerUp.png");
        powerup2 = new Texture("ForcePowerUp.png");
        powerup3 = new Texture("LightningPowerUp.png");
        powerup4 = new Texture("MoonPowerUp.png");

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.YELLOW);
        pixmap.fill();
        bulletTexture = new Texture(pixmap);
        pixmap.dispose();

        Pixmap whitePixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        whitePixmap.setColor(Color.WHITE);
        whitePixmap.fill();
        pixelTexture = new Texture(whitePixmap);
        whitePixmap.dispose();

        int circleSize = 64;
        Pixmap circlePixmap = new Pixmap(circleSize, circleSize, Pixmap.Format.RGBA8888);
        circlePixmap.setColor(Color.WHITE);
        circlePixmap.fillCircle(circleSize / 2, circleSize / 2, circleSize / 2);
        circleTexture = new Texture(circlePixmap);
        circleTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        circlePixmap.dispose();
    }

    private void loadFiringPatternTextures(FiringPatternDef def) {
        if (def == null) return;
        if (def.bulletTexture != null) loadTexture(def.bulletTexture);
        if (def.patterns != null) {
            for (FiringPatternDef sub : def.patterns) loadFiringPatternTextures(sub);
        }
    }

    private void loadExplosionPatternTextures(ExplosionPatternDef def) {
        if (def == null) return;
        if (def.textures != null) {
            for (String path : def.textures) loadTexture(path);
        }
        if (def.patterns != null) {
            for (ExplosionPatternDef sub : def.patterns) loadExplosionPatternTextures(sub);
        }
    }

    private void loadTexture(String path) {
        if (path != null && !textures.containsKey(path)) {
            textures.put(path, new Texture(path));
        }
    }

    public Texture getTexture(String path) {
        return textures.get(path);
    }

    public WeaponDefinition getWeaponDefinition(String id) {
        return weaponDefinitions.get(id);
    }

    public EnemyDefinition getEnemyDefinition(String id) {
        return enemyDefinitions.get(id);
    }

    public PlayerDefinition getPlayerDefinition() {
        return playerDefinition;
    }

    @Override
    public void dispose() {
        for (Texture t : textures.values()) t.dispose();
        playerTexture.dispose();
        playerDeathTexture.dispose();
        playerHaloTexture.dispose();
        playerReflectShieldTexture.dispose();
        bombSpriteTexture.dispose();
        bulletTexture.dispose();
        pixelTexture.dispose();
        circleTexture.dispose();
        powerup1.dispose();
        powerup2.dispose();
        powerup3.dispose();
        powerup4.dispose();
    }
}
