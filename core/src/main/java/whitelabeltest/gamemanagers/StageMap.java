package whitelabeltest.gamemanagers;

import com.badlogic.gdx.utils.Array;
import whitelabeltest.gamemanagers.spawning.StageMapDefinition;

/** The runtime graph built from a StageMapDefinition: one Node per entry, positioned and connected the
 *  way the reference sketch (assets/stage guide.png) shows - columns left to right, each column's nodes
 *  centred vertically, every node joined to whichever nodes in the next column sit next to it. Pure data,
 *  no rendering or input - see StageSelect for the "which one do I pick" state and GameController for
 *  how the player's route through it becomes the run's stage order. */
public class StageMap {
    public static class Node {
        public final int column, row;
        /** The stage played here, or null for a node that's on the map but not built yet. */
        public final String stageId;
        /** Position on the map, each 0..1 (x rightward, y upward - row 0 is the TOP of its column). */
        public final float x, y;
        /** Display name - set by GameController from the stage's own name; null when there's no stage. */
        public String label;
        /** The nodes ahead of this one it connects to, top to bottom. */
        public final Array<Node> next = new Array<>();

        Node(int column, int row, String stageId, float x, float y) {
            this.column = column;
            this.row = row;
            this.stageId = stageId;
            this.x = x;
            this.y = y;
        }

        public boolean hasStage() { return stageId != null; }
    }

    private final Array<Node> nodes = new Array<>();
    private final Node start;

    public StageMap(StageMapDefinition def) {
        if (def == null || def.columns == null || def.columns.length == 0 || def.columns[0].length != 1
            || isBlank(def.columns[0][0])) {
            throw new IllegalArgumentException("stageMap needs a first column with exactly one stage - that's where the run starts");
        }
        int columnCount = def.columns.length;
        int tallest = 1;
        for (String[] column : def.columns) tallest = Math.max(tallest, column.length);
        // One row step = the vertical distance between neighbours in the tallest column; a shorter column's
        // nodes are centred, so they sit half a step off the ones beside them.
        float rowStep = tallest <= 1 ? 0f : 1f / (tallest - 1);

        Array<Array<Node>> columns = new Array<>();
        for (int c = 0; c < columnCount; c++) {
            String[] entries = def.columns[c];
            Array<Node> column = new Array<>();
            for (int r = 0; r < entries.length; r++) {
                float x = columnCount <= 1 ? 0.5f : c / (float) (columnCount - 1);
                float y = 0.5f - (r - (entries.length - 1) / 2f) * rowStep;
                Node node = new Node(c, r, isBlank(entries[r]) ? null : entries[r], x, y);
                column.add(node);
                nodes.add(node);
            }
            columns.add(column);
        }
        start = columns.get(0).get(0);

        // Join each node to the next column's nodes that sit next to it (within half a row step).
        for (int c = 0; c + 1 < columnCount; c++) {
            for (Node from : columns.get(c)) {
                for (Node to : columns.get(c + 1)) {
                    if (Math.abs(from.y - to.y) <= rowStep * 0.5f + 1e-3f) from.next.add(to);
                }
            }
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    public Array<Node> getNodes() { return nodes; }
    public Node getStart() { return start; }

    /** The nodes the player can move on to from `node`: the ones ahead of it that actually have a stage. */
    public Array<Node> choicesAfter(Node node) {
        Array<Node> choices = new Array<>();
        for (Node next : node.next) if (next.hasStage()) choices.add(next);
        return choices;
    }
}
