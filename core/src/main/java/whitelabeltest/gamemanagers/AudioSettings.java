package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.math.MathUtils;

/** Persisted music/sound-effect volume levels (0..1), edited from the Options screen's Audio
 *  submenu - see OptionsScreen. Kept separate from AudioManager/ScrollingBackground (rather than
 *  living on either of them) because the Options screen is reachable from the start menu, before
 *  a game session - and with it, either of those classes - exists; both are constructed reading
 *  the current values here once a session starts. */
public class AudioSettings {
    private static final String PREFS_NAME = "whitelabeltest-audio";
    private static final String MUSIC_KEY = "musicVolume";
    private static final String SFX_KEY = "sfxVolume";
    private static final float DEFAULT_VOLUME = 1f;

    private final Preferences prefs;
    private float musicVolume;
    private float sfxVolume;

    public AudioSettings() {
        prefs = Gdx.app.getPreferences(PREFS_NAME);
        musicVolume = MathUtils.clamp(prefs.getFloat(MUSIC_KEY, DEFAULT_VOLUME), 0f, 1f);
        sfxVolume = MathUtils.clamp(prefs.getFloat(SFX_KEY, DEFAULT_VOLUME), 0f, 1f);
    }

    public float getMusicVolume() { return musicVolume; }
    public float getSfxVolume() { return sfxVolume; }

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
        musicVolume = DEFAULT_VOLUME;
        sfxVolume = DEFAULT_VOLUME;
        prefs.putFloat(MUSIC_KEY, DEFAULT_VOLUME);
        prefs.putFloat(SFX_KEY, DEFAULT_VOLUME);
        prefs.flush();
    }
}
