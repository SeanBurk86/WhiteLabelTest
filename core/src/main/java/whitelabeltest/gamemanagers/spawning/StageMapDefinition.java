package whitelabeltest.gamemanagers.spawning;

/** A branching stage-select map: columns left to right, nodes top to bottom, each a stage id or null
 *  for a locked placeholder. Connections are implied by the layout; see the README's "Stage map"
 *  section. */
public class StageMapDefinition {
    public String[][] columns;

    public StageMapDefinition() {}
}
