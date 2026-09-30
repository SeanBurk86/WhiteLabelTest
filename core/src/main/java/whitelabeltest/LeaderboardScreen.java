package whitelabeltest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.online.LeaderboardClient;
import whitelabeltest.online.OnlineServices;

/** LEADERBOARD menu: the top scores plus the player's own standing. R / gamepad Y refreshes;
 *  Escape, Enter or gamepad A/B goes back. */
public class LeaderboardScreen implements Disposable {
    private static final int TOP_COUNT = 10;

    private final OnlineServices online;
    private final float worldWidth, worldHeight;
    private final BitmapFont font;
    private final BitmapFont titleFont;
    private final GlyphLayout layout = new GlyphLayout();

    private LeaderboardClient.LeaderboardPage page;
    private boolean loading;
    private boolean disposed;
    private boolean prevBackDown, prevRefreshDown;

    public LeaderboardScreen(float worldWidth, float worldHeight, OnlineServices online) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.online = online;

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/VT323-Regular.ttf"));
        FreeTypeFontParameter fontParams = new FreeTypeFontParameter();
        fontParams.size = 32;
        font = generator.generateFont(fontParams);
        titleFont = generator.generateFont(fontParams);
        generator.dispose();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.01171875f);
        titleFont.setUseIntegerPositions(false);
        titleFont.getData().setScale(0.0161953125f);

        // The button that opened this screen may still be held.
        Controller controller = Controllers.getCurrent();
        prevBackDown = controller != null && (controller.getButton(controller.getMapping().buttonA)
            || controller.getButton(controller.getMapping().buttonB));
        prevRefreshDown = controller != null && controller.getButton(controller.getMapping().buttonY);

        refresh();
    }

    private void refresh() {
        if (loading) return;
        loading = true;
        // Also a good moment to retry anything that failed earlier.
        online.retryRegistration();
        online.submitter.trySubmitPending();
        online.client.fetchLeaderboard(TOP_COUNT, online.account.getToken(), result -> {
            if (disposed) return;
            loading = false;
            page = result;
        });
    }

    /** True when the player backs out. */
    public boolean update(float delta) {
        boolean back = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
            || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.Z);
        boolean refresh = Gdx.input.isKeyJustPressed(Input.Keys.R);
        Controller controller = Controllers.getCurrent();
        if (controller != null) {
            boolean backDown = controller.getButton(controller.getMapping().buttonA)
                || controller.getButton(controller.getMapping().buttonB)
                || controller.getButton(controller.getMapping().buttonBack);
            boolean refreshDown = controller.getButton(controller.getMapping().buttonY);
            if (backDown && !prevBackDown) back = true;
            if (refreshDown && !prevRefreshDown) refresh = true;
            prevBackDown = backDown;
            prevRefreshDown = refreshDown;
        }
        if (refresh) refresh();
        return back;
    }

    public void draw(SpriteBatch batch) {
        float cx = worldWidth / 2f;
        titleFont.setColor(Color.WHITE);
        drawCentered(batch, titleFont, "LEADERBOARD", cx, worldHeight * 0.88f);

        String me = online.account.getUsername();
        if (loading && page == null) {
            font.setColor(Color.WHITE);
            drawCentered(batch, font, "Loading...", cx, worldHeight * 0.5f);
        } else if (page == null || page.status != LeaderboardClient.Status.OK) {
            font.setColor(Color.ORANGE);
            drawCentered(batch, font, "Couldn't reach the leaderboard.", cx, worldHeight * 0.5f);
        } else if (page.entries.size == 0) {
            font.setColor(Color.WHITE);
            drawCentered(batch, font, "No scores yet. Be the first!", cx, worldHeight * 0.5f);
        } else {
            float rowSpacing = worldHeight * 0.055f;
            float y = worldHeight * 0.76f;
            float left = worldWidth * 0.12f, right = worldWidth * 0.88f;
            for (LeaderboardClient.Entry entry : page.entries) {
                font.setColor(entry.username.equals(me) ? Color.YELLOW : Color.WHITE);
                font.draw(batch, String.format("%2d. %s", entry.rank, entry.username), left, y);
                drawRightAligned(batch, font, String.format("%,d", entry.score), right, y);
                y -= rowSpacing;
            }
        }

        font.setColor(Color.LIGHT_GRAY);
        if (me != null) {
            String standing;
            if (page != null && page.myRank > 0) standing = String.format("%s  #%d  %,d", me, page.myRank, page.myScore);
            else standing = me + "  - no score yet";
            drawCentered(batch, font, standing, cx, worldHeight * 0.17f);
        }
        if (online.submitter.hasPending()) {
            font.setColor(Color.ORANGE);
            drawCentered(batch, font, "A new best score is waiting to upload.", cx, worldHeight * 0.13f);
        }
        font.setColor(Color.LIGHT_GRAY);
        drawCentered(batch, font, "R - Refresh   Esc/Enter - Back", cx, worldHeight * 0.07f);
        font.setColor(Color.WHITE);
    }

    private void drawCentered(SpriteBatch batch, BitmapFont f, String text, float cx, float y) {
        layout.setText(f, text);
        f.draw(batch, layout, cx - layout.width / 2f, y);
    }

    private void drawRightAligned(SpriteBatch batch, BitmapFont f, String text, float right, float y) {
        layout.setText(f, text);
        f.draw(batch, layout, right - layout.width, y);
    }

    @Override
    public void dispose() {
        disposed = true;
        font.dispose();
        titleFont.dispose();
    }
}
