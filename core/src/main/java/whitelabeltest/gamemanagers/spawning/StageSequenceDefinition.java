package whitelabeltest.gamemanagers.spawning;

import com.badlogic.gdx.utils.Array;

/** A named run of stages from stage_sequences.json (e.g. "campaign", "tutorial"), drawn from the
 *  stage pool in stages.json. */
public class StageSequenceDefinition {
    public String id;
    public Array<String> stageIds;
    // Optional branching map. When set, stageIds is unused and each clear opens stage select.
    public StageMapDefinition stageMap;
    // Optional fixed loadout; null = the loadout picked on WeaponSelectScreen.
    public StartingLoadoutDefinition startingLoadout;

    public StageSequenceDefinition() {}
}
