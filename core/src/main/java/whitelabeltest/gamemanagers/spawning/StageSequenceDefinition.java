package whitelabeltest.gamemanagers.spawning;

import com.badlogic.gdx.utils.Array;

/** An explicit, named ordering of stage ids (each id resolved against AssetManager's stage pool -
 *  see StageDefinition) - decouples "which stages exist" (assets/data/stages.json) from "which
 *  stages play, and in what order, for a given run" (assets/data/stage_sequences.json). Lets
 *  alternate play modes (tutorial, practice, a boss-rush, etc.) reuse any subset/order of the same
 *  stage pool without duplicating stage content - see GameController's stageSequenceId
 *  constructor param. */
public class StageSequenceDefinition {
    public String id;
    public Array<String> stageIds;
    // When set, this sequence is a branching map instead of a fixed list: the run starts at the map's first
    // stage and every stage clear opens a stage-select screen to pick which connected stage to play next
    // (see StageMap/StageSelect). stageIds is then unused. null (the default) keeps the fixed order, as
    // every other sequence (tutorial, ...) has.
    public StageMapDefinition stageMap;
    // See StartingLoadoutDefinition - null (the default) means this sequence uses whatever loadout
    // the player picked on WeaponSelectScreen, same as "campaign" always has.
    public StartingLoadoutDefinition startingLoadout;

    public StageSequenceDefinition() {}
}
