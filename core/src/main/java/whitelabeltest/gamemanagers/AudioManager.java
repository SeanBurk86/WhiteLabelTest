package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import whitelabeltest.player.weapons.SoundType;

public class AudioManager implements Disposable {
    private final ObjectMap<Integer, Array<Sound>> explosionSounds;
    private final ObjectMap<Integer, Array<Sound>> basicWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> thunderWhipWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> waveBlastWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> homingWeaponSounds;
    private final ObjectMap<Integer, Array<Sound>> orbitWeaponSounds;

    public AudioManager() {
        Json json = new Json();
        basicWeaponSounds = new ObjectMap<>();
        thunderWhipWeaponSounds = new ObjectMap<>();
        waveBlastWeaponSounds = new ObjectMap<>();
        homingWeaponSounds = new ObjectMap<>();
        orbitWeaponSounds = new ObjectMap<>();
        explosionSounds = new ObjectMap<>();
        @SuppressWarnings("unchecked")
        Array<SoundBank> soundBanks = json.fromJson(Array.class, SoundBank.class, Gdx.files.internal("sounds.json"));
        for(SoundBank sBank : soundBanks) {
            if(sBank.type == SoundType.BasicWeapon) {
                populateSounds(sBank, basicWeaponSounds);
            }
            if(sBank.type == SoundType.ThunderWhipWeapon) {
                populateSounds(sBank, thunderWhipWeaponSounds);
            }
            if(sBank.type == SoundType.OrbitWeapon) {
                populateSounds(sBank, orbitWeaponSounds);
            }
            if(sBank.type == SoundType.WaveBlastWeapon) {
                populateSounds(sBank, waveBlastWeaponSounds);
            }
            if(sBank.type == SoundType.HomingWeapon) {
                populateSounds(sBank, homingWeaponSounds);
            }
            if(sBank.type == SoundType.Explosion) {
                populateSounds(sBank, explosionSounds);
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

    public void playExplosion() {
        if (explosionSounds != null) explosionSounds.get(1).random().play();
    }

    public void playBasicWeaponSound(int level) {
        if (basicWeaponSounds != null && basicWeaponSounds.containsKey(level)) basicWeaponSounds.get(level).random().play();
    }

    public void playThunderWhipWeaponSound(int level) {
        if (thunderWhipWeaponSounds != null && thunderWhipWeaponSounds.containsKey(level)) thunderWhipWeaponSounds.get(level).random().play();
    }

    public void playWaveBlastWeaponSound(int level) {
        if (waveBlastWeaponSounds != null && waveBlastWeaponSounds.containsKey(level)) waveBlastWeaponSounds.get(level).random().play();
    }

    public void playHomingWeaponSound(int level) {
        if (homingWeaponSounds != null && homingWeaponSounds.containsKey(level)) homingWeaponSounds.get(level).random().play();
    }

    public void playOrbitWeaponSound(int level) {
        if (orbitWeaponSounds != null && orbitWeaponSounds.containsKey(level)) orbitWeaponSounds.get(level).random().play();
    }

    @Override
    public void dispose() {
        if (explosionSounds != null) {
            for(int i = 1; i < 5; i++) {
                for(Sound s : explosionSounds.get(i)) s.dispose();
            }
        }
        if (basicWeaponSounds != null) {
            for(int i = 1; i < 5; i++) {
                for(Sound s : basicWeaponSounds.get(i)) s.dispose();
            }
        };
    }
}
