package whitelabeltest.gamemanagers.audio;

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

    // Perceived loudness scales roughly logarithmically with linear amplitude, so feeding a slider's
    // raw 0..1 position straight into Sound.play()/Music.setVolume() (linear gain) makes most of the
    // slider's travel sound bunched up near the top, with all the useful low-volume range crammed
    // into the first ~10-20%. Cubing the position before it's applied as gain approximates that log
    // response instead, so equal slider steps feel like more even steps in perceived loudness. Only
    // the applied gain is curved - the stored/displayed slider position (see get*Volume()) stays
    // linear so the Options screen's 10%-per-press steps and label are unaffected.
    private static final float GAIN_CURVE_EXPONENT = 3f;

    private static float toGain(float sliderPosition) {
        return (float) Math.pow(sliderPosition, GAIN_CURVE_EXPONENT);
    }

    /** Music volume actually applied to any Music instance, factoring in masterVolume - all
     *  playback code (AudioManager, StartScreen) should use this instead of getMusicVolume() so
     *  master volume affects every music track without each caller re-deriving the product. */
    public float getEffectiveMusicVolume() { return toGain(masterVolume) * toGain(musicVolume); }

    /** Sound-effect volume actually applied to any Sound.play() call, factoring in masterVolume -
     *  see getEffectiveMusicVolume(). */
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
