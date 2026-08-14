package whitelabeltest.gamemanagers;

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
    // See StartingLoadoutDefinition - null (the default) means this sequence uses whatever loadout
    // the player picked on WeaponSelectScreen, same as "campaign" always has.
    public StartingLoadoutDefinition startingLoadout;

    public StageSequenceDefinition() {}
}
