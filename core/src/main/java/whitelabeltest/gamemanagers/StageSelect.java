package whitelabeltest.gamemanagers;

import com.badlogic.gdx.utils.Array;

/** State of the stage-select screen after a clear: the map, the path taken so far and a cursor over
 *  the next choices. Pure state (no rendering or input), so replays stay deterministic. */
public class StageSelect {
    private final StageMap map;
    private final Array<StageMap.Node> path;
    private final Array<StageMap.Node> choices;
    private int cursor;
    private float time;

    public StageSelect(StageMap map, Array<StageMap.Node> path) {
        this.map = map;
        this.path = path;
        this.choices = map.choicesAfter(path.peek());
    }

    public void tick(float delta) {
        time += delta;
    }

    /** Moves the cursor to the previous (-1, up the screen) / next (+1, down) choice, wrapping around. */
    public void move(int direction) {
        if (choices.size == 0) return;
        cursor = ((cursor + direction) % choices.size + choices.size) % choices.size;
    }

    public StageMap getMap() { return map; }
    public StageMap.Node getCurrent() { return path.peek(); }
    public boolean isVisited(StageMap.Node node) { return path.contains(node, true); }
    public boolean isChoice(StageMap.Node node) { return choices.contains(node, true); }
    public StageMap.Node getSelected() { return choices.size > 0 ? choices.get(cursor) : null; }
    public int getChoiceCount() { return choices.size; }
    public float getTime() { return time; }
}
