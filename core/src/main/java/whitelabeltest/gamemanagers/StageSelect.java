package whitelabeltest.gamemanagers;

import com.badlogic.gdx.utils.Array;

/** State of the "pick your next stage" map shown after a stage is cleared on a sequence with
 *  StageSequenceDefinition.chooseNextStage - see GameController.openStageSelect() and
 *  UIManager.drawStageSelect().
 *
 *  Every stage in the sequence is a Node on a path (in the sequence's own order - the path the map
 *  draws runs through them one after another, climbing from the first stage up toward the last). The
 *  ones already cleared are lit and can't be picked; the rest are the choices, and the cursor moves
 *  between those. Pure state - no rendering and no input - so it stays trivially deterministic for
 *  replays: GameController feeds it the same recorded left/right/confirm inputs either way. */
public class StageSelect {
    public static class Node {
        public final String id;
        public final String label;
        /** Position on the map, each 0..1 (x rightward, y upward) - see StageDefinition.mapX/mapY. */
        public final float x, y;
        public final boolean cleared;

        public Node(String id, String label, float x, float y, boolean cleared) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.cleared = cleared;
        }
    }

    private final Array<Node> nodes = new Array<>();
    private final Array<Node> choices = new Array<>();
    private int cursor;
    private float time;

    /** Adds the next stage along the path. Pass NaN for x/y to use the default layout (see
     *  defaultX()/defaultY()), which needs `index` and `count` - the stage's place along the path and
     *  how many stages the path has in total. */
    public void addNode(String id, String label, float x, float y, boolean cleared, int index, int count) {
        Node node = new Node(id, label,
            Float.isNaN(x) ? defaultX(index, count) : x,
            Float.isNaN(y) ? defaultY(index, count) : y, cleared);
        nodes.add(node);
        if (!cleared) choices.add(node);
    }

    /** Default layout for a stage with no explicit mapX/mapY: evenly spaced along a straight climb from the
     *  lower left to the upper right (the map draws the winding itself - see UIManager.drawStageSelect()). */
    public static float defaultX(int index, int count) {
        float t = count <= 1 ? 0.5f : index / (float) (count - 1);
        return 0.12f + 0.76f * t;
    }

    public static float defaultY(int index, int count) {
        float t = count <= 1 ? 0.5f : index / (float) (count - 1);
        return 0.04f + 0.92f * t;
    }

    public void tick(float delta) {
        time += delta;
    }

    /** Moves the cursor to the previous (-1) / next (+1) choice, wrapping around. */
    public void move(int direction) {
        if (choices.size == 0) return;
        cursor = ((cursor + direction) % choices.size + choices.size) % choices.size;
    }

    public Array<Node> getNodes() { return nodes; }
    public Node getSelected() { return choices.size > 0 ? choices.get(cursor) : null; }
    public int getChoiceCount() { return choices.size; }
    public float getTime() { return time; }
}
