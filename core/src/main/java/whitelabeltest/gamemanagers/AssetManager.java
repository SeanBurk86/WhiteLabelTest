package whitelabeltest.gamemanagers;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.spawning.GameBalance;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
import whitelabeltest.gamemanagers.spawning.StageSequenceDefinition;

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
import whitelabeltest.enemy.EnemyAnimationDef;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.ExplosionPatternDef;
import whitelabeltest.enemy.FiringPatternDef;
import whitelabeltest.enemy.PatternRegistry;
import whitelabeltest.player.PlayerDefinition;
import whitelabeltest.player.weapons.WeaponDefinition;

/** Loads every data/*.json definition file and the textures they reference at startup, and owns
 *  those textures. */
public class AssetManager implements Disposable {
    private final ObjectMap<String, Texture> textures = new ObjectMap<>();
    private final ObjectMap<String, WeaponDefinition> weaponDefinitions = new ObjectMap<>();
    private final ObjectMap<String, EnemyDefinition> enemyDefinitions = new ObjectMap<>();
    private final PlayerDefinition playerDefinition;
    private final GameBalance gameBalance;
    // Every stage by id; which ones play, in what order, is up to stageSequences.
    private final ObjectMap<String, StageDefinition> stages = new ObjectMap<>();
    private final ObjectMap<String, StageSequenceDefinition> stageSequences = new ObjectMap<>();
    // Clips InterstitialPlayer picks from at random before a stage.
    private final Array<String> interstitialVideos;

    public final Texture playerTexture;
    public final Texture playerDeathTexture;
    public final Texture playerHaloTexture;
    public final Texture basicHaloDetachTexture;
    public final Texture[] thunderHyperHaloShrinkTextures;
    public final Texture thunderHaloBombTexture;
    public final Texture playerReflectShieldTexture;
    public final Texture bombSpriteTexture;
    public final Texture[] bulletCancelTextures;
    public final Texture bulletTexture;
    public final Texture pixelTexture;
    public final Texture circleTexture;
    // Index 0..2 = powerup tier 1..3.
    public final Texture[] powerupTierTextures;
    public final Texture pointGemTexture;

    public AssetManager() {
        Json json = new Json();

        // Load Definitions
        @SuppressWarnings("unchecked")
        Array<WeaponDefinition> wDefs = json.fromJson(Array.class, WeaponDefinition.class, Gdx.files.internal("data/weapons.json"));
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
        Array<EnemyDefinition> eDefs = json.fromJson(Array.class, EnemyDefinition.class, Gdx.files.internal("data/enemies.json"));
        for (EnemyDefinition def : eDefs) {
            enemyDefinitions.put(def.id, def);
            loadTexture(def.texture);
            if (def.bulletTexture != null) loadTexture(def.bulletTexture);
            if (def.spawnTexture != null) loadTexture(def.spawnTexture);
            if (def.deathTexture != null) loadTexture(def.deathTexture);
            if (def.animations != null) {
                for (EnemyAnimationDef anim : def.animations.values()) {
                    if (anim.texture != null) loadTexture(anim.texture);
                }
            }
            loadFiringPatternTextures(PatternRegistry.getFiring(def.firingPattern));
        }

        playerDefinition = json.fromJson(PlayerDefinition.class, Gdx.files.internal("data/player.json"));
        gameBalance = json.fromJson(GameBalance.class, Gdx.files.internal("data/balance.json"));

        @SuppressWarnings("unchecked")
        Array<StageDefinition> stageDefs = json.fromJson(Array.class, StageDefinition.class, Gdx.files.internal("data/stages.json"));
        for (StageDefinition def : stageDefs) stages.put(def.id, def);

        @SuppressWarnings("unchecked")
        Array<StageSequenceDefinition> sequenceDefs = json.fromJson(Array.class, StageSequenceDefinition.class, Gdx.files.internal("data/stage_sequences.json"));
        for (StageSequenceDefinition def : sequenceDefs) stageSequences.put(def.id, def);

        @SuppressWarnings("unchecked")
        Array<String> interstitials = json.fromJson(Array.class, String.class, Gdx.files.internal("data/interstitials.json"));
        interstitialVideos = interstitials;

        // Setup common fixed assets
        playerTexture = new Texture(playerDefinition.player.texture);
        playerDeathTexture = new Texture(playerDefinition.playerDeath.texture);
        playerHaloTexture = new Texture(playerDefinition.playerHalo.texture);
        basicHaloDetachTexture = new Texture(playerDefinition.basicHaloDetach.texture);
        thunderHyperHaloShrinkTextures = new Texture[] {
            new Texture(playerDefinition.thunderHyperHaloShrink1.texture),
            new Texture(playerDefinition.thunderHyperHaloShrink2.texture),
            new Texture(playerDefinition.thunderHyperHaloShrink3.texture),
            new Texture(playerDefinition.thunderHyperHaloShrink4.texture),
        };
        thunderHaloBombTexture = new Texture(playerDefinition.thunderHaloBomb.texture);
        playerReflectShieldTexture = new Texture(playerDefinition.reflectShield.texture);
        bombSpriteTexture = new Texture("images/effects/BombSprite.png");
        bulletCancelTextures = new Texture[] {
            new Texture("images/effects/BulletCancel01.png"),
            new Texture("images/effects/BulletCancel02.png"),
            new Texture("images/effects/BulletCancel03.png"),
            new Texture("images/effects/BulletCancel04.png"),
            new Texture("images/effects/BulletCancel05.png"),
        };

        powerupTierTextures = new Texture[] {
            new Texture("images/pickups/PowerUp1.png"),
            new Texture("images/pickups/PowerUp2.png"),
            new Texture("images/pickups/PowerUp3.png"),
        };
        pointGemTexture = new Texture("images/pickups/PointGem.png");

        // Sprite cue textures otherwise load on first use, causing a visible hitch mid-stage.
        // WarningSign.png is a very large sheet (4860x1400), so preload it here. Preload any new
        // sprite cue texture the same way (or shrink the art).
        loadTexture("images/ui/WarningSign.png");

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
        return path != null ? textures.get(path) : null;
    }

    /** Returns the texture, loading it first if it isn't cached. */
    public Texture ensureTexture(String path) {
        if (path == null) return null;
        loadTexture(path);
        return getTexture(path);
    }

    public WeaponDefinition getWeaponDefinition(String id) {
        return weaponDefinitions.get(id);
    }

    public EnemyDefinition getEnemyDefinition(String id) {
        return enemyDefinitions.get(id);
    }

    public Array<String> getEnemyIds() {
        Array<String> ids = enemyDefinitions.keys().toArray();
        ids.sort();
        return ids;
    }

    /** Registers or replaces an enemy definition in memory (used by the debug pattern editor). */
    public void putEnemyDefinition(EnemyDefinition def) {
        enemyDefinitions.put(def.id, def);
    }

    /** All enemy definitions sorted by id, for writing enemies.json. */
    public Array<EnemyDefinition> getAllEnemyDefinitionsSorted() {
        Array<EnemyDefinition> out = new Array<>();
        for (String id : getEnemyIds()) out.add(enemyDefinitions.get(id));
        return out;
    }

    public PlayerDefinition getPlayerDefinition() {
        return playerDefinition;
    }

    public GameBalance getGameBalance() {
        return gameBalance;
    }

    public StageDefinition getStageDefinition(String id) {
        return stages.get(id);
    }

    // Every stage id, sorted (for the debug menu's stage select).
    public Array<String> getStageIds() {
        Array<String> ids = new Array<>(stages.size);
        for (String id : stages.keys()) ids.add(id);
        ids.sort();
        return ids;
    }

    public StageSequenceDefinition getStageSequence(String id) {
        return stageSequences.get(id);
    }

    public Array<String> getInterstitialVideos() {
        return interstitialVideos;
    }

    @Override
    public void dispose() {
        for (Texture t : textures.values()) t.dispose();
        playerTexture.dispose();
        playerDeathTexture.dispose();
        playerHaloTexture.dispose();
        basicHaloDetachTexture.dispose();
        for (Texture t : thunderHyperHaloShrinkTextures) t.dispose();
        thunderHaloBombTexture.dispose();
        playerReflectShieldTexture.dispose();
        bombSpriteTexture.dispose();
        for (Texture t : bulletCancelTextures) t.dispose();
        pointGemTexture.dispose();
        bulletTexture.dispose();
        pixelTexture.dispose();
        circleTexture.dispose();
        for (Texture t : powerupTierTextures) t.dispose();
    }
}
