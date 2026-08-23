package whitelabeltest.gamemanagers.spawning;

import com.badlogic.gdx.utils.Array;

public class StageDefinition {
    public static class BackgroundLayerDef {
        public String texture;
        // NaN = unset -> ScrollingBackground.DEFAULT_SCROLL_SPEED, since 0 is a valid (static) speed.
        public float scrollSpeed = Float.NaN;

        public BackgroundLayerDef() {}
    }

    public String id;
    public String name;
    public String spawnSchedule;
    public String music;
    public Array<BackgroundLayerDef> backgroundLayers;
    public String bossVideo;
    // Stage-long looping video used as the background from the moment the stage loads - see
    // ScrollingBackground.backgroundVideoFile. Unlike bossVideo (which cuts in later on a
    // spawn-schedule cue), this plays immediately, so a stage using it typically leaves
    // backgroundLayers empty (or minimal) rather than layering it under a parallax image.
    public String backgroundVideo;
    // When set, the background is a full-screen procedural shader effect (see
    // ScrollingBackground.createShaderBackground/BackgroundShader) instead of backgroundVideo/
    // backgroundLayers - same "plays immediately, covers the whole screen" role as backgroundVideo,
    // just procedural rather than a decoded video file. Takes priority over backgroundVideo if both
    // are somehow set. null (the default) means no shader background; otherwise must be one of the
    // ids createShaderBackground() recognizes ("boxTunnel", "kaleidoscope").
    public String shaderBackground = null;

    public StageDefinition() {}
}
