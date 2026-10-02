package whitelabeltest.gamemanagers.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import whitelabeltest.perf.PerfProbe;

/** All sound effects and music. Weapon/explosion/gem sound banks come from data/sounds.json (a random
 *  pick per play); trigger/waypoint cue sounds are loaded by path and cached. */
public class AudioManager implements Disposable {
    private static final float STAGE_MUSIC_FADE_DURATION = 3f;

    private final AudioSettings settings;
    private boolean muted;
    // A ghost run's audio (see silent()): loads nothing and plays nothing.
    private final boolean silent;
    // Picks among a sound bank's variants. Separate from MathUtils.random, which drives gameplay and
    // must advance identically whether or not sounds play (a replay or ghost must stay in step).
    private final RandomXS128 variantRandom = new RandomXS128();
    private boolean fadingOutStageMusic;
    private float stageMusicFadeTimer;
    private float stageMusicFadeStartVolume;

    private final Sound playerDeathSound;
    private final Sound bombSound;
    private final Sound gameOverSound;
    private final Sound powerupSound;
    private final Sound gemPickupSound;
    // Text cue blip (single for static/blinking cues, looped while a typewriter cue types).
    private final Sound textCueSound;
    // BasicWeapon Hyper Attack: halo detaches, hits an enemy, starts returning, reattaches.
    private final Sound haloDetachSound;
    private final Sound haloBashSound;
    private final Sound haloReturnSound;
    // Instance id of the playing return sound, so playHaloLatch() can cut just that one.
    private long haloReturnSoundId = -1;
    private final Sound haloLatchSound;
    // Graze points earned a bomb.
    private final Sound grazeLevelUpSound;
    // Bomb cooldown ended with a bomb in stock.
    private final Sound bombReadySound;
    // Orbit weapon's reflect shield recharged.
    private final Sound shieldsReadySound;
    // Thunderbolt Hyper Attack: one sound per charge tier (in order), plus the detonation.
    private final Sound[] thunderboltHyperLevelSounds;
    private final Sound thunderboltHyperExplosionSound;
    // Thunderbolt main fire with nothing to strike.
    private final Sound thunderboltNullSound;
    private final Music victoryFanfare;
    private final Music victoryLoop;
    private Music stageMusic;
    private String stageMusicPath;
    private final ObjectMap<Integer, Array<Sound>> pointGemSounds;
    private final ObjectMap<Integer, Array<Sound>> explosionSounds;
    private final ObjectMap<Integer, Array<Sound>> basicWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> waveBlastWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> thunderboltWeaponSounds;
    // Orbit weapon bullet impact.
    private final ObjectMap<Integer, Array<Sound>> orbitGongSounds;
    // Orbit ring whip crack, once per blade per lap.
    private final ObjectMap<Integer, Array<Sound>> orbitWhipSounds;
    // Scripted cue sounds keyed by asset path, loaded on first use or preload.
    private final ObjectMap<String, Sound> cueSounds = new ObjectMap<>();

    // Sounds that can fire many times a frame (gem showers, mass kills) play at most once per
    // interval: hundreds of Sound.play() calls a frame caused frame drops and sound no different.
    private static final float POINT_GEM_SOUND_INTERVAL = 0.05f;
    private static final float EXPLOSION_SOUND_INTERVAL = 0.04f;
    private static final float ORBIT_GONG_SOUND_INTERVAL = 0.05f;
    private static final float HALO_BASH_SOUND_INTERVAL = 0.05f;
    // Audio clock and when each throttled sound last played.
    private float clock;
    private float lastPointGem = -1f, lastExplosion = -1f, lastOrbitGong = -1f, lastHaloBash = -1f;

    /** Audio that loads and plays nothing, for a ghost run. */
    public static AudioManager silent(AudioSettings settings) {
        return new AudioManager(settings, true);
    }

    public AudioManager(AudioSettings settings) {
        this(settings, false);
    }

    private AudioManager(AudioSettings settings, boolean silent) {
        this.settings = settings;
        this.silent = silent;
        if (silent) {
            muted = true;
            playerDeathSound = bombSound = gameOverSound = powerupSound = gemPickupSound = textCueSound = null;
            haloDetachSound = haloBashSound = haloReturnSound = haloLatchSound = null;
            grazeLevelUpSound = bombReadySound = shieldsReadySound = null;
            thunderboltHyperLevelSounds = new Sound[0];
            thunderboltHyperExplosionSound = thunderboltNullSound = null;
            victoryFanfare = victoryLoop = null;
            basicWeaponSounds = waveBlastWeaponSounds = thunderboltWeaponSounds = null;
            explosionSounds = pointGemSounds = orbitGongSounds = orbitWhipSounds = null;
            return;
        }
        playerDeathSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/playerdeath.mp3"));
        bombSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/bombsound.mp3"));
        gameOverSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/gameover.mp3"));
        powerupSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/powerup.mp3"));
        gemPickupSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/pointgem.mp3"));
        textCueSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/textsound.mp3"));
        haloDetachSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/halo_release.mp3"));
        haloBashSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/halo_bash.mp3"));
        haloReturnSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/halo_return.mp3"));
        haloLatchSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/halo_latch.mp3"));
        grazeLevelUpSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/grazelevelup.mp3"));
        bombReadySound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/bombready.mp3"));
        shieldsReadySound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/shieldsready.mp3"));
        thunderboltHyperLevelSounds = new Sound[] {
            Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperlevel.wav")),
            Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperlevel-001.wav")),
            Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperlevel-002.wav")),
            Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperlevel-003.wav"))
        };
        thunderboltHyperExplosionSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperexplosion.wav"));
        thunderboltNullSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/nulllightning.mp3"));
        victoryFanfare = Gdx.audio.newMusic(Gdx.files.internal("audio/music/victoryfanfare.mp3"));
        victoryLoop = Gdx.audio.newMusic(Gdx.files.internal("audio/music/victory.mp3"));
        victoryLoop.setLooping(true);
        victoryFanfare.setOnCompletionListener(music -> victoryLoop.play());
        Json json = new Json();
        basicWeaponSounds = new ObjectMap<>();
        waveBlastWeaponSounds = new ObjectMap<>();
        thunderboltWeaponSounds = new ObjectMap<>();
        explosionSounds = new ObjectMap<>();
        pointGemSounds = new ObjectMap<>();
        orbitGongSounds = new ObjectMap<>();
        orbitWhipSounds = new ObjectMap<>();
        @SuppressWarnings("unchecked")
        Array<SoundBank> soundBanks = json.fromJson(Array.class, SoundBank.class, Gdx.files.internal("data/sounds.json"));
        for(SoundBank sBank : soundBanks) {
            if(sBank.type == SoundType.BasicWeapon) {
                populateSounds(sBank, basicWeaponSounds);
            }
            if(sBank.type == SoundType.Thunderbolt) {
                populateSounds(sBank, thunderboltWeaponSounds);
            }
            if(sBank.type == SoundType.WaveBlastWeapon) {
                populateSounds(sBank, waveBlastWeaponSounds);
            }
            if(sBank.type == SoundType.Explosion) {
                populateSounds(sBank, explosionSounds);
            }
            if(sBank.type == SoundType.PointGem) {
                populateSounds(sBank, pointGemSounds);
            }
            if(sBank.type == SoundType.OrbitGong) {
                populateSounds(sBank, orbitGongSounds);
            }
            if(sBank.type == SoundType.OrbitWhip) {
                populateSounds(sBank, orbitWhipSounds);
            }

        }
    }

    private void populateSounds(SoundBank sBank, ObjectMap<Integer, Array<Sound>> soundsArray) {
        Array<Sound> tempArray = new Array<>();
        for (String s : sBank.sounds) {
            tempArray.add(Gdx.audio.newSound(Gdx.files.internal(s)));
        }
        soundsArray.put(sBank.level, tempArray);
    }

    /** Loads the stage track (disposing the old one). Called on every stage load, so stageMusic is
     *  never null afterwards. */
    public void loadStageMusic(String path) {
        fadingOutStageMusic = false;
        stageMusicPath = path;
        if (silent) return;
        if (stageMusic != null) stageMusic.dispose();
        stageMusic = Gdx.audio.newMusic(Gdx.files.internal(path));
        stageMusic.setLooping(true);
    }

    /** Switches to and plays (looping) the track at `path` mid-stage, cancelling any fade. No-op if
     *  that track is already playing, so re-syncing after a seek never restarts it. */
    public void switchStageMusic(String path) {
        if (path == null) return;
        if (silent) {
            stageMusicPath = path;
            return;
        }
        if (path.equals(stageMusicPath) && stageMusic.isPlaying() && !fadingOutStageMusic) return;
        if (stageMusic != null) stageMusic.stop();
        loadStageMusic(path);
        playStageMusic();
    }

    /** Asset path of the loaded stage track. */
    public String getStageMusicPath() { return stageMusicPath; }

    public void setMuted(boolean muted) {
        if (silent) return;
        this.muted = muted;
        stageMusic.setVolume(muted ? 0f : settings.getEffectiveMusicVolume());
    }
    public boolean isMuted() { return muted; }

    public void playStageMusic() {
        fadingOutStageMusic = false;
        if (silent) return;
        stageMusic.setVolume(muted ? 0f : settings.getEffectiveMusicVolume());
        stageMusic.play();
    }

    public void stopStageMusic() {
        fadingOutStageMusic = false;
        if (silent) return;
        stageMusic.stop();
    }

    /** Fades the stage music out over STAGE_MUSIC_FADE_DURATION, then stops it (used before the boss
     *  video's own audio). */
    public void fadeOutStageMusic() {
        if (silent) return;
        if (!stageMusic.isPlaying() || fadingOutStageMusic) return;
        fadingOutStageMusic = true;
        stageMusicFadeTimer = 0f;
        stageMusicFadeStartVolume = stageMusic.getVolume();
    }

    public void update(float delta) {
        clock += delta;
        if (fadingOutStageMusic) {
            stageMusicFadeTimer += delta;
            float t = Math.min(stageMusicFadeTimer / STAGE_MUSIC_FADE_DURATION, 1f);
            stageMusic.setVolume(stageMusicFadeStartVolume * (1f - t));
            if (t >= 1f) {
                stageMusic.stop();
                fadingOutStageMusic = false;
            }
        }
    }

    public void playPlayerDeath() {
        PerfProbe.soundStarted();
        if (!muted) playerDeathSound.play(settings.getEffectiveSfxVolume());
    }

    public void playBomb() {
        PerfProbe.soundStarted();
        if (!muted) bombSound.play(settings.getEffectiveSfxVolume());
    }

    public void playGameOver() {
        PerfProbe.soundStarted();
        if (!muted) gameOverSound.play(settings.getEffectiveSfxVolume());
    }

    public void playPowerup() {
        PerfProbe.soundStarted();
        if (!muted) powerupSound.play(settings.getEffectiveSfxVolume());
    }

    // Single blip for a static/blinking text cue appearing.
    public void playTextCue() {
        PerfProbe.soundStarted();
        if (!muted) textCueSound.play(settings.getEffectiveSfxVolume());
    }

    // Loops the blip while a typewriter cue reveals.
    public void loopTextCue() {
        if (!muted) textCueSound.loop(settings.getEffectiveSfxVolume());
    }

    // Stops every blip instance. Not gated on `muted`, so a loop started before muting still stops.
    public void stopTextCueLoop() {
        if (!silent) textCueSound.stop();
    }

    public void playHaloDetach() {
        PerfProbe.soundStarted();
        if (!muted) haloDetachSound.play(settings.getEffectiveSfxVolume());
    }

    public void playHaloBash() {
        if (clock - lastHaloBash < HALO_BASH_SOUND_INTERVAL && lastHaloBash >= 0f) return;
        lastHaloBash = clock;
        PerfProbe.soundStarted();
        if (!muted) haloBashSound.play(settings.getEffectiveSfxVolume());
    }

    public void playHaloReturn() {
        PerfProbe.soundStarted();
        if (!muted) haloReturnSoundId = haloReturnSound.play(settings.getEffectiveSfxVolume());
    }

    /** Cuts off a still-playing return sound so it doesn't overlap the latch on a short return. */
    public void playHaloLatch() {
        if (haloReturnSoundId != -1) {
            haloReturnSound.stop(haloReturnSoundId);
            haloReturnSoundId = -1;
        }
        PerfProbe.soundStarted();
        if (!muted) haloLatchSound.play(settings.getEffectiveSfxVolume());
    }

    public void playGrazeBombEarned() {
        PerfProbe.soundStarted();
        if (!muted) grazeLevelUpSound.play(settings.getEffectiveSfxVolume());
    }

    public void playBombReady() {
        PerfProbe.soundStarted();
        if (!muted) bombReadySound.play(settings.getEffectiveSfxVolume());
    }

    public void playShieldsReady() {
        PerfProbe.soundStarted();
        if (!muted) shieldsReadySound.play(settings.getEffectiveSfxVolume());
    }

    public void playVictory() {
        if (!muted) {
            victoryFanfare.setVolume(settings.getEffectiveSfxVolume());
            victoryLoop.setVolume(settings.getEffectiveSfxVolume());
            victoryFanfare.play();
        }
    }

    public void stopVictory() {
        if (silent) return;
        victoryFanfare.stop();
        victoryLoop.stop();
    }

    public void playPointGem() {
        if (clock - lastPointGem < POINT_GEM_SOUND_INTERVAL && lastPointGem >= 0f) return;
        lastPointGem = clock;
        PerfProbe.soundStarted();
        if (!muted && pointGemSounds != null) pick(pointGemSounds.get(1)).play(settings.getEffectiveSfxVolume());
    }

    public void playExplosion() {
        if (clock - lastExplosion < EXPLOSION_SOUND_INTERVAL && lastExplosion >= 0f) return;
        lastExplosion = clock;
        PerfProbe.soundStarted();
        if (!muted && explosionSounds != null) pick(explosionSounds.get(1)).play(settings.getEffectiveSfxVolume());
    }

    public void playOrbitGong() {
        if (clock - lastOrbitGong < ORBIT_GONG_SOUND_INTERVAL && lastOrbitGong >= 0f) return;
        lastOrbitGong = clock;
        PerfProbe.soundStarted();
        if (!muted && orbitGongSounds != null) pick(orbitGongSounds.get(1)).play(settings.getEffectiveSfxVolume());
    }

    public void playBasicWeaponSound(int level) {
        if (!muted && basicWeaponSounds != null && basicWeaponSounds.containsKey(level)) pick(basicWeaponSounds.get(level)).play(settings.getEffectiveSfxVolume());
    }

    public void playWaveBlastWeaponSound(int level) {
        if (!muted && waveBlastWeaponSounds != null && waveBlastWeaponSounds.containsKey(level)) pick(waveBlastWeaponSounds.get(level)).play(settings.getEffectiveSfxVolume());
    }

    public void playOrbitWhip() {
        PerfProbe.soundStarted();
        if (!muted && orbitWhipSounds != null) pick(orbitWhipSounds.get(1)).play(settings.getEffectiveSfxVolume());
    }

    public void playThunderboltWeaponSound(int level) {
        if (!muted && thunderboltWeaponSounds != null && thunderboltWeaponSounds.containsKey(level)) pick(thunderboltWeaponSounds.get(level)).play(settings.getEffectiveSfxVolume());
    }

    public void playThunderboltNullSound() {
        PerfProbe.soundStarted();
        if (!muted) thunderboltNullSound.play(settings.getEffectiveSfxVolume());
    }

    /** The charge sound for Thunderbolt Hyper Attack tier `tier` (0-based). */
    public void playThunderboltHyperLevel(int tier) {
        PerfProbe.soundStarted();
        if (!muted && tier >= 0 && tier < thunderboltHyperLevelSounds.length) thunderboltHyperLevelSounds[tier].play(settings.getEffectiveSfxVolume());
    }

    public void playThunderboltHyperExplosion() {
        PerfProbe.soundStarted();
        if (!muted) thunderboltHyperExplosionSound.play(settings.getEffectiveSfxVolume());
    }

    /** Plays a scripted cue sound by asset path (loaded and cached on first use). */
    public void playCueSound(String path) {
        playCueSound(path, 1f, 1f);
    }

    /** Loads a cue sound ahead of time so its first play doesn't stall a frame. */
    public void preloadCueSound(String path) {
        if (silent || path == null || cueSounds.containsKey(path)) return;
        if (!Gdx.files.internal(path).exists()) return;
        cueSounds.put(path, Gdx.audio.newSound(Gdx.files.internal(path)));
    }

    /** @param volume multiplied into the SFX volume setting. @param pitch 1 = unchanged. */
    public void playCueSound(String path, float volume, float pitch) {
        if (silent || path == null) return;
        Sound sound = cueSounds.get(path);
        if (sound == null) {
            sound = Gdx.audio.newSound(Gdx.files.internal(path));
            cueSounds.put(path, sound);
        }
        PerfProbe.soundStarted();
        if (!muted) sound.play(settings.getEffectiveSfxVolume() * volume, pitch, 0f);
    }

    @Override
    public void dispose() {
        if (silent) return;
        playerDeathSound.dispose();
        bombSound.dispose();
        gameOverSound.dispose();
        powerupSound.dispose();
        gemPickupSound.dispose();
        textCueSound.dispose();
        haloDetachSound.dispose();
        haloBashSound.dispose();
        haloReturnSound.dispose();
        haloLatchSound.dispose();
        grazeLevelUpSound.dispose();
        bombReadySound.dispose();
        shieldsReadySound.dispose();
        for (Sound s : thunderboltHyperLevelSounds) s.dispose();
        thunderboltHyperExplosionSound.dispose();
        thunderboltNullSound.dispose();
        victoryFanfare.dispose();
        victoryLoop.dispose();
        if (stageMusic != null) stageMusic.dispose();
        disposeSoundsMap(pointGemSounds);
        disposeSoundsMap(explosionSounds);
        disposeSoundsMap(basicWeaponSounds);
        disposeSoundsMap(waveBlastWeaponSounds);
        disposeSoundsMap(thunderboltWeaponSounds);
        disposeSoundsMap(orbitGongSounds);
        disposeSoundsMap(orbitWhipSounds);
        for (Sound s : cueSounds.values()) s.dispose();
    }

    private Sound pick(Array<Sound> variants) {
        return variants.get(variantRandom.nextInt(variants.size));
    }

    private void disposeSoundsMap(ObjectMap<Integer, Array<Sound>> soundsMap) {
        if (soundsMap != null) {
            for (int i = 1; i <= soundsMap.size; i++) {
                for (Sound s : soundsMap.get(i)) s.dispose();
            }
        }
    }
}
