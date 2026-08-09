package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

public class AudioManager implements Disposable {
    private static final float STAGE_MUSIC_FADE_DURATION = 3f;

    private final AudioSettings settings;
    private boolean muted;
    private boolean fadingOutStageMusic;
    private float stageMusicFadeTimer;
    private float stageMusicFadeStartVolume;

    private final Sound playerDeathSound;
    private final Sound bombSound;
    private final Sound gameOverSound;
    private final Sound powerupSound;
    private final Sound gemPickupSound;
    // BasicWeapon's Hyper Attack (see Player.triggerBasicHyperAttack): plays once, the moment the
    // halo actually detaches from the ship to dash out - not on the re-press that starts its
    // return trip.
    private final Sound haloDetachSound;
    // BasicWeapon's Hyper Attack dash (see CollisionManager.checkHaloDashCollisions): plays once
    // per enemy the halo clips while dashing out.
    private final Sound haloBashSound;
    // BasicWeapon's Hyper Attack (see Player.triggerBasicHyperAttack/recallHaloOnWeaponSwitch):
    // plays once, the moment the halo starts gliding back to reattach.
    private final Sound haloReturnSound;
    // Sound.play()'s instance id for the currently-playing haloReturnSound, so playHaloLatch() can
    // stop that specific instance rather than every playing copy of the sound - see playHaloLatch().
    private long haloReturnSoundId = -1;
    // BasicWeapon's Hyper Attack (see Player.updateHaloMovement): plays once, the moment the halo
    // finishes its glide back and reattaches to the ship.
    private final Sound haloLatchSound;
    // ThunderboltWeapon's Hyper Attack (see Player.updateThunderboltCharge/CollisionManager.
    // checkThunderboltDetonation): one distinct sound per charge tier, played in a fixed order as
    // the bomb climbs through them - not a random pick from a pool like the per-weapon-level
    // sound banks below - plus a dedicated explosion sound on detonation.
    private final Sound[] thunderboltHyperLevelSounds;
    private final Sound thunderboltHyperExplosionSound;
    private final Music victoryFanfare;
    private final Music victoryLoop;
    private Music stageMusic;
    private final ObjectMap<Integer, Array<Sound>> pointGemSounds;
    private final ObjectMap<Integer, Array<Sound>> explosionSounds;
    private final ObjectMap<Integer, Array<Sound>> basicWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> waveBlastWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> thunderboltWeaponSounds;
    // OrbitWeapon's bullet-hits-enemy impact sound (see CollisionManager.checkBulletEnemyCollisions)
    // - OrbitWeapon's Hyper Attack (shield) sound instead reuses waveBlastWeaponSounds (see
    // OrbitWeapon.hyperAttack()).
    private final ObjectMap<Integer, Array<Sound>> orbitGongSounds;
    // OrbitWeapon's ring-rotation firing sound (see OrbitWeapon.update()) - plays once per ring
    // member per full lap, so it fires `level` times per rotation (one crack per orbiting blade).
    private final ObjectMap<Integer, Array<Sound>> orbitWhipSounds;
    // Scripted one-off SFX triggered by SpawnScheduler's SoundCue (see playCueSound()) - keyed by
    // asset path and loaded lazily the first time each is cued, since these are level-specific and
    // not worth preloading into a dedicated field like the sounds above.
    private final ObjectMap<String, Sound> cueSounds = new ObjectMap<>();

    public AudioManager(AudioSettings settings) {
        this.settings = settings;
        playerDeathSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/playerdeath.mp3"));
        bombSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/bombsound.mp3"));
        gameOverSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/gameover.mp3"));
        powerupSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/powerup.mp3"));
        gemPickupSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/pointgem.mp3"));
        haloDetachSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/halo_release.mp3"));
        haloBashSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/halo_bash.mp3"));
        haloReturnSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/halo_return.mp3"));
        haloLatchSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/halo_latch.mp3"));
        thunderboltHyperLevelSounds = new Sound[] {
            Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperlevel.wav")),
            Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperlevel-001.wav")),
            Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperlevel-002.wav")),
            Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperlevel-003.wav"))
        };
        thunderboltHyperExplosionSound = Gdx.audio.newSound(Gdx.files.internal("audio/sfx/thunderbolthyperexplosion.wav"));
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

    /** Swaps the currently-loaded stage track for the one at path, disposing the old one - called
     *  once per stage load (see GameController.loadStage()), always before playStageMusic()/
     *  setMuted() are next used, so those methods can keep assuming stageMusic is non-null. */
    public void loadStageMusic(String path) {
        if (stageMusic != null) stageMusic.dispose();
        stageMusic = Gdx.audio.newMusic(Gdx.files.internal(path));
        stageMusic.setLooping(true);
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        stageMusic.setVolume(muted ? 0f : settings.getEffectiveMusicVolume());
    }
    public boolean isMuted() { return muted; }

    public void playStageMusic() {
        fadingOutStageMusic = false;
        stageMusic.setVolume(muted ? 0f : settings.getEffectiveMusicVolume());
        stageMusic.play();
    }

    public void stopStageMusic() {
        fadingOutStageMusic = false;
        stageMusic.stop();
    }

    /** Gradually lowers stageMusic to silence over STAGE_MUSIC_FADE_DURATION and then stops it,
     *  instead of stopStageMusic()'s hard cut - see update(). Used for the boss video/audio
     *  handoff in ScrollingBackground, where an abrupt cut would clash with the video's own audio. */
    public void fadeOutStageMusic() {
        if (!stageMusic.isPlaying() || fadingOutStageMusic) return;
        fadingOutStageMusic = true;
        stageMusicFadeTimer = 0f;
        stageMusicFadeStartVolume = stageMusic.getVolume();
    }

    public void update(float delta) {
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
        if (!muted) playerDeathSound.play(settings.getEffectiveSfxVolume());
    }

    public void playBomb() {
        if (!muted) bombSound.play(settings.getEffectiveSfxVolume());
    }

    public void playGameOver() {
        if (!muted) gameOverSound.play(settings.getEffectiveSfxVolume());
    }

    public void playPowerup() {
        if (!muted) powerupSound.play(settings.getEffectiveSfxVolume());
    }

    public void playHaloDetach() {
        if (!muted) haloDetachSound.play(settings.getEffectiveSfxVolume());
    }

    public void playHaloBash() {
        if (!muted) haloBashSound.play(settings.getEffectiveSfxVolume());
    }

    public void playHaloReturn() {
        if (!muted) haloReturnSoundId = haloReturnSound.play(settings.getEffectiveSfxVolume());
    }

    /** Cuts off halo_return.mp3 if it's still playing from the start of this same return trip -
     *  the two can otherwise overlap when the glide back is short/fast enough that the return cue
     *  hasn't finished by the time the halo actually reattaches. */
    public void playHaloLatch() {
        if (haloReturnSoundId != -1) {
            haloReturnSound.stop(haloReturnSoundId);
            haloReturnSoundId = -1;
        }
        if (!muted) haloLatchSound.play(settings.getEffectiveSfxVolume());
    }

    public void playVictory() {
        if (!muted) {
            victoryFanfare.setVolume(settings.getEffectiveSfxVolume());
            victoryLoop.setVolume(settings.getEffectiveSfxVolume());
            victoryFanfare.play();
        }
    }

    public void stopVictory() {
        victoryFanfare.stop();
        victoryLoop.stop();
    }

    public void playPointGem() {
        if (!muted && pointGemSounds != null) pointGemSounds.get(1).random().play(settings.getEffectiveSfxVolume());
    }

    public void playExplosion() {
        if (!muted && explosionSounds != null) explosionSounds.get(1).random().play(settings.getEffectiveSfxVolume());
    }

    /** OrbitWeapon's bullet-hits-enemy impact sound - see CollisionManager.checkBulletEnemyCollisions,
     *  which calls this once per orbit-bullet hit alongside its OrbitSparks.png hit effect. */
    public void playOrbitGong() {
        if (!muted && orbitGongSounds != null) orbitGongSounds.get(1).random().play(settings.getEffectiveSfxVolume());
    }

    public void playBasicWeaponSound(int level) {
        if (!muted && basicWeaponSounds != null && basicWeaponSounds.containsKey(level)) basicWeaponSounds.get(level).random().play(settings.getEffectiveSfxVolume());
    }

    public void playWaveBlastWeaponSound(int level) {
        if (!muted && waveBlastWeaponSounds != null && waveBlastWeaponSounds.containsKey(level)) waveBlastWeaponSounds.get(level).random().play(settings.getEffectiveSfxVolume());
    }

    /** OrbitWeapon's ring-rotation whip crack - see OrbitWeapon.update(), which calls this once per
     *  ring member each time that member completes a full lap, so it plays `level` times per
     *  rotation of the ring (one crack per orbiting blade). */
    public void playOrbitWhip() {
        if (!muted && orbitWhipSounds != null) orbitWhipSounds.get(1).random().play(settings.getEffectiveSfxVolume());
    }

    public void playThunderboltWeaponSound(int level) {
        if (!muted && thunderboltWeaponSounds != null && thunderboltWeaponSounds.containsKey(level)) thunderboltWeaponSounds.get(level).random().play(settings.getEffectiveSfxVolume());
    }

    /** Plays the tier-th (0-based) charge sound for ThunderboltWeapon's Hyper Attack bomb - see
     *  Player.updateThunderboltCharge, which calls this once per tier as the bomb climbs through
     *  its damage tiers (weapons.json's thunderboltChargeDamageByTier), in order, rather than
     *  picking randomly like the sound banks above. */
    public void playThunderboltHyperLevel(int tier) {
        if (!muted && tier >= 0 && tier < thunderboltHyperLevelSounds.length) thunderboltHyperLevelSounds[tier].play(settings.getEffectiveSfxVolume());
    }

    public void playThunderboltHyperExplosion() {
        if (!muted) thunderboltHyperExplosionSound.play(settings.getEffectiveSfxVolume());
    }

    /** Plays a scripted one-off SFX by asset path - see SpawnScheduler.SoundCue. Loads and caches
     *  the Sound the first time this path is triggered rather than up front, since which cue
     *  sounds exist is entirely down to spawn_schedule.json. */
    public void playCueSound(String path) {
        if (path == null) return;
        Sound sound = cueSounds.get(path);
        if (sound == null) {
            sound = Gdx.audio.newSound(Gdx.files.internal(path));
            cueSounds.put(path, sound);
        }
        if (!muted) sound.play(settings.getEffectiveSfxVolume());
    }

    @Override
    public void dispose() {
        playerDeathSound.dispose();
        bombSound.dispose();
        gameOverSound.dispose();
        powerupSound.dispose();
        gemPickupSound.dispose();
        haloDetachSound.dispose();
        haloBashSound.dispose();
        haloReturnSound.dispose();
        haloLatchSound.dispose();
        for (Sound s : thunderboltHyperLevelSounds) s.dispose();
        thunderboltHyperExplosionSound.dispose();
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

    private void disposeSoundsMap(ObjectMap<Integer, Array<Sound>> soundsMap) {
        if (soundsMap != null) {
            for (int i = 1; i <= soundsMap.size; i++) {
                for (Sound s : soundsMap.get(i)) s.dispose();
            }
        }
    }
}
