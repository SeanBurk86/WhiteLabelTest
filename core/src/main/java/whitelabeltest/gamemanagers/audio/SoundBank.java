package whitelabeltest.gamemanagers.audio;

import com.badlogic.gdx.utils.Array;

/** One data/sounds.json entry: the sound files for a SoundType at a level (a random one plays). */
public class SoundBank {
    public SoundType type;
    public int level;
    public Array<String> sounds;

}
