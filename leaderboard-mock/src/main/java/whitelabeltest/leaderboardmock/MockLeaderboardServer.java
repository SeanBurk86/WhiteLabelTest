package whitelabeltest.leaderboardmock;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import whitelabeltest.gamemanagers.replay.ReplayData;
import whitelabeltest.gamemanagers.replay.ReplayResult;
import whitelabeltest.headless.HeadlessReplayValidator;
import whitelabeltest.online.BuildFingerprint;
import whitelabeltest.online.UsernameGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** A local leaderboard server for development and testing, implementing docs/leaderboard-api.md.
 *  Submitted replays are re-simulated headlessly (HeadlessReplayValidator) and a score is only
 *  accepted if the simulation reproduces it. Everything runs on one thread, so a submission
 *  blocks other requests while it's checked (a few seconds for a long run).
 *
 *  Players are kept in mock-leaderboard.json (-Dmock.store). Run with
 *  `gradlew leaderboard-mock:run [-Pport=8787]`; the working directory must be assets/. */
public class MockLeaderboardServer {
    private static final int DEFAULT_PORT = 8787;
    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;
    private static final int MAX_LEADERBOARD_LIMIT = 100;
    private static final Path STORE_FILE = Path.of(System.getProperty("mock.store", "mock-leaderboard.json"));

    /** One registered player. Public fields for libGDX Json. */
    public static class PlayerRecord {
        public String playerId;
        public String username;
        public String token;
        public int bestScore;
        public long bestScoreAt;
    }

    public static class Store {
        public ArrayList<PlayerRecord> players = new ArrayList<>();
    }

    private final Store store;
    private final Map<String, PlayerRecord> byUsername = new HashMap<>();
    private final Map<String, PlayerRecord> byToken = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        HeadlessReplayValidator.start();
        System.out.println("Validating replays against build " + BuildFingerprint.GAME_BUILD + ", dataHash " + BuildFingerprint.dataHash());
        new MockLeaderboardServer().start(port);
    }

    private MockLeaderboardServer() throws IOException {
        store = Files.exists(STORE_FILE)
            ? new Json().fromJson(Store.class, Files.readString(STORE_FILE))
            : new Store();
        for (PlayerRecord p : store.players) {
            byUsername.put(p.username, p);
            byToken.put(p.token, p);
        }
    }

    private void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/v1/players", exchange -> handle(exchange, "POST", this::register));
        server.createContext("/v1/scores", exchange -> handle(exchange, "POST", this::submitScore));
        server.createContext("/v1/leaderboard", exchange -> handle(exchange, "GET", this::leaderboard));
        server.start();
        System.out.println("Mock leaderboard listening on http://localhost:" + port
            + " (" + store.players.size() + " players loaded from " + STORE_FILE.toAbsolutePath() + ")");
    }

    private record Response(int status, JsonValue body) {}

    private interface Handler {
        Response handle(HttpExchange exchange, byte[] body) throws IOException;
    }

    private void handle(HttpExchange exchange, String method, Handler handler) throws IOException {
        Response response;
        try {
            if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
                response = error(405, "method_not_allowed");
            } else {
                byte[] body = readBody(exchange.getRequestBody());
                response = body == null ? error(413, "too_large") : handler.handle(exchange, body);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response = error(500, "internal_error");
        }
        byte[] out = response.body().toJson(JsonWriter.OutputType.json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
        System.out.println(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " -> " + response.status());
    }

    // --- POST /v1/players ---

    private synchronized Response register(HttpExchange exchange, byte[] body) {
        JsonValue json = parse(body);
        String username = json != null ? json.getString("username", null) : null;
        if (!UsernameGenerator.isValid(username)) return error(400, "invalid_username");
        if (byUsername.containsKey(username)) return error(409, "username_taken");

        PlayerRecord player = new PlayerRecord();
        player.playerId = UUID.randomUUID().toString();
        player.username = username;
        player.token = newToken();
        store.players.add(player);
        byUsername.put(username, player);
        byToken.put(player.token, player);
        save();

        JsonValue result = object();
        result.addChild("playerId", new JsonValue(player.playerId));
        result.addChild("username", new JsonValue(player.username));
        result.addChild("token", new JsonValue(player.token));
        return new Response(201, result);
    }

    // --- POST /v1/scores ---

    private synchronized Response submitScore(HttpExchange exchange, byte[] body) {
        PlayerRecord player = authenticate(exchange);
        if (player == null) return error(401, "unauthorized");
        JsonValue json = parse(body);
        if (json == null) return error(400, "invalid_body");

        int score = json.getInt("score", -1);
        if (score < 0) return error(400, "invalid_score");
        if (!"gzip+base64".equals(json.getString("replayEncoding", null))) return error(400, "unsupported_replay_encoding");

        ReplayData replay;
        try {
            replay = decodeReplay(json.getString("replay", ""));
        } catch (Exception e) {
            return error(400, "invalid_replay");
        }
        // Cheap checks first, then the full re-simulation.
        if (replay.frames == null || replay.frames.size == 0) return error(422, "replay_empty");
        if (replay.finalScore != score) return error(422, "replay_mismatch");
        if (!HeadlessReplayValidator.isSameBuild(replay)) return error(422, "unsupported_build");
        ReplayResult simulated;
        try {
            simulated = HeadlessReplayValidator.simulate(replay);
        } catch (RuntimeException e) {
            System.out.println("  simulation failed: " + e);
            return error(422, "replay_invalid");
        }
        System.out.println("  claimed score=" + score + ", simulated " + simulated);
        if (simulated.containsSeek) return error(422, "replay_has_debug_seek");
        if (!simulated.matches(replay)) return error(422, "replay_mismatch");

        JsonValue result = object();
        if (score > player.bestScore) {
            player.bestScore = score;
            player.bestScoreAt = System.currentTimeMillis();
            save();
            result.addChild("status", new JsonValue("accepted"));
        } else {
            result.addChild("status", new JsonValue("not_a_personal_best"));
        }
        result.addChild("personalBest", new JsonValue(player.bestScore));
        result.addChild("rank", new JsonValue(rankOf(player)));
        return new Response(200, result);
    }

    // --- GET /v1/leaderboard?limit=N ---

    private synchronized Response leaderboard(HttpExchange exchange, byte[] body) {
        int limit = 10;
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("limit=")) {
                    try {
                        limit = Integer.parseInt(part.substring(6));
                    } catch (NumberFormatException ignored) {
                        // keep the default
                    }
                }
            }
        }
        limit = Math.max(1, Math.min(limit, MAX_LEADERBOARD_LIMIT));

        List<PlayerRecord> ranked = ranked();
        JsonValue entries = new JsonValue(JsonValue.ValueType.array);
        for (int i = 0; i < Math.min(limit, ranked.size()); i++) {
            PlayerRecord p = ranked.get(i);
            JsonValue entry = object();
            entry.addChild("rank", new JsonValue(i + 1));
            entry.addChild("username", new JsonValue(p.username));
            entry.addChild("score", new JsonValue(p.bestScore));
            entry.addChild("recordedAt", new JsonValue(p.bestScoreAt));
            entries.addChild(entry);
        }
        JsonValue result = object();
        result.addChild("entries", entries);

        PlayerRecord me = authenticate(exchange);
        if (me != null && me.bestScore > 0) {
            JsonValue mine = object();
            mine.addChild("rank", new JsonValue(rankOf(me)));
            mine.addChild("score", new JsonValue(me.bestScore));
            result.addChild("me", mine);
        } else {
            result.addChild("me", new JsonValue(JsonValue.ValueType.nullValue));
        }
        return new Response(200, result);
    }

    // --- helpers ---

    private List<PlayerRecord> ranked() {
        List<PlayerRecord> ranked = new ArrayList<>();
        for (PlayerRecord p : store.players) if (p.bestScore > 0) ranked.add(p);
        // Higher score first; ties go to whoever got there first.
        ranked.sort((a, b) -> a.bestScore != b.bestScore ? Integer.compare(b.bestScore, a.bestScore) : Long.compare(a.bestScoreAt, b.bestScoreAt));
        return ranked;
    }

    private int rankOf(PlayerRecord player) {
        return ranked().indexOf(player) + 1;
    }

    private PlayerRecord authenticate(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return byToken.get(header.substring("Bearer ".length()).trim());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ReplayData decodeReplay(String base64) throws IOException {
        byte[] gz = Base64.getDecoder().decode(base64);
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Json().fromJson(ReplayData.class, json);
        }
    }

    private void save() {
        try {
            Files.writeString(STORE_FILE, new Json(JsonWriter.OutputType.json).prettyPrint(store));
        } catch (IOException e) {
            System.err.println("Couldn't save " + STORE_FILE + ": " + e.getMessage());
        }
    }

    private static byte[] readBody(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int n; (n = in.read(buffer)) != -1; ) {
            total += n;
            if (total > MAX_BODY_BYTES) return null;
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static JsonValue parse(byte[] body) {
        try {
            return new JsonReader().parse(new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonValue object() {
        return new JsonValue(JsonValue.ValueType.object);
    }

    private static Response error(int status, String code) {
        JsonValue body = object();
        body.addChild("error", new JsonValue(code));
        return new Response(status, body);
    }
}
