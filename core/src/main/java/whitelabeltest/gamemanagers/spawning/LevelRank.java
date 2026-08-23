package whitelabeltest.gamemanagers.spawning;

/** Letter grade summarizing a level-complete run - computed in
 *  GameController.computeRank() from the same stats UIManager.drawLevelComplete's MISSION_LOG
 *  rows already show (kill rate, peak chain, boss takedown speed, bombs/lives preserved), and
 *  displayed alongside them as the mockup's side "TACTICAL RANK" badge card. Purely a display
 *  flourish - it doesn't feed back into the score. */
public enum LevelRank {
    S("PERFECT CLEAR"),
    A("EXCELLENT CLEAR"),
    B("SOLID CLEAR"),
    C("ROUGH CLEAR"),
    D("SLOPPY CLEAR");

    public final String subtitle;

    LevelRank(String subtitle) {
        this.subtitle = subtitle;
    }
}
