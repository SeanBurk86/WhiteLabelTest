package whitelabeltest.gamemanagers.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.math.MathUtils;

/** Persisted master/music/SFX volume (0..1), edited in Options. Separate from AudioManager because
 *  Options is reachable before a game session exists. */
public class AudioSettings {
    private static final String PREFS_NAME = "whitelabeltest-audio";
    private static final String MASTER_KEY = "masterVolume";
    private static final String MUSIC_KEY = "musicVolume";
    private static final String SFX_KEY = "sfxVolume";
    private static final float DEFAULT_VOLUME = 1f;

    private final Preferences prefs;
    private float masterVolume;
    private float musicVolume;
    private float sfxVolume;

    public AudioSettings() {
        prefs = Gdx.app.getPreferences(PREFS_NAME);
        masterVolume = MathUtils.clamp(prefs.getFloat(MASTER_KEY, DEFAULT_VOLUME), 0f, 1f);
        musicVolume = MathUtils.clamp(prefs.getFloat(MUSIC_KEY, DEFAULT_VOLUME), 0f, 1f);
        sfxVolume = MathUtils.clamp(prefs.getFloat(SFX_KEY, DEFAULT_VOLUME), 0f, 1f);
    }

    public float getMasterVolume() { return masterVolume; }
    public float getMusicVolume() { return musicVolume; }
    public float getSfxVolume() { return sfxVolume; }

    // Loudness is perceived roughly logarithmically, so the applied gain is the slider position cubed
    // (even-feeling steps). The stored/displayed slider value stays linear.
    private static final float GAIN_CURVE_EXPONENT = 3f;

    private static float toGain(float sliderPosition) {
        return (float) Math.pow(sliderPosition, GAIN_CURVE_EXPONENT);
    }

    /** The gain to apply to music (includes master volume). Use this, not getMusicVolume(). */
    public float getEffectiveMusicVolume() { return toGain(masterVolume) * toGain(musicVolume); }

    /** The gain to apply to sound effects (includes master volume). */
    public float getEffectiveSfxVolume() { return toGain(masterVolume) * toGain(sfxVolume); }

    public void setMasterVolume(float volume) {
        masterVolume = MathUtils.clamp(volume, 0f, 1f);
        prefs.putFloat(MASTER_KEY, masterVolume);
        prefs.flush();
    }

    public void setMusicVolume(float volume) {
        musicVolume = MathUtils.clamp(volume, 0f, 1f);
        prefs.putFloat(MUSIC_KEY, musicVolume);
        prefs.flush();
    }

    public void setSfxVolume(float volume) {
        sfxVolume = MathUtils.clamp(volume, 0f, 1f);
        prefs.putFloat(SFX_KEY, sfxVolume);
        prefs.flush();
    }

    public void resetToDefaults() {
        masterVolume = DEFAULT_VOLUME;
        musicVolume = DEFAULT_VOLUME;
        sfxVolume = DEFAULT_VOLUME;
        prefs.putFloat(MASTER_KEY, DEFAULT_VOLUME);
        prefs.putFloat(MUSIC_KEY, DEFAULT_VOLUME);
        prefs.putFloat(SFX_KEY, DEFAULT_VOLUME);
        prefs.flush();
    }
}
