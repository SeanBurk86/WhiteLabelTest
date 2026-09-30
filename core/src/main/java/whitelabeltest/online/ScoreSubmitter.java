package whitelabeltest.online;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import whitelabeltest.gamemanagers.replay.ReplayData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/** Posts a finished run's score and replay to the leaderboard when it beats the player's best.
 *
 *  The best unsent run is kept in ~/WhiteLabelTest/pending_score.json until the server answers,
 *  so a run finished offline (or just before quitting) is sent on a later launch. Only one run is
 *  kept, since only a personal best matters. */
public class ScoreSubmitter {
    private static final String PENDING_PATH = "WhiteLabelTest/pending_score.json";

    private final PlayerAccount account;
    private final LeaderboardClient client;
    // Called when the server no longer accepts the player's token.
    private final Runnable onUnauthorized;
    private boolean inFlight;

    public ScoreSubmitter(PlayerAccount account, LeaderboardClient client, Runnable onUnauthorized) {
        this.account = account;
        this.client = client;
        this.onUnauthorized = onUnauthorized;
    }

    /** Called when a run's recording ends (see GameController.setRunFinishedListener). */
    public void onRunFinished(ReplayData replay) {
        if (replay.finalScore <= 0) return;
        int best = Math.max(account.getBestSubmittedScore(), pendingScore());
        if (replay.finalScore <= best) return;
        try {
            pendingFile().writeString(buildRequestBody(replay), false, "UTF-8");
        } catch (Exception e) {
            Gdx.app.error("ScoreSubmitter", "Couldn't save the pending score", e);
            return;
        }
        trySubmitPending();
    }

    public boolean hasPending() {
        return pendingFile().exists();
    }

    /** Sends the pending run, if there is one and the player is registered. */
    public void trySubmitPending() {
        if (inFlight || !account.isRegistered()) return;
        FileHandle file = pendingFile();
        if (!file.exists()) return;
        String body;
        try {
            body = file.readString("UTF-8");
        } catch (Exception e) {
            Gdx.app.error("ScoreSubmitter", "Couldn't read the pending score", e);
            return;
        }
        int score = scoreOf(body);
        inFlight = true;
        client.submitScore(account.getToken(), body, result -> {
            inFlight = false;
            switch (result.status) {
                case OK:
                case NOT_PERSONAL_BEST:
                    account.setBestSubmittedScore(Math.max(account.getBestSubmittedScore(), Math.max(score, result.personalBest)));
                    deletePendingIfScore(score);
                    // A better run may have been queued while this one was in flight.
                    trySubmitPending();
                    break;
                case REJECTED:
                    Gdx.app.error("ScoreSubmitter", "Score " + score + " rejected by the server: " + result.error);
                    deletePendingIfScore(score);
                    trySubmitPending();
                    break;
                case UNAUTHORIZED:
                    // Keep the score; re-registering the name gets a new token, then it's sent.
                    onUnauthorized.run();
                    break;
                default:
                    // Network or server error: keep it and retry later.
                    Gdx.app.log("ScoreSubmitter", "Score " + score + " not sent (" + result.status + "); will retry.");
                    break;
            }
        });
    }

    private void deletePendingIfScore(int score) {
        FileHandle file = pendingFile();
        if (file.exists() && pendingScore() == score) file.delete();
    }

    private int pendingScore() {
        FileHandle file = pendingFile();
        if (!file.exists()) return 0;
        try {
            return scoreOf(file.readString("UTF-8"));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int scoreOf(String body) {
        try {
            return new JsonReader().parse(body).getInt("score", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private static FileHandle pendingFile() {
        return Gdx.files.external(PENDING_PATH);
    }

    /** The POST /v1/scores body: the claimed result plus the replay as gzipped, base64 JSON. */
    public static String buildRequestBody(ReplayData replay) throws IOException {
        String replayJson = new Json(JsonWriter.OutputType.json).toJson(replay);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(replayJson.getBytes(StandardCharsets.UTF_8));
        }

        JsonValue body = new JsonValue(JsonValue.ValueType.object);
        body.addChild("score", new JsonValue(replay.finalScore));
        body.addChild("stagesReached", new JsonValue(replay.stagesReached));
        body.addChild("wasGameOver", new JsonValue(replay.wasGameOver));
        body.addChild("gameBuild", new JsonValue(replay.gameBuild));
        body.addChild("dataHash", new JsonValue(replay.dataHash));
        body.addChild("replayEncoding", new JsonValue("gzip+base64"));
        body.addChild("replay", new JsonValue(Base64.getEncoder().encodeToString(bytes.toByteArray())));
        return body.toJson(JsonWriter.OutputType.json);
    }
}
