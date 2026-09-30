package whitelabeltest.gamemanagers.spawning;

import com.badlogic.gdx.utils.Array;

/** One stage from stages.json. See the README's "Stages" section. */
public class StageDefinition {
    public static class BackgroundLayerDef {
        public String texture;
        // Alternative to texture: images played one after another in this same layer, each handing
        // off to the next once fully scrolled. Only the last one freezes at its end.
        public Array<String> textureSequence;
        // NaN = ScrollingBackground.DEFAULT_SCROLL_SPEED (0 is a valid static speed).
        public float scrollSpeed = Float.NaN;

        public BackgroundLayerDef() {}
    }

    public String id;
    public String name;
    // Legacy time-based schedule; only used when triggerFile is null.
    public String spawnSchedule;
    // The stage's trigger file (see TriggerManager). When set, spawnSchedule is ignored.
    public String triggerFile = null;
    // Optional overrides of ScrollingBackground's defaults; null = engine default.
    public Float kaleidoscopeTransitionTime;
    // Distance by which the "kaleidoscope" background has faded from monochrome to color.
    // null = the boss trigger's distance.
    public Float kaleidoscopeColorFadeDistance;
    // Distance at which the "kaleidoscope" background swaps from phosphene to tentacles tunnel.
    // null = a few units before the boss trigger.
    public Float kaleidoscopeTransitionDistance;
    // Distance at which the "mandelbulb" background starts diving into the bulb.
    // null = a few units before the boss trigger.
    public Float mandelbulbDiveDistance;
    public Float groundScrollSpeed;
    public String music;
    public Array<BackgroundLayerDef> backgroundLayers;
    public String bossVideo;
    // Looping video background from stage start (bossVideo instead cuts in later on a cue).
    public String backgroundVideo;
    // Full-screen procedural shader background: "boxTunnel", "kaleidoscope" or "mandelbulb". Takes
    // priority over backgroundVideo and backgroundLayers.
    public String shaderBackground = null;
    // Hue-rotates backgroundLayers (see HueCycleShader), completing one cycle as the boss video starts.
    public boolean hueCycleBackground = false;
    // Overlays PlayerFeedbackShader's video-feedback trail of the player on top of any background.
    public boolean playerFeedbackBackground = false;

    public StageDefinition() {}
}
