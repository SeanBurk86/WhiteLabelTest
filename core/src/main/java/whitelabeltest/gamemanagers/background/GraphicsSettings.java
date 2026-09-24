package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Persisted graphics options, edited from the Options screen - see OptionsScreen. Static (unlike
 *  AudioSettings) because the only reader is the shader backgrounds' construction, deep inside stage
 *  loading; the value is read when a stage's background is built, so a change applies from the next
 *  stage load.
 *
 *  shaderQuality scales back the heaviest procedural backgrounds (stage 3's Mandelbulb, the tutorial's
 *  box tunnel) for weaker GPUs - see ShaderQuality for what each level does. */
public final class GraphicsSettings {
    public enum ShaderQuality {
        /** Full march step counts at ReducedResolutionRenderer.RESOLUTION_SCALE. */
        HIGH("High", null),
        /** Fewer, longer march steps (the shaders' QUALITY_MEDIUM define) at a lower resolution scale. */
        MEDIUM("Medium", "QUALITY_MEDIUM"),
        /** For integrated GPUs (e.g. an Intel Iris Pro MacBook): fewer steps still (QUALITY_LOW), a fixed
         *  pixel budget instead of a fraction of the screen - a Retina play area otherwise has several
         *  times the pixels of a 1080p one - and the shader redrawn only every other frame. */
        LOW("Low", "QUALITY_LOW");

        public final String label;
        /** The #define the shaders' fragment source gets for this level, or null for none. */
        public final String define;

        ShaderQuality(String label, String define) {
            this.label = label;
            this.define = define;
        }

        public ShaderQuality next() {
            ShaderQuality[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private static final String PREFS_NAME = "whitelabeltest-graphics";
    private static final String SHADER_QUALITY_KEY = "shaderQuality";
    // The earlier on/off setting - its "on" is today's MEDIUM.
    private static final String LEGACY_LOW_KEY = "lowShaderQuality";

    private static Preferences prefs;
    private static ShaderQuality shaderQuality;

    private GraphicsSettings() {}

    private static Preferences prefs() {
        if (prefs == null) {
            prefs = Gdx.app.getPreferences(PREFS_NAME);
            String stored = prefs.getString(SHADER_QUALITY_KEY, null);
            shaderQuality = prefs.getBoolean(LEGACY_LOW_KEY, false) ? ShaderQuality.MEDIUM : ShaderQuality.HIGH;
            if (stored != null) {
                try {
                    shaderQuality = ShaderQuality.valueOf(stored);
                } catch (IllegalArgumentException ignored) {
                    // unknown value from some other build - keep the default
                }
            }
        }
        return prefs;
    }

    public static ShaderQuality getShaderQuality() {
        prefs();
        return shaderQuality;
    }

    public static void setShaderQuality(ShaderQuality quality) {
        prefs().putString(SHADER_QUALITY_KEY, quality.name());
        prefs.remove(LEGACY_LOW_KEY);
        prefs.flush();
        shaderQuality = quality;
    }
}
