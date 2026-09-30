package whitelabeltest.online;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** The player's leaderboard identity, persisted in Preferences "whitelabeltest-account".
 *
 *  States: no name yet (the username screen must be shown), a chosen name not yet registered
 *  (the server was unreachable; registration is retried), and registered (has a player id and
 *  token). */
public class PlayerAccount {
    private static final String PREFS_NAME = "whitelabeltest-account";
    private static final String USERNAME_KEY = "username";
    private static final String PLAYER_ID_KEY = "playerId";
    private static final String TOKEN_KEY = "token";
    private static final String BEST_SUBMITTED_KEY = "bestSubmittedScore";

    private final Preferences prefs;

    public PlayerAccount() {
        prefs = Gdx.app.getPreferences(PREFS_NAME);
    }

    public String getUsername() {
        String name = prefs.getString(USERNAME_KEY, null);
        return name == null || name.isEmpty() ? null : name;
    }

    public String getPlayerId() {
        String id = prefs.getString(PLAYER_ID_KEY, null);
        return id == null || id.isEmpty() ? null : id;
    }

    public String getToken() {
        String token = prefs.getString(TOKEN_KEY, null);
        return token == null || token.isEmpty() ? null : token;
    }

    public boolean needsUsername() {
        return getUsername() == null;
    }

    public boolean isRegistered() {
        return getUsername() != null && getToken() != null;
    }

    /** Stores a picked name before registration, so it survives being offline. */
    public void setPendingUsername(String username) {
        prefs.putString(USERNAME_KEY, username);
        prefs.remove(PLAYER_ID_KEY);
        prefs.remove(TOKEN_KEY);
        prefs.flush();
    }

    public void setRegistered(String username, String playerId, String token) {
        prefs.putString(USERNAME_KEY, username);
        prefs.putString(PLAYER_ID_KEY, playerId);
        prefs.putString(TOKEN_KEY, token);
        prefs.flush();
    }

    /** The server rejected the name (taken or invalid): the player must pick again. */
    public void clearUsername() {
        prefs.remove(USERNAME_KEY);
        prefs.remove(PLAYER_ID_KEY);
        prefs.remove(TOKEN_KEY);
        prefs.flush();
    }

    /** The best score the server has accepted for this player (0 if none). */
    public int getBestSubmittedScore() {
        return prefs.getInteger(BEST_SUBMITTED_KEY, 0);
    }

    public void setBestSubmittedScore(int score) {
        prefs.putInteger(BEST_SUBMITTED_KEY, score);
        prefs.flush();
    }
}
