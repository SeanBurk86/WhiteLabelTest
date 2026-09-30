package whitelabeltest.gamemanagers;

// On-screen text shown by a trigger or schedule, with its own position, scale and reveal effect.
public class TextCue {
    public String text = "";
    // "static" (shown all at once), "typewriter" (revealed char by char), or "blinking".
    public String effect = "static";
    public float time = 0f;
    public float duration = Float.MAX_VALUE;

    // Draw position in world units. When centered, (x, y) is the center of the text block instead of
    // its top-left draw origin.
    public float x = 0f;
    public float y = 0f;
    public boolean centered = false;

    public float fontSize = 1f; // multiplier over the base UI font size
    public float charsPerSecond = 30f; // typewriter only
    public float blinksPerSecond = 4f; // blinking only

    // Real (never-frozen) time the cue appeared, or -1 if not yet. Display timing is measured against
    // this so a gate freezing the stage clock doesn't stall a reveal mid-way.
    public float triggeredAtRealTime = -1f;

    // True while the typewriter typing sound is looping for this cue. Cleared when the reveal ends
    // and on any seek/reset.
    public boolean typingSoundActive = false;

    // True once the player has confirmed past this cue; hides it immediately.
    public boolean dismissed = false;

    // True: stays on screen past `duration` (with the "press to continue" hint) until dismissed. Set
    // from Trigger.requireConfirm; the per-cue form of ScheduleFile.textCuesRequireConfirm.
    public boolean requireConfirm = false;

    public TextCue() {}
}
