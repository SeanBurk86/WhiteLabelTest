package whitelabeltest.online;

import com.badlogic.gdx.Gdx;

import java.util.function.Consumer;

/** The leaderboard account, client and score submitter, created once by Main. */
public class OnlineServices {
    public final PlayerAccount account;
    public final LeaderboardClient client;
    public final ScoreSubmitter submitter;
    private boolean registering;

    public OnlineServices() {
        account = new PlayerAccount();
        client = new LeaderboardClient();
        submitter = new ScoreSubmitter(account, client, () -> {
            // The server lost or revoked the account: drop the token and register the name again
            // (if it's been taken since, the player is asked to pick a new one).
            account.setPendingUsername(account.getUsername());
            retryRegistration();
        });
    }

    /** On startup: finishes an interrupted registration, or sends a pending score. */
    public void start() {
        if (account.isRegistered()) submitter.trySubmitPending();
        else retryRegistration();
    }

    /** Registers a name picked on the username screen. If the server can't be reached, the name
     *  is kept locally and registered later (see retryRegistration()). */
    public void chooseUsername(String name, Consumer<LeaderboardClient.Status> callback) {
        registering = true;
        client.register(name, result -> {
            registering = false;
            switch (result.status) {
                case OK:
                    account.setRegistered(name, result.playerId, result.token);
                    submitter.trySubmitPending();
                    break;
                case NETWORK_ERROR:
                case SERVER_ERROR:
                    account.setPendingUsername(name);
                    break;
                default:
                    break;
            }
            callback.accept(result.status);
        });
    }

    /** Registers a name that was picked while offline. If the server now says it's taken or
     *  invalid, the name is cleared and Main shows the username screen again. */
    public void retryRegistration() {
        String name = account.getUsername();
        if (name == null || account.isRegistered() || registering) return;
        registering = true;
        client.register(name, result -> {
            registering = false;
            switch (result.status) {
                case OK:
                    account.setRegistered(name, result.playerId, result.token);
                    submitter.trySubmitPending();
                    break;
                case NAME_TAKEN:
                case INVALID_NAME:
                    Gdx.app.log("OnlineServices", "Pending name " + name + " can't be registered (" + result.status + ")");
                    account.clearUsername();
                    break;
                default:
                    break;
            }
        });
    }
}
