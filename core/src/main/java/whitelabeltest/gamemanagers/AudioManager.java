package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

public class AudioManager implements Disposable {
    private final AudioSettings settings;
    private boolean muted;

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
    private final Music stageMusic;
    private final ObjectMap<Integer, Array<Sound>> pointGemSounds;
    private final ObjectMap<Integer, Array<Sound>> explosionSounds;
    private final ObjectMap<Integer, Array<Sound>> basicWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> waveBlastWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> orbitWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> thunderboltWeaponSounds;

    public AudioManager(AudioSettings settings) {
        this.settings = settings;
        playerDeathSound = Gdx.audio.newSound(Gdx.files.internal("playerdeath.mp3"));
        bombSound = Gdx.audio.newSound(Gdx.files.internal("bombsound.mp3"));
        gameOverSound = Gdx.audio.newSound(Gdx.files.internal("gameover.mp3"));
        powerupSound = Gdx.audio.newSound(Gdx.files.internal("powerup.mp3"));
        gemPickupSound = Gdx.audio.newSound(Gdx.files.internal("pointgem.mp3"));
        haloDetachSound = Gdx.audio.newSound(Gdx.files.internal("halo_release.mp3"));
        haloBashSound = Gdx.audio.newSound(Gdx.files.internal("halo_bash.mp3"));
        haloReturnSound = Gdx.audio.newSound(Gdx.files.internal("halo_return.mp3"));
        haloLatchSound = Gdx.audio.newSound(Gdx.files.internal("halo_latch.mp3"));
        thunderboltHyperLevelSounds = new Sound[] {
            Gdx.audio.newSound(Gdx.files.internal("thunderbolthyperlevel.wav")),
            Gdx.audio.newSound(Gdx.files.internal("thunderbolthyperlevel-001.wav")),
            Gdx.audio.newSound(Gdx.files.internal("thunderbolthyperlevel-002.wav")),
            Gdx.audio.newSound(Gdx.files.internal("thunderbolthyperlevel-003.wav"))
        };
        thunderboltHyperExplosionSound = Gdx.audio.newSound(Gdx.files.internal("thunderbolthyperexplosion.wav"));
        victoryFanfare = Gdx.audio.newMusic(Gdx.files.internal("victoryfanfare.mp3"));
        victoryLoop = Gdx.audio.newMusic(Gdx.files.internal("victory.mp3"));
        victoryLoop.setLooping(true);
        victoryFanfare.setOnCompletionListener(music -> victoryLoop.play());
        stageMusic = Gdx.audio.newMusic(Gdx.files.internal("battleontheedge.mp3"));
        stageMusic.setLooping(true);
        Json json = new Json();
        basicWeaponSounds = new ObjectMap<>();
        waveBlastWeaponSounds = new ObjectMap<>();
        orbitWeaponSounds = new ObjectMap<>();
        thunderboltWeaponSounds = new ObjectMap<>();
        explosionSounds = new ObjectMap<>();
        pointGemSounds = new ObjectMap<>();
        @SuppressWarnings("unchecked")
        Array<SoundBank> soundBanks = json.fromJson(Array.class, SoundBank.class, Gdx.files.internal("sounds.json"));
        for(SoundBank sBank : soundBanks) {
            if(sBank.type == SoundType.BasicWeapon) {
                populateSounds(sBank, basicWeaponSounds);
            }
            if(sBank.type == SoundType.OrbitWeapon) {
                populateSounds(sBank, orbitWeaponSounds);
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

        }
    }

    private void populateSounds(SoundBank sBank, ObjectMap<Integer, Array<Sound>> soundsArray) {
        Array<Sound> tempArray = new Array<>();
        for (String s : sBank.sounds) {
            tempArray.add(Gdx.audio.newSound(Gdx.files.internal(s)));
        }
        soundsArray.put(sBank.level, tempArray);
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        stageMusic.setVolume(muted ? 0f : settings.getMusicVolume());
    }
    public boolean isMuted() { return muted; }

    public void playStageMusic() {
        stageMusic.setVolume(muted ? 0f : settings.getMusicVolume());
        stageMusic.play();
    }

    public void stopStageMusic() {
        stageMusic.stop();
    }

    public void playPlayerDeath() {
        if (!muted) playerDeathSound.play(settings.getSfxVolume());
    }

    public void playBomb() {
        if (!muted) bombSound.play(settings.getSfxVolume());
    }

    public void playGameOver() {
        if (!muted) gameOverSound.play(settings.getSfxVolume());
    }

    public void playPowerup() {
        if (!muted) powerupSound.play(settings.getSfxVolume());
    }

    public void playHaloDetach() {
        if (!muted) haloDetachSound.play(settings.getSfxVolume());
    }

    public void playHaloBash() {
        if (!muted) haloBashSound.play(settings.getSfxVolume());
    }

    public void playHaloReturn() {
        if (!muted) haloReturnSoundId = haloReturnSound.play(settings.getSfxVolume());
    }

    /** Cuts off halo_return.mp3 if it's still playing from the start of this same return trip -
     *  the two can otherwise overlap when the glide back is short/fast enough that the return cue
     *  hasn't finished by the time the halo actually reattaches. */
    public void playHaloLatch() {
        if (haloReturnSoundId != -1) {
            haloReturnSound.stop(haloReturnSoundId);
            haloReturnSoundId = -1;
        }
        if (!muted) haloLatchSound.play(settings.getSfxVolume());
    }

    public void playVictory() {
        if (!muted) {
            victoryFanfare.setVolume(settings.getSfxVolume());
            victoryLoop.setVolume(settings.getSfxVolume());
            victoryFanfare.play();
        }
    }

    public void stopVictory() {
        victoryFanfare.stop();
        victoryLoop.stop();
    }

    public void playPointGem() {
        if (!muted && pointGemSounds != null) pointGemSounds.get(1).random().play(settings.getSfxVolume());
    }

    public void playExplosion() {
        if (!muted && explosionSounds != null) explosionSounds.get(1).random().play(settings.getSfxVolume());
    }

    public void playBasicWeaponSound(int level) {
        if (!muted && basicWeaponSounds != null && basicWeaponSounds.containsKey(level)) basicWeaponSounds.get(level).random().play(settings.getSfxVolume());
    }

    public void playWaveBlastWeaponSound(int level) {
        if (!muted && waveBlastWeaponSounds != null && waveBlastWeaponSounds.containsKey(level)) waveBlastWeaponSounds.get(level).random().play(settings.getSfxVolume());
    }

    public void playOrbitWeaponSound(int level) {
        if (!muted && orbitWeaponSounds != null && orbitWeaponSounds.containsKey(level)) orbitWeaponSounds.get(level).random().play(settings.getSfxVolume());
    }

    public void playThunderboltWeaponSound(int level) {
        if (!muted && thunderboltWeaponSounds != null && thunderboltWeaponSounds.containsKey(level)) thunderboltWeaponSounds.get(level).random().play(settings.getSfxVolume());
    }

    /** Plays the tier-th (0-based) charge sound for ThunderboltWeapon's Hyper Attack bomb - see
     *  Player.updateThunderboltCharge, which calls this once per tier as the bomb climbs through
     *  THUNDERBOLT_CHARGE_DAMAGE, in order, rather than picking randomly like the sound banks above. */
    public void playThunderboltHyperLevel(int tier) {
        if (!muted && tier >= 0 && tier < thunderboltHyperLevelSounds.length) thunderboltHyperLevelSounds[tier].play(settings.getSfxVolume());
    }

    public void playThunderboltHyperExplosion() {
        if (!muted) thunderboltHyperExplosionSound.play(settings.getSfxVolume());
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
        stageMusic.dispose();
        disposeSoundsMap(pointGemSounds);
        disposeSoundsMap(explosionSounds);
        disposeSoundsMap(basicWeaponSounds);
        disposeSoundsMap(waveBlastWeaponSounds);
        disposeSoundsMap(orbitWeaponSounds);
        disposeSoundsMap(thunderboltWeaponSounds);
    }

    private void disposeSoundsMap(ObjectMap<Integer, Array<Sound>> soundsMap) {
        if (soundsMap != null) {
            for (int i = 1; i <= soundsMap.size; i++) {
                for (Sound s : soundsMap.get(i)) s.dispose();
            }
        }
    }
}
