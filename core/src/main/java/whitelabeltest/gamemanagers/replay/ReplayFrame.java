package whitelabeltest.gamemanagers.replay;

/** One tick of recorded gameplay input. confirmJustPressed is the RESTART press, which also
 *  advances past stage clear and stage select. Debug and QUIT input are never recorded. */
public class ReplayFrame {
    public float delta;
    public float moveX;
    public float moveY;
    public boolean shooting;
    public boolean bombJustPressed;
    public boolean weaponSwitchJustPressed;
    public boolean hyperAttackHeld;
    public boolean confirmJustPressed;
    // NaN = ordinary tick; otherwise a debug seek to this time/distance (other fields unused).
    public float seekToTime = Float.NaN;

    public ReplayFrame() {}
}
