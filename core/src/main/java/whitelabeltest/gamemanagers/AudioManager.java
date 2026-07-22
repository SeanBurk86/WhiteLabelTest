package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

public class AudioManager implements Disposable {
    private boolean muted;

    private final Sound playerDeathSound;
    private final Sound bombSound;
    private final Sound gameOverSound;
    private final Sound powerupSound;
    private final Sound gemPickupSound;
    private final Music victoryFanfare;
    private final Music victoryLoop;
    private final ObjectMap<Integer, Array<Sound>> pointGemSounds;
    private final ObjectMap<Integer, Array<Sound>> explosionSounds;
    private final ObjectMap<Integer, Array<Sound>> basicWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> waveBlastWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> orbitWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> thunderboltWeaponSounds;

    public AudioManager() {
        playerDeathSound = Gdx.audio.newSound(Gdx.files.internal("playerdeath.mp3"));
        bombSound = Gdx.audio.newSound(Gdx.files.internal("bombsound.mp3"));
        gameOverSound = Gdx.audio.newSound(Gdx.files.internal("gameover.mp3"));
        powerupSound = Gdx.audio.newSound(Gdx.files.internal("powerup.mp3"));
        gemPickupSound = Gdx.audio.newSound(Gdx.files.internal("pointgem.mp3"));
        victoryFanfare = Gdx.audio.newMusic(Gdx.files.internal("victoryfanfare.mp3"));
        victoryLoop = Gdx.audio.newMusic(Gdx.files.internal("victory.mp3"));
        victoryLoop.setLooping(true);
        victoryFanfare.setOnCompletionListener(music -> victoryLoop.play());
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

    public void setMuted(boolean muted) { this.muted = muted; }
    public boolean isMuted() { return muted; }

    public void playPlayerDeath() {
        if (!muted) playerDeathSound.play();
    }

    public void playBomb() {
        if (!muted) bombSound.play();
    }

    public void playGameOver() {
        if (!muted) gameOverSound.play();
    }

    public void playPowerup() {
        if (!muted) powerupSound.play();
    }

    public void playVictory() {
        if (!muted) victoryFanfare.play();
    }

    public void stopVictory() {
        victoryFanfare.stop();
        victoryLoop.stop();
    }

    public void playPointGem() {
        if (!muted && pointGemSounds != null) pointGemSounds.get(1).random().play();
    }

    public void playExplosion() {
        if (!muted && explosionSounds != null) explosionSounds.get(1).random().play();
    }

    public void playBasicWeaponSound(int level) {
        if (!muted && basicWeaponSounds != null && basicWeaponSounds.containsKey(level)) basicWeaponSounds.get(level).random().play();
    }

    public void playWaveBlastWeaponSound(int level) {
        if (!muted && waveBlastWeaponSounds != null && waveBlastWeaponSounds.containsKey(level)) waveBlastWeaponSounds.get(level).random().play();
    }

    public void playOrbitWeaponSound(int level) {
        if (!muted && orbitWeaponSounds != null && orbitWeaponSounds.containsKey(level)) orbitWeaponSounds.get(level).random().play();
    }

    public void playThunderboltWeaponSound(int level) {
        if (!muted && thunderboltWeaponSounds != null && thunderboltWeaponSounds.containsKey(level)) thunderboltWeaponSounds.get(level).random().play();
    }

    @Override
    public void dispose() {
        playerDeathSound.dispose();
        bombSound.dispose();
        gameOverSound.dispose();
        powerupSound.dispose();
        gemPickupSound.dispose();
        victoryFanfare.dispose();
        victoryLoop.dispose();
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
