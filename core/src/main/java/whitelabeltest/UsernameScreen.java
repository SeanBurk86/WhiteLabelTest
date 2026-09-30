package whitelabeltest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.gamemanagers.audio.AudioSettings;
import whitelabeltest.online.LeaderboardClient;
import whitelabeltest.online.OnlineServices;
import whitelabeltest.online.UsernameGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** First launch: the player picks a leaderboard name from generated candidates (no free text), or
 *  asks for new ones. The pick is registered with the server; a taken name offers new choices, and
 *  if the server can't be reached the name is kept and registered later. */
public class UsernameScreen implements Disposable {
    private static final int CANDIDATE_COUNT = 6;
    private static final String REROLL_LABEL = "GENERATE NEW NAMES";
    private static final float WELCOME_SECONDS = 1.5f;

    private static final Color SELECTION_BOX_COLOR = new Color(0.35f, 1f, 0.55f, 1f);
    private static final float SELECTION_BOX_PADDING_X = 0.15f;
    private static final float SELECTION_BOX_PADDING_Y = 0.08f;
    private static final float SELECTION_BOX_THICKNESS = 0.025f;

    private enum Phase { CHOOSING, REGISTERING, OFFLINE_NOTICE, WELCOME }

    private final OnlineServices online;
    private final AudioSettings audioSettings;
    private final float worldWidth, worldHeight;
    private final BitmapFont font;
    private final BitmapFont titleFont;
    private final GlyphLayout layout = new GlyphLayout();
    private final Texture whitePixel;
    private final Sound selectSound;
    private final Sound confirmSound;
    private final Random random = new Random();
    // Names the server reported as taken this session, never offered again.
    private final Set<String> takenNames = new HashSet<>();

    private Phase phase = Phase.CHOOSING;
    private List<String> candidates;
    private int selectedIndex;
    private String chosenName;
    private String message;
    private float welcomeTimer;
    private boolean prevDpadUpDown, prevDpadDownDown, prevConfirmDown;

    public UsernameScreen(float worldWidth, float worldHeight, AudioSettings audioSettings, OnlineServices online) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.audioSettings = audioSettings;
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

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        selectSound = Gdx.audio.newSound(Gdx.files.internal("audio/menu/selectsoundmenu.mp3"));
        confirmSound = Gdx.audio.newSound(Gdx.files.internal("audio/menu/confirmsoundmenu.mp3"));

        candidates = UsernameGenerator.candidates(CANDIDATE_COUNT, takenNames, random);

        // A button still held from before this screen isn't a new press.
        Controller controller = Controllers.getCurrent();
        prevDpadUpDown = controller != null && controller.getButton(controller.getMapping().buttonDpadUp);
        prevDpadDownDown = controller != null && controller.getButton(controller.getMapping().buttonDpadDown);
        prevConfirmDown = controller != null && controller.getButton(controller.getMapping().buttonA);
    }

    /** True once a name has been picked (registered, or saved for later registration). */
    public boolean update(float delta) {
        int move = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) move = -1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) move = 1;
        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.Z);
        Controller controller = Controllers.getCurrent();
        if (controller != null) {
            boolean up = controller.getButton(controller.getMapping().buttonDpadUp);
            boolean down = controller.getButton(controller.getMapping().buttonDpadDown);
            boolean a = controller.getButton(controller.getMapping().buttonA);
            if (up && !prevDpadUpDown) move = -1;
            if (down && !prevDpadDownDown) move = 1;
            if (a && !prevConfirmDown) confirm = true;
            prevDpadUpDown = up;
            prevDpadDownDown = down;
            prevConfirmDown = a;
        }

        switch (phase) {
            case CHOOSING:
                if (move != 0) {
                    int rows = candidates.size() + 1;
                    selectedIndex = (selectedIndex + move + rows) % rows;
                    selectSound.play(audioSettings.getEffectiveSfxVolume());
                }
                if (confirm) {
                    confirmSound.play(audioSettings.getEffectiveSfxVolume());
                    if (selectedIndex == candidates.size()) reroll(null);
                    else register(candidates.get(selectedIndex));
                }
                return false;
            case OFFLINE_NOTICE:
                return confirm;
            case WELCOME:
                welcomeTimer += delta;
                return welcomeTimer >= WELCOME_SECONDS;
            default:
                return false;
        }
    }

    private void register(String name) {
        chosenName = name;
        message = null;
        phase = Phase.REGISTERING;
        online.chooseUsername(name, this::onRegistered);
    }

    private void onRegistered(LeaderboardClient.Status status) {
        switch (status) {
            case OK:
                phase = Phase.WELCOME;
                welcomeTimer = 0f;
                break;
            case NAME_TAKEN:
            case INVALID_NAME:
                takenNames.add(chosenName);
                reroll(chosenName + " is taken. Pick another.");
                break;
            default:
                phase = Phase.OFFLINE_NOTICE;
                break;
        }
    }

    private void reroll(String newMessage) {
        candidates = UsernameGenerator.candidates(CANDIDATE_COUNT, takenNames, random);
        selectedIndex = 0;
        message = newMessage;
        phase = Phase.CHOOSING;
    }

    public void draw(SpriteBatch batch) {
        float cx = worldWidth / 2f;
        titleFont.setColor(Color.WHITE);
        drawCentered(batch, titleFont, "CHOOSE YOUR NAME", cx, worldHeight * 0.82f);
        font.setColor(Color.LIGHT_GRAY);
        drawCentered(batch, font, "Your name on the online leaderboard.", cx, worldHeight * 0.75f);

        switch (phase) {
            case CHOOSING:
                drawChoices(batch, cx);
                break;
            case REGISTERING:
                font.setColor(Color.WHITE);
                drawCentered(batch, font, "Registering " + chosenName + "...", cx, worldHeight * 0.5f);
                break;
            case OFFLINE_NOTICE:
                font.setColor(Color.WHITE);
                drawCentered(batch, font, "Couldn't reach the leaderboard server.", cx, worldHeight * 0.56f);
                drawCentered(batch, font, "You'll play as " + chosenName + ", and your name", cx, worldHeight * 0.5f);
                drawCentered(batch, font, "will be registered next time you're online.", cx, worldHeight * 0.46f);
                font.setColor(Color.LIGHT_GRAY);
                drawCentered(batch, font, "Enter/Space - Continue", cx, worldHeight * 0.1f);
                break;
            case WELCOME:
                font.setColor(Color.YELLOW);
                drawCentered(batch, font, "Welcome, " + chosenName + "!", cx, worldHeight * 0.5f);
                break;
        }
        font.setColor(Color.WHITE);
    }

    private void drawChoices(SpriteBatch batch, float cx) {
        float rowSpacing = worldHeight * 0.065f;
        float startY = worldHeight * 0.65f;
        for (int i = 0; i <= candidates.size(); i++) {
            boolean selected = i == selectedIndex;
            boolean isReroll = i == candidates.size();
            // A gap before the reroll row.
            float y = startY - i * rowSpacing - (isReroll ? rowSpacing * 0.5f : 0f);
            String label = isReroll ? REROLL_LABEL : candidates.get(i);
            String text = (selected ? "> " : "  ") + label;
            font.setColor(selected ? Color.YELLOW : (isReroll ? Color.LIGHT_GRAY : Color.WHITE));
            drawCentered(batch, font, text, cx, y);
            if (selected) {
                layout.setText(font, text);
                drawSelectionBox(batch, cx, y, layout.width, layout.height);
            }
        }
        if (message != null) {
            font.setColor(Color.ORANGE);
            drawCentered(batch, font, message, cx, worldHeight * 0.17f);
        }
        font.setColor(Color.LIGHT_GRAY);
        drawCentered(batch, font, "Up/Down - Move   Enter/Space - Confirm", cx, worldHeight * 0.1f);
    }

    private void drawCentered(SpriteBatch batch, BitmapFont f, String text, float cx, float y) {
        layout.setText(f, text);
        f.draw(batch, layout, cx - layout.width / 2f, y);
    }

    /** topY is the text's top (font.draw's y), so the box hangs down from it. */
    private void drawSelectionBox(SpriteBatch batch, float centerX, float topY, float textWidth, float textHeight) {
        float x = centerX - textWidth / 2f - SELECTION_BOX_PADDING_X;
        float y = topY - textHeight - SELECTION_BOX_PADDING_Y;
        float width = textWidth + SELECTION_BOX_PADDING_X * 2f;
        float height = textHeight + SELECTION_BOX_PADDING_Y * 2f;
        float t = SELECTION_BOX_THICKNESS;
        batch.setColor(SELECTION_BOX_COLOR);
        batch.draw(whitePixel, x, y, width, t);
        batch.draw(whitePixel, x, y + height - t, width, t);
        batch.draw(whitePixel, x, y, t, height);
        batch.draw(whitePixel, x + width - t, y, t, height);
        batch.setColor(Color.WHITE);
    }

    @Override
    public void dispose() {
        font.dispose();
        titleFont.dispose();
        whitePixel.dispose();
        selectSound.dispose();
        confirmSound.dispose();
    }
}
