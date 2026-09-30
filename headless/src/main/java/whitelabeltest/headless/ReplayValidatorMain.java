package whitelabeltest.headless;

import com.badlogic.gdx.utils.Json;
import whitelabeltest.gamemanagers.replay.ReplayData;
import whitelabeltest.gamemanagers.replay.ReplayResult;
import whitelabeltest.online.BuildFingerprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Command line: re-simulates each replay file and compares it with its recorded summary.
 *  `gradlew headless:run --args="<replay.json> ..."` (paths absolute, or relative to assets/).
 *  Exit code 0 when every replay reproduces, 1 otherwise. */
public final class ReplayValidatorMain {
    private ReplayValidatorMain() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: ReplayValidatorMain <replay.json> [more.json ...]");
            System.exit(2);
        }
        HeadlessReplayValidator.start();
        System.out.println("Build " + BuildFingerprint.GAME_BUILD + ", dataHash " + BuildFingerprint.dataHash());
        if (args[0].equals("--self-test")) {
            System.exit(selfTest(java.util.Arrays.copyOfRange(args, 1, args.length)));
        }
        int failures = 0;
        for (String arg : args) {
            Path file = Path.of(arg).toAbsolutePath();
            ReplayData replay = new Json().fromJson(ReplayData.class, Files.readString(file));
            String recorded = "score=" + replay.finalScore + " stagesReached=" + replay.stagesReached + " gameOver=" + replay.wasGameOver;
            if (!HeadlessReplayValidator.isSameBuild(replay)) {
                System.out.println("WARN  " + file.getFileName() + ": recorded on build " + replay.gameBuild
                    + " / " + replay.dataHash + ", results may differ");
            }
            long start = System.nanoTime();
            try {
                ReplayResult result = HeadlessReplayValidator.simulate(replay);
                long millis = (System.nanoTime() - start) / 1_000_000;
                boolean ok = result.matches(replay);
                if (!ok) failures++;
                System.out.println((ok ? "OK    " : "FAIL  ") + file.getFileName() + " (" + millis + " ms)");
                System.out.println("      recorded:  " + recorded);
                System.out.println("      simulated: " + result);
            } catch (RuntimeException e) {
                failures++;
                System.out.println("ERROR " + file.getFileName() + ": " + e);
                e.printStackTrace(System.out);
            }
        }
        HeadlessReplayValidator.stop();
        System.exit(failures == 0 ? 0 : 1);
    }

    /** `--self-test <replay.json> ...`: checks the simulation itself, whatever build the replays
     *  came from. Each replay runs twice headless and once with draw() every frame; all three must
     *  end in the same state (deterministic, and drawing doesn't change the game). */
    private static int selfTest(String[] files) throws IOException {
        int failures = 0;
        for (String arg : files) {
            Path file = Path.of(arg).toAbsolutePath();
            ReplayData replay = new Json().fromJson(ReplayData.class, Files.readString(file));
            ReplayResult first = HeadlessReplayValidator.simulate(replay);
            ReplayResult second = HeadlessReplayValidator.simulate(replay);
            ReplayResult drawn = HeadlessReplayValidator.simulateWithDrawing(replay);
            boolean ok = first.sameAs(second) && first.sameAs(drawn);
            if (!ok) failures++;
            System.out.println((ok ? "OK    " : "FAIL  ") + file.getFileName());
            System.out.println("      run 1:     " + first);
            if (!first.sameAs(second)) System.out.println("      run 2:     " + second);
            if (!first.sameAs(drawn)) System.out.println("      with draw: " + drawn);
        }
        HeadlessReplayValidator.stop();
        return failures == 0 ? 0 : 1;
    }
}
