package whitelabeltest.gamemanagers;

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

    public StageDefinition() {}
}
