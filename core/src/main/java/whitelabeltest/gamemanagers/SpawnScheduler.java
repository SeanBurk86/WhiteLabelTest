package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SerializationException;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.GenericEnemy;

import java.util.Comparator;

public class SpawnScheduler {
    public static class SpawnEvent {
        public float time;
        public String type;
        public float x = Float.NaN;
        public float y = Float.NaN;
        // Guaranteed weapon-powerup tier (1-3) this spawn drops on death - see
        // Enemy.setGuaranteedPowerup()/GameController.spawnPowerup(). Null means no guarantee.
        public Integer powerup;
        public boolean inverseMovement = false;
        public boolean spawned = false;

        // This spawn's slot in a squad formation - see PatternFactory.createMovement's javadoc.
        // NaN (the default) means "not a formation member", so a Squadron-type movement pattern
        // falls back to whatever offsetX/offsetY it has baked in.
        public float offsetX = Float.NaN;
        public float offsetY = Float.NaN;

        // Overrides the enemy definition's own movementPattern when set - lets several spawn
        // events share one enemy definition while steering each toward a different movement
        // pattern (e.g. two waves of the same squad with different rally points/exits).
        public String movementPattern;

        public SpawnEvent() {}
    }

    // A scripted one-off sound effect - lets a level trigger SFX (alarms, environmental stingers,
    // dialogue blips, etc.) purely from spawn_schedule.json, the same way SpawnEvent triggers
    // enemies. "sound" is an asset path (e.g. "alarm.mp3"), lazily loaded and cached the first
    // time it's played - see AudioManager.playCueSound().
    public static class SoundCue {
        public float time;
        public String sound;
        public boolean triggered = false;

        public SoundCue() {}
    }

    // A scripted one-off sprite/animation played at a fixed world position - see
    // ScheduledSpriteEffect. frameCount/columns/rows/frameDuration describe the sprite sheet the
    // same way EnemyDefinition's animations do; frameCount == 1 (the default) plays a single
    // static image for frameDuration seconds instead of animating.
    public static class SpriteCue {
        public float time;
        public String texture;
        public float x;
        public float y;
        public float size = 1f;
        public int columns = 1;
        public int rows = 1;
        public int frameCount = 1;
        public float frameDuration = 1f;
        public boolean triggered = false;

        public SpriteCue() {}
    }

    private static class ScheduleFile {
        public Array<TextCue> textCues;
        public Array<SpawnEvent> events;
        public Array<SoundCue> soundCues;
        public Array<SpriteCue> spriteCues;
        // Optional cue time (seconds) for handing the scrolling background off to the boss video -
        // see ScrollingBackground.triggerBossVideo(). Null means no schedule-driven trigger.
        public Float backgroundVideoTime;
        // Optional cue time (seconds) for fading out the stage music - see
        // AudioManager.fadeOutStageMusic(). Null means no schedule-driven trigger. Independent of
        // backgroundVideoTime so the two can be timed apart (e.g. music fading ahead of/behind the
        // video hand-off).
        public Float musicFadeOutTime;

        public ScheduleFile() {}
    }

    private float totalTime;
    private final float worldWidth;
    private final float worldHeight;
    private Array<SpawnEvent> schedule;
    private Array<TextCue> textCues = new Array<>();
    private Array<SoundCue> soundCues = new Array<>();
    private Array<SpriteCue> spriteCues = new Array<>();
    private Float backgroundVideoTime;
    private boolean backgroundVideoTriggered;
    private Float musicFadeOutTime;
    private boolean musicFadeOutTriggered;
    private final ObjectMap<String, EnemyDefinition> enemyDefinitions;
    private final AssetManager assets;

    public SpawnScheduler(float worldWidth, float worldHeight, AssetManager assets) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.assets = assets;
        this.totalTime = 0;
        this.enemyDefinitions = new ObjectMap<>();
        loadDefinitions();
        loadSchedule();
    }

    private void loadDefinitions() {
        Json json = new Json();
        @SuppressWarnings("unchecked")
        Array<EnemyDefinition> defs = json.fromJson(Array.class, EnemyDefinition.class, Gdx.files.internal("enemies.json"));
        for (EnemyDefinition def : defs) {
            enemyDefinitions.put(def.id, def);
        }
    }

    private void loadSchedule() {
        Json json = new Json();
        try {
            ScheduleFile file = json.fromJson(ScheduleFile.class, Gdx.files.internal("spawn_schedule.json"));
            this.schedule = (file != null && file.events != null) ? file.events : new Array<>();
            if (file != null && file.textCues != null) this.textCues = file.textCues;
            if (file != null && file.soundCues != null) this.soundCues = file.soundCues;
            if (file != null && file.spriteCues != null) this.spriteCues = file.spriteCues;
            if (file != null) this.backgroundVideoTime = file.backgroundVideoTime;
            if (file != null) this.musicFadeOutTime = file.musicFadeOutTime;
            schedule.sort(new Comparator<SpawnEvent>() {
                @Override
                public int compare(SpawnEvent e1, SpawnEvent e2) {
                    return Float.compare(e1.time, e2.time);
                }
            });
            soundCues.sort(new Comparator<SoundCue>() {
                @Override
                public int compare(SoundCue c1, SoundCue c2) {
                    return Float.compare(c1.time, c2.time);
                }
            });
            spriteCues.sort(new Comparator<SpriteCue>() {
                @Override
                public int compare(SpriteCue c1, SpriteCue c2) {
                    return Float.compare(c1.time, c2.time);
                }
            });
        } catch (SerializationException e) {
            Gdx.app.error("SpawnScheduler", "Error parsing spawn_schedule.json", e);
            this.schedule = new Array<>();
        }
    }

    public Array<TextCue> getTextCues() { return textCues; }

    public float getTotalTime() { return totalTime; }

    public Array<SpawnEvent> getSchedule() { return schedule; }

    /** Scheduled spawn time of the stage's boss (the first SpawnEvent whose EnemyDefinition sets
     *  isBoss), or -1 if the schedule has no boss - see GameController's boss-takedown time bonus,
     *  which measures the fight against this rather than the whole stage's elapsed time. */
    public float getBossSpawnTime() {
        for (SpawnEvent event : schedule) {
            EnemyDefinition def = enemyDefinitions.get(event.type);
            if (def != null && def.isBoss) return event.time;
        }
        return -1f;
    }

    /** True once the schedule clock has crossed backgroundVideoTime - a permanent latch (only
     *  cleared by reset()/seekTo()) that GameController edge-detects to trigger the boss video
     *  hand-off exactly once - see GameController.update(). */
    public boolean isBackgroundVideoTriggered() { return backgroundVideoTriggered; }

    /** True once the schedule clock has crossed musicFadeOutTime - same permanent-latch pattern as
     *  isBackgroundVideoTriggered(), edge-detected by GameController to fade out the stage music
     *  exactly once - see GameController.update(). */
    public boolean isMusicFadeOutTriggered() { return musicFadeOutTriggered; }

    /** Debug-only: jumps the schedule clock to targetTime, marking every event on the far side of
     *  it as (un)spawned so the normal update() loop picks back up correctly from there - forward
     *  seeks skip past events without spawning them, rewinds let already-passed events fire again.
     *  The background-video and music-fade cues follow the same rule: jumping past either marks it
     *  as already fired without actually triggering it, consistent with spawn events being skipped
     *  rather than replayed. */
    public void seekTo(float targetTime) {
        totalTime = Math.max(0f, targetTime);
        if (schedule != null) {
            for (SpawnEvent event : schedule) event.spawned = event.time <= totalTime;
        }
        for (SoundCue cue : soundCues) cue.triggered = cue.time <= totalTime;
        for (SpriteCue cue : spriteCues) cue.triggered = cue.time <= totalTime;
        backgroundVideoTriggered = backgroundVideoTime != null && totalTime >= backgroundVideoTime;
        musicFadeOutTriggered = musicFadeOutTime != null && totalTime >= musicFadeOutTime;
    }

    public void update(float delta, EntityManager entityManager, AudioManager audio) {
        totalTime += delta;
        for (SpawnEvent event : schedule) {
            if (!event.spawned && totalTime >= event.time) {
                spawnEnemy(entityManager, event);
                event.spawned = true;
            }
        }
        for (SoundCue cue : soundCues) {
            if (!cue.triggered && totalTime >= cue.time) {
                audio.playCueSound(cue.sound);
                cue.triggered = true;
            }
        }
        for (SpriteCue cue : spriteCues) {
            if (!cue.triggered && totalTime >= cue.time) {
                spawnSpriteCue(entityManager, cue);
                cue.triggered = true;
            }
        }
        if (!backgroundVideoTriggered && backgroundVideoTime != null && totalTime >= backgroundVideoTime) {
            backgroundVideoTriggered = true;
        }
        if (!musicFadeOutTriggered && musicFadeOutTime != null && totalTime >= musicFadeOutTime) {
            musicFadeOutTriggered = true;
        }
    }

    private void spawnEnemy(EntityManager entityManager, SpawnEvent event) {
        EnemyDefinition def = enemyDefinitions.get(event.type);
        if (def == null) return;

        Texture tex = assets.getTexture(def.texture);
        Texture bulletTex = assets.getTexture(def.bulletTexture);
        Texture spawnTex = def.spawnTexture != null ? assets.getTexture(def.spawnTexture) : null;
        Texture deathTex = def.deathTexture != null ? assets.getTexture(def.deathTexture) : null;

        GenericEnemy enemy = ObjectPools.genericEnemyPool.obtain();

        def.inverseMovement = event.inverseMovement;

        enemy.initWithDefinition(def, tex, bulletTex, spawnTex, deathTex, worldWidth, worldHeight, event.x, event.y, event.offsetX, event.offsetY, event.movementPattern);

        if (event.powerup != null) enemy.setGuaranteedPowerup(event.powerup);
        entityManager.getEnemies().add(enemy);
    }

    private void spawnSpriteCue(EntityManager entityManager, SpriteCue cue) {
        Texture texture = assets.ensureTexture(cue.texture);
        if (texture == null) return;

        Animation<TextureRegion> animation =
            AnimationCache.get(texture, cue.columns, cue.rows, cue.frameCount, cue.frameDuration, Animation.PlayMode.NORMAL);

        // cue.size sets the draw height; width is derived from the sheet's per-frame aspect ratio
        // so non-square art (e.g. a wide banner like WarningSign.png) isn't squashed into a square.
        float frameAspect = (texture.getWidth() / (float) cue.columns) / (texture.getHeight() / (float) cue.rows);
        float height = cue.size;
        float width = height * frameAspect;
        entityManager.spawnScheduledSprite(animation, cue.x, cue.y, width, height);
    }

    public void reset() {
        totalTime = 0;
        if (schedule != null) {
            for (SpawnEvent event : schedule) event.spawned = false;
        }
        for (SoundCue cue : soundCues) cue.triggered = false;
        for (SpriteCue cue : spriteCues) cue.triggered = false;
        backgroundVideoTriggered = false;
        musicFadeOutTriggered = false;
    }
}
