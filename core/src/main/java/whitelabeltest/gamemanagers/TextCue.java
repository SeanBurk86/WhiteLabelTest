package whitelabeltest.gamemanagers;

// A piece of on-screen text scheduled to appear for a window of time, with its own position,
// scale, and reveal effect. Defined entirely in spawn_schedule.json so new cues (banners,
// warnings, dialogue, etc.) can be added anywhere in a level without touching game code.
public class TextCue {
    public String text = "";
    // "static" (shown all at once), "typewriter" (revealed char by char), or "blinking".
    public String effect = "static";
    public float time = 0f;
    public float duration = Float.MAX_VALUE;

    // Draw position in world units. When centered is true, (x, y) is the center of the text's
    // bounding box instead of the baseline-left draw origin.
    public float x = 0f;
    public float y = 0f;
    public boolean centered = false;

    public float fontSize = 1f; // multiplier over the base UI font size
    public float charsPerSecond = 30f; // used when effect == "typewriter"
    public float blinksPerSecond = 4f; // used when effect == "blinking"

    // Real (never-frozen) time at which SpawnScheduler.update() first saw its own totalTime reach
    // `time` - -1 means "not yet reached". SpawnScheduler's totalTime freezes solid at an
    // unsatisfied GateCue (see its class doc), so a cue whose duration window would otherwise span
    // a gate must not be timed against that same freezable clock - it would stop mid-typewriter for
    // however long the player takes to clear the gate, then jump straight to wherever it left off.
    // Stamping the real time here once, on first reach, lets rendering measure this cue's own
    // elapsed display time (see UIManager.drawTextCues) against a clock that keeps advancing
    // through any later freeze, so once a cue starts playing it always finishes playing.
    public float triggeredAtRealTime = -1f;

    // True while SpawnScheduler is looping the text-blip sound for this cue's own typewriter reveal
    // - see SpawnScheduler.update()/AudioManager.loopTextCue()/stopTextCueLoop(). Only meaningful
    // for effect == "typewriter"; never set for any other effect. Cleared the moment the reveal
    // finishes (or the cue's own duration runs out first), and on any seek/reset so a rewind can't
    // leave the loop playing with nothing left tracking it.
    public boolean typingSoundActive = false;

    public TextCue() {}
}
