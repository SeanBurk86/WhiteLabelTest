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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.gamemanagers.audio.AudioSettings;
import whitelabeltest.gamemanagers.replay.ReplayBrowser;
import whitelabeltest.gamemanagers.replay.ReplayData;

/** Reached from StartScreen's REPLAYS item - lets the player pick one of their own recorded runs
 *  (see ReplayBrowser/ReplayRecorder) and watch it play back. Polls input directly the same way
 *  StartScreen/WeaponSelectScreen do, rather than through InputManager, since no GameController
 *  exists yet at this point in Main's state machine. */
public class ReplaySelectScreen implements Disposable {
    private static final String CONFIRM_SOUND = "audio/menu/confirmsoundmenu.mp3";
    private static final String BACK_SOUND = "audio/menu/backsoundmenu.mp3";
    private static final String SELECT_SOUND = "audio/menu/selectsoundmenu.mp3";

    // How many rows fit between startY and the bottom instructions line in draw() below, at this
    // screen's rowSpacing (worldHeight * 0.09) - see ReplayBrowser.setPageSize().
    private static final int PAGE_SIZE = 5;

    // Selection-box styling for the highlighted row - see drawSelectionBox().
    private static final Color SELECTION_BOX_COLOR = new Color(0.35f, 1f, 0.55f, 1f);
    private static final float SELECTION_BOX_PADDING_X = 0.15f;
    private static final float SELECTION_BOX_PADDING_Y = 0.08f;
    private static final float SELECTION_BOX_THICKNESS = 0.025f;

    private final BitmapFont font;
    private final BitmapFont titleFont;
    private final GlyphLayout layout;
    private final Texture whitePixel;
    private final float worldWidth, worldHeight;
    private final AudioSettings audioSettings;

    private final ReplayBrowser browser = new ReplayBrowser();
    private final Sound selectSound;
    private final Sound backSound;
    private boolean backRequested;
    private boolean prevDpadUpDown, prevDpadDownDown, prevDpadLeftDown, prevDpadRightDown, prevConfirmDown, prevBackButtonDown;

    // Kept alive after this screen is disposed (see getConfirmSound()) so the cue can keep playing
    // while GameController loads; the caller is responsible for disposing it eventually.
    private Sound confirmSound;

    public ReplaySelectScreen(float worldWidth, float worldHeight, AudioSettings audioSettings) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.audioSettings = audioSettings;

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/VT323-Regular.ttf"));
        FreeTypeFontParameter fontParams = new FreeTypeFontParameter();
        fontParams.size = 32;
        font = generator.generateFont(fontParams);
        titleFont = generator.generateFont(fontParams);
        generator.dispose();

        font.setUseIntegerPositions(false);
        font.getData().setScale(0.01171875f);
        titleFont.setUseIntegerPositions(false);
        titleFont.getData().setScale(0.0161953125f); // matches WeaponSelectScreen's header proportions

        layout = new GlyphLayout();

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        selectSound = Gdx.audio.newSound(Gdx.files.internal(SELECT_SOUND));
        backSound = Gdx.audio.newSound(Gdx.files.internal(BACK_SOUND));

        browser.open();
        browser.setPageSize(PAGE_SIZE);

        // Whatever button just confirmed the REPLAYS item on StartScreen (commonly gamepad buttonA,
        // which is also this screen's own confirm button) is very likely still physically held down
        // on the first frame this screen runs - seeding prev*Down from the controller's actual
        // current state here (instead of leaving them at their false default) stops update() from
        // misreading that same held press as a fresh confirm/back input and instantly picking
        // whatever replay is highlighted. Same fix as StartScreen.enterMenuPhase().
        Controller controller = Controllers.getCurrent();
        prevDpadUpDown = controller != null && controller.getButton(controller.getMapping().buttonDpadUp);
        prevDpadDownDown = controller != null && controller.getButton(controller.getMapping().buttonDpadDown);
        prevDpadLeftDown = controller != null && controller.getButton(controller.getMapping().buttonDpadLeft);
        prevDpadRightDown = controller != null && controller.getButton(controller.getMapping().buttonDpadRight);
        prevConfirmDown = controller != null && controller.getButton(controller.getMapping().buttonA);
        prevBackButtonDown = controller != null && (controller.getButton(controller.getMapping().buttonBack)
            || controller.getButton(controller.getMapping().buttonB));
    }

    /** Returns the confirmed replay the instant one is picked, null every frame before that - check
     *  isBackRequested() separately for the "return to start screen" case. */
    public ReplayData update(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) moveSelection(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) moveSelection(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) movePage(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) movePage(1);
        boolean confirmPressed = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.Z);
        boolean backPressed = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE);

        Controller controller = Controllers.getCurrent();
        if (controller != null) {
            boolean dpadUpDown = controller.getButton(controller.getMapping().buttonDpadUp);
            boolean dpadDownDown = controller.getButton(controller.getMapping().buttonDpadDown);
            if (dpadUpDown && !prevDpadUpDown) moveSelection(-1);
            if (dpadDownDown && !prevDpadDownDown) moveSelection(1);
            prevDpadUpDown = dpadUpDown;
            prevDpadDownDown = dpadDownDown;

            boolean dpadLeftDown = controller.getButton(controller.getMapping().buttonDpadLeft);
            boolean dpadRightDown = controller.getButton(controller.getMapping().buttonDpadRight);
            if (dpadLeftDown && !prevDpadLeftDown) movePage(-1);
            if (dpadRightDown && !prevDpadRightDown) movePage(1);
            prevDpadLeftDown = dpadLeftDown;
            prevDpadRightDown = dpadRightDown;

            boolean confirmDown = controller.getButton(controller.getMapping().buttonA);
            if (confirmDown && !prevConfirmDown) confirmPressed = true;
            prevConfirmDown = confirmDown;

            boolean backButtonDown = controller.getButton(controller.getMapping().buttonBack)
                || controller.getButton(controller.getMapping().buttonB);
            if (backButtonDown && !prevBackButtonDown) backPressed = true;
            prevBackButtonDown = backButtonDown;
        }

        if (backPressed) {
            backSound.play(audioSettings.getEffectiveSfxVolume());
            backRequested = true;
            return null;
        }

        if (!confirmPressed) return null;

        ReplayData data = browser.confirmSelection();
        if (data == null) return null; // empty list, or the selected file failed to parse

        confirmSound = Gdx.audio.newSound(Gdx.files.internal(CONFIRM_SOUND));
        confirmSound.play(audioSettings.getEffectiveSfxVolume());
        return data;
    }

    private void moveSelection(int direction) {
        int before = browser.getSelectedIndex();
        browser.moveSelection(direction);
        if (browser.getSelectedIndex() != before) selectSound.play(audioSettings.getEffectiveSfxVolume());
    }

    private void movePage(int direction) {
        int before = browser.getCurrentPage();
        browser.movePage(direction);
        if (browser.getCurrentPage() != before) selectSound.play(audioSettings.getEffectiveSfxVolume());
    }

    public boolean isBackRequested() { return backRequested; }

    public void draw(SpriteBatch batch) {
        float cx = worldWidth / 2f;

        titleFont.setColor(Color.WHITE);
        drawCentered(batch, titleFont, "REPLAYS", cx, worldHeight * 0.72f);

        Array<String> names = browser.getDisplayNames();
        float rowSpacing = worldHeight * 0.09f;
        float startY = worldHeight * 0.56f;
        if (names.size == 0) {
            font.setColor(Color.LIGHT_GRAY);
            drawCentered(batch, font, "(no replays recorded yet)", cx, startY);
            drawCentered(batch, font, "Drop a shared replay .json into:", cx, startY - rowSpacing);
            drawCentered(batch, font, browser.getFolderPath(), cx, startY - rowSpacing * 2f);
            font.setColor(Color.WHITE);
        } else {
            if (browser.getPageCount() > 1) {
                font.setColor(Color.LIGHT_GRAY);
                drawCentered(batch, font, "Page " + (browser.getCurrentPage() + 1) + "/" + browser.getPageCount(), cx, startY + rowSpacing * 0.8f);
                font.setColor(Color.WHITE);
            }
            for (int i = 0; i < names.size; i++) {
                boolean selected = i == browser.getSelectedIndexInPage();
                float y = startY - i * rowSpacing;
                font.setColor(selected ? Color.YELLOW : Color.WHITE);
                String text = (selected ? "> " : "  ") + names.get(i);
                drawCentered(batch, font, text, cx, y);
                if (selected) {
                    layout.setText(font, text);
                    drawSelectionBox(batch, cx, y, layout.width, layout.height);
                }
            }
        }

        font.setColor(Color.LIGHT_GRAY);
        String instructions = browser.getPageCount() > 1
            ? "Up/Down - Move   Left/Right - Page   Enter/Space - Watch   Esc - Back"
            : "Up/Down - Move   Enter/Space - Watch   Esc - Back";
        drawCentered(batch, font, instructions, cx, worldHeight * 0.1f);
        font.setColor(Color.WHITE);
    }

    private void drawCentered(SpriteBatch batch, BitmapFont f, String text, float cx, float y) {
        layout.setText(f, text);
        f.draw(batch, layout, cx - layout.width / 2f, y);
    }

    /** Green rectangle drawn around the currently highlighted row - see WeaponSelectScreen's
     *  identically-named method for why topY is treated as the text's top rather than its baseline. */
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

    /** Returns the fire-and-forget confirm sound so the caller can dispose it once it's safe to cut
     *  off (e.g. at app shutdown). Never disposed here, since this screen is torn down while the
     *  sound is still meant to be playing. May be null if no replay was confirmed yet. */
    public Sound getConfirmSound() {
        return confirmSound;
    }

    @Override
    public void dispose() {
        font.dispose();
        titleFont.dispose();
        whitePixel.dispose();
        selectSound.dispose();
        backSound.dispose();
    }
}
