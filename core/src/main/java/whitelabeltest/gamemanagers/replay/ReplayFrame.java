package whitelabeltest.gamemanagers.replay;

/** One recorded/replayed simulation tick's worth of gameplay input - see ReplayRecorder.record()/
 *  InputManager.update(ReplayFrame). confirmJustPressed reuses InputManager.isRestartJustPressed()'s
 *  value since that same key/button both restarts on the game-over screen and confirms "continue to
 *  next stage" on the STAGE CLEAR screen (see GameController.handleLevelCompleteInput) - recording
 *  it lets a multi-stage replay trigger advanceToNextStage() at the right frame during playback.
 *  Deliberately excludes debug-menu input (dev tool, not part of "the run") and isQuitJustPressed()
 *  (playback must never call Gdx.app.exit() on autopilot - it just stops when frames run out). */
public class ReplayFrame {
    public float delta;
    public float moveX;
    public float moveY;
    public boolean shooting;
    public boolean bombJustPressed;
    public boolean weaponSwitchJustPressed;
    public boolean hyperAttackHeld;
    public boolean confirmJustPressed;
    // NaN (default) = an ordinary tick. Otherwise this frame represents a debug-menu seek/bookmark
    // jump (see GameController.seekToTime()) - an instantaneous clock jump outside the normal
    // delta-accumulation model, so it can't be reconstructed from delta alone and needs recording
    // as its own event. All other fields are meaningless on a seek frame.
    public float seekToTime = Float.NaN;

    public ReplayFrame() {}
}
