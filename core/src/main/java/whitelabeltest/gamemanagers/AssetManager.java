package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.FiringPatternDef;
import whitelabeltest.player.weapons.WeaponDefinition;

public class AssetManager implements Disposable {
    private final ObjectMap<String, Texture> textures = new ObjectMap<>();
    private final ObjectMap<String, WeaponDefinition> weaponDefinitions = new ObjectMap<>();
    private final ObjectMap<String, EnemyDefinition> enemyDefinitions = new ObjectMap<>();

    public final Texture playerTexture;
    public final Texture playerDeathTexture;
    public final Texture playerHaloTexture;
    public final Texture bombSpriteTexture;
    public final Texture bulletTexture;
    public final Texture[] explosionTextures;
    public final Texture powerup1, powerup2, powerup3, powerup4;

    public AssetManager() {
        Json json = new Json();

        // Load Definitions
        @SuppressWarnings("unchecked")
        Array<WeaponDefinition> wDefs = json.fromJson(Array.class, WeaponDefinition.class, Gdx.files.internal("weapons.json"));
        for (WeaponDefinition def : wDefs) {
            weaponDefinitions.put(def.id, def);
            loadTexture(def.texture);
        }

        @SuppressWarnings("unchecked")
        Array<EnemyDefinition> eDefs = json.fromJson(Array.class, EnemyDefinition.class, Gdx.files.internal("enemies.json"));
        for (EnemyDefinition def : eDefs) {
            enemyDefinitions.put(def.id, def);
            loadTexture(def.texture);
            if (def.bulletTexture != null) loadTexture(def.bulletTexture);
            if (def.spawnTexture != null) loadTexture(def.spawnTexture);
            if (def.deathTexture != null) loadTexture(def.deathTexture);
            loadFiringPatternTextures(def.firingPattern);
        }

        // Setup common fixed assets
        playerTexture = new Texture("PlayerSprite.png");
        playerDeathTexture = new Texture("PlayerSpriteDeath.png");
        playerHaloTexture = new Texture("PlayerSpriteHalo.png");
        bombSpriteTexture = new Texture("BombSprite.png");

        explosionTextures = new Texture[5];
        for (int i = 0; i < 5; i++) {
            explosionTextures[i] = new Texture("ExplosionParticle" + (i + 1) + ".png");
        }

        powerup1 = new Texture("powerup1.png");
        powerup2 = new Texture("powerup2.png");
        powerup3 = new Texture("powerup3.png");
        powerup4 = new Texture("powerup4.png");

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.YELLOW);
        pixmap.fill();
        bulletTexture = new Texture(pixmap);
        pixmap.dispose();
    }

    private void loadFiringPatternTextures(FiringPatternDef def) {
        if (def == null) return;
        if (def.bulletTexture != null) loadTexture(def.bulletTexture);
        if (def.patterns != null) {
            for (FiringPatternDef sub : def.patterns) loadFiringPatternTextures(sub);
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

    @Override
    public void dispose() {
        for (Texture t : textures.values()) t.dispose();
        playerTexture.dispose();
        playerDeathTexture.dispose();
        playerHaloTexture.dispose();
        bombSpriteTexture.dispose();
        bulletTexture.dispose();
        for (Texture t : explosionTextures) t.dispose();
        powerup1.dispose();
        powerup2.dispose();
        powerup3.dispose();
        powerup4.dispose();
    }
}
