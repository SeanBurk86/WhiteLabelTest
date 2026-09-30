package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Persisted graphics options (Options screen). Static because it's read deep inside stage loading;
 *  changes apply from the next stage load. shaderQuality scales back the heavy procedural backgrounds
 *  for weaker GPUs. */
public final class GraphicsSettings {
    public enum ShaderQuality {
        /** Full march step counts at ReducedResolutionRenderer.RESOLUTION_SCALE. */
        HIGH("High", null),
        /** Fewer, longer march steps (the shaders' QUALITY_MEDIUM define) at a lower resolution scale. */
        MEDIUM("Medium", "QUALITY_MEDIUM"),
        /** Integrated GPUs: fewer steps still (QUALITY_LOW), a fixed pixel budget instead of a screen
         *  fraction (Retina displays have many more pixels), and redrawn every other frame. */
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
    // Old on/off setting, migrated: "on" = MEDIUM.
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
