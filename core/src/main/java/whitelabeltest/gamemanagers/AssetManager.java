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
    private final GameBalance gameBalance;
    // Pool of every stage that exists, keyed by id - which of these play, and in what order, for a
    // given run is a separate concern (see stageSequences/StageSequenceDefinition), so a stage can
    // be reused across multiple sequences (e.g. a tutorial mode reusing a campaign stage).
    private final ObjectMap<String, StageDefinition> stages = new ObjectMap<>();
    private final ObjectMap<String, StageSequenceDefinition> stageSequences = new ObjectMap<>();
    // Pool of clips InterstitialPlayer picks randomly from before a stage starts - see
    // GameController.startInterstitial(). Not per-stage (unlike bossVideo), so it's just a flat
    // list rather than a field on StageDefinition.
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
    // Index 0 = tier 1 (PowerUp1.png) ... index 2 = tier 3 (PowerUp3.png) - see WeaponPowerup's
    // amount/GameController.powerupTextureForTier().
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

    /** Loads the texture at this asset path if it isn't already cached, then returns it — used by
     *  the debug enemy/pattern editor when previewing an enemy whose texture wasn't referenced by
     *  any enemy loaded at startup (e.g. a brand-new enemy definition, or one whose texture field
     *  was just changed). */
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

    /** Registers (or overwrites) an enemy definition under an id — used by the debug enemy/pattern
     *  editor to install a live-edited or brand-new working copy without touching the JSON-loaded
     *  set, the same way PatternRegistry.putMovement/putFiring work for patterns. */
    public void putEnemyDefinition(EnemyDefinition def) {
        enemyDefinitions.put(def.id, def);
    }

    /** All enemy definitions currently registered, sorted by id — used when writing enemies.json
     *  back to disk. */
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
