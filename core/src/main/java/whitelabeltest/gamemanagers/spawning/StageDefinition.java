package whitelabeltest.gamemanagers.spawning;

import com.badlogic.gdx.utils.Array;

public class StageDefinition {
    public static class BackgroundLayerDef {
        public String texture;
        // Alternative to texture (ignored if this is set and non-empty): a relay of several images
        // played one after another as this SAME layer scrolls, not several layers scrolling at once.
        // The first image scrolls through its own full height exactly like a single-texture layer
        // would (see ScrollingBackground.Layer/clampToTopOfImage) - normally that just freezes once
        // fully revealed, but a sequence instead hands off to the next image at that point (resetting
        // to its own top) and keeps scrolling, continuing through every entry in order. Only the
        // LAST image actually freezes at the end, same as a normal single-texture layer. Lets one
        // parallax layer read as a single long continuous piece of art across several separate files
        // instead of needing one giant image.
        public Array<String> textureSequence;
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
    // When true, the stage's ordinary backgroundLayers (NOT shaderBackground/backgroundVideo, which
    // this has no effect on) are drawn with a hue-rotating shader instead of their native colors -
    // see HueCycleShader. The rotation's period is set to exactly
    // SpawnScheduler.getBackgroundVideoTime() (see GameController.loadStage()), so it completes one
    // full cycle - ending back at the image's original colors - right as the boss video cuts in.
    public boolean hueCycleBackground = false;
    // When true, overlays PlayerFeedbackShader's analog "video feedback" trail (the player's own
    // sprite endlessly re-fed into a zooming/rotating/fading accumulation buffer) on top of whatever
    // this stage's background actually is - ordinary backgroundLayers, backgroundVideo/bossVideo, or
    // even a shaderBackground - rather than replacing it. Independent of shaderBackground/
    // hueCycleBackground: any combination of the three is valid, same "opt-in overlay" role as
    // hueCycleBackground, just applied after ALL background content instead of just the layer stack.
    public boolean playerFeedbackBackground = false;

    public StageDefinition() {}
}
