package whitelabeltest.online;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.util.function.Consumer;

/** HTTP client for the leaderboard API (see docs/leaderboard-api.md). Requests run on libGDX's
 *  network threads; every callback is delivered on the render thread.
 *
 *  The server URL is -Dleaderboard.url, else "baseUrl" in assets/leaderboard.json, else
 *  http://localhost:8787 (the mock server's default). */
public class LeaderboardClient {
    private static final String DEFAULT_BASE_URL = "http://localhost:8787";
    private static final int TIMEOUT_MILLIS = 10000;

    public enum Status { OK, NAME_TAKEN, INVALID_NAME, NOT_PERSONAL_BEST, REJECTED, UNAUTHORIZED, NETWORK_ERROR, SERVER_ERROR }

    public static class RegisterResult {
        public Status status;
        public String playerId;
        public String token;
    }

    public static class SubmitResult {
        public Status status;
        public int rank;
        public int personalBest;
        public String error;
    }

    public static class Entry {
        public int rank;
        public String username;
        public int score;
    }

    public static class LeaderboardPage {
        public Status status;
        public final Array<Entry> entries = new Array<>();
        // The requesting player's standing, or rank 0 if they have no score yet.
        public int myRank;
        public int myScore;
    }

    private final String baseUrl;

    public LeaderboardClient() {
        this.baseUrl = resolveBaseUrl();
    }

    public String getBaseUrl() { return baseUrl; }

    private static String resolveBaseUrl() {
        String url = System.getProperty("leaderboard.url");
        if (url == null || url.isBlank()) {
            try {
                FileHandle config = Gdx.files.internal("leaderboard.json");
                if (config.exists()) url = new JsonReader().parse(config).getString("baseUrl", null);
            } catch (Exception e) {
                Gdx.app.error("LeaderboardClient", "Couldn't read leaderboard.json", e);
            }
        }
        if (url == null || url.isBlank()) url = DEFAULT_BASE_URL;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public void register(String username, Consumer<RegisterResult> callback) {
        JsonValue body = new JsonValue(JsonValue.ValueType.object);
        body.addChild("username", new JsonValue(username));
        send(Net.HttpMethods.POST, "/v1/players", null, body.toJson(JsonWriter.OutputType.json), (code, response) -> {
            RegisterResult result = new RegisterResult();
            if (code == 200 || code == 201) {
                result.status = Status.OK;
                result.playerId = response != null ? response.getString("playerId", null) : null;
                result.token = response != null ? response.getString("token", null) : null;
                if (result.playerId == null || result.token == null) result.status = Status.SERVER_ERROR;
            } else if (code == 409) {
                result.status = Status.NAME_TAKEN;
            } else if (code == 400 || code == 422) {
                result.status = Status.INVALID_NAME;
            } else {
                result.status = code < 0 ? Status.NETWORK_ERROR : Status.SERVER_ERROR;
            }
            callback.accept(result);
        });
    }

    /** @param requestBody a body built by ScoreSubmission (score, build info and encoded replay) */
    public void submitScore(String token, String requestBody, Consumer<SubmitResult> callback) {
        send(Net.HttpMethods.POST, "/v1/scores", token, requestBody, (code, response) -> {
            SubmitResult result = new SubmitResult();
            if (code == 200 || code == 201) {
                String status = response != null ? response.getString("status", "accepted") : "accepted";
                result.status = "not_a_personal_best".equals(status) ? Status.NOT_PERSONAL_BEST : Status.OK;
                if (response != null) {
                    result.rank = response.getInt("rank", 0);
                    result.personalBest = response.getInt("personalBest", 0);
                }
            } else if (code == 401 || code == 403) {
                result.status = Status.UNAUTHORIZED;
            } else if (code == 400 || code == 413 || code == 422) {
                result.status = Status.REJECTED;
                result.error = response != null ? response.getString("error", null) : null;
            } else {
                result.status = code < 0 ? Status.NETWORK_ERROR : Status.SERVER_ERROR;
            }
            callback.accept(result);
        });
    }

    /** @param token optional; when given, the page includes the player's own rank */
    public void fetchLeaderboard(int limit, String token, Consumer<LeaderboardPage> callback) {
        send(Net.HttpMethods.GET, "/v1/leaderboard?limit=" + limit, token, null, (code, response) -> {
            LeaderboardPage page = new LeaderboardPage();
            if (code != 200 || response == null) {
                page.status = code < 0 ? Status.NETWORK_ERROR : Status.SERVER_ERROR;
                callback.accept(page);
                return;
            }
            page.status = Status.OK;
            JsonValue entries = response.get("entries");
            if (entries != null) {
                for (JsonValue e = entries.child; e != null; e = e.next) {
                    Entry entry = new Entry();
                    entry.rank = e.getInt("rank", 0);
                    entry.username = e.getString("username", "?");
                    entry.score = e.getInt("score", 0);
                    page.entries.add(entry);
                }
            }
            JsonValue me = response.get("me");
            if (me != null && me.isObject()) {
                page.myRank = me.getInt("rank", 0);
                page.myScore = me.getInt("score", 0);
            }
            callback.accept(page);
        });
    }

    /** Called on the render thread. statusCode is -1 for a network failure; response is the
     *  parsed JSON body, or null if there was none or it wasn't JSON. */
    private interface ResponseHandler {
        void handle(int statusCode, JsonValue response);
    }

    private void send(String method, String path, String token, String body, ResponseHandler handler) {
        HttpRequestBuilder builder = new HttpRequestBuilder().newRequest()
            .method(method)
            .url(baseUrl + path)
            .timeout(TIMEOUT_MILLIS)
            .header("Accept", "application/json");
        if (body != null) builder.header("Content-Type", "application/json").content(body);
        if (token != null) builder.header("Authorization", "Bearer " + token);

        Gdx.net.sendHttpRequest(builder.build(), new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                int code = httpResponse.getStatus().getStatusCode();
                JsonValue json = parse(httpResponse.getResultAsString());
                Gdx.app.postRunnable(() -> handler.handle(code, json));
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.error("LeaderboardClient", method + " " + path + " failed: " + t.getMessage());
                Gdx.app.postRunnable(() -> handler.handle(-1, null));
            }

            @Override
            public void cancelled() {
                Gdx.app.postRunnable(() -> handler.handle(-1, null));
            }
        });
    }

    private static JsonValue parse(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return new JsonReader().parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
