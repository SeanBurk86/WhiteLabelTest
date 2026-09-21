package whitelabeltest.gamemanagers.spawning;

/** The branching stage-select map of a StageSequenceDefinition - see StageMap for how it's built
 *  and StageSelect/UIManager.drawStageSelect() for how it plays and looks.
 *
 *  Authored as columns, left to right, each listing its nodes top to bottom; every entry is a stage id
 *  (resolved like any other sequence's stageIds), or null for a node that's on the map but has no stage
 *  yet (shown locked, and never offered as a choice). Which nodes connect is implied by the layout:
 *  each node joins the nodes in the next column that sit vertically next to it, so a column with one
 *  more node than the last fans out into a triangle - two nodes ahead of every node - exactly like the
 *  reference sketch (assets/stage guide.png):
 *  <pre>
 *  "columns": [ ["stage1"], ["stage2", "stage3"], [null, null, null], [null, null, null, null] ]
 *  </pre>
 *  The first column must be a single node with a stage: that's where the run starts. Clearing a stage
 *  offers the stages of the nodes it connects to; a node with no stage-holding node ahead of it ends the
 *  run. */
public class StageMapDefinition {
    public String[][] columns;

    public StageMapDefinition() {}
}
