package whitelabeltest.gamemanagers;

/** A linear playback cursor over a loaded ReplayData - see GameController.startReplay()/update(). */
public class ReplayPlayer {
    private final ReplayData data;
    private int cursor;

    public ReplayPlayer(ReplayData data) {
        this.data = data;
    }

    public boolean hasNext() { return cursor < data.frames.size; }
    public ReplayFrame next() { return data.frames.get(cursor++); }
    public long getSeed() { return data.rngSeed; }
    public ReplayData getData() { return data; }
    public float getProgress() { return data.frames.size == 0 ? 1f : cursor / (float) data.frames.size; }
}
