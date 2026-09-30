package whitelabeltest.gamemanagers.spawning;

/** Display-only letter grade for a cleared stage (see GameController.computeRank()). Doesn't affect
 *  the score. */
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
