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

    public TextCue() {}
}
