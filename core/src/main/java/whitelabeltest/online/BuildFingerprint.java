package whitelabeltest.online;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Identifies the code and data a run was played on, so a validating server can re-simulate a
 *  replay with the same build. Bump GAME_BUILD whenever a code change could alter a replay. */
public final class BuildFingerprint {
    public static final String GAME_BUILD = "1.0.0";

    private static String cachedDataHash;

    private BuildFingerprint() {}

    /** SHA-256 over every data/*.json file listed in assets.txt (path + contents), excluding
     *  debug-only files. "unknown" if assets.txt can't be read. */
    public static synchronized String dataHash() {
        if (cachedDataHash != null) return cachedDataHash;
        try {
            FileHandle list = Gdx.files.internal("assets.txt");
            if (!list.exists()) return cachedDataHash = "unknown";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : list.readString("UTF-8").split("\\r?\\n")) {
                String path = line.trim();
                if (!path.startsWith("data/") || !path.endsWith(".json")) continue;
                if (path.startsWith("data/replays/") || path.equals("data/debug_savestates.json")) continue;
                FileHandle file = Gdx.files.internal(path);
                if (!file.exists()) continue;
                digest.update(path.getBytes(StandardCharsets.UTF_8));
                digest.update(file.readBytes());
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return cachedDataHash = hex.toString();
        } catch (Exception e) {
            return cachedDataHash = "unknown";
        }
    }
}
