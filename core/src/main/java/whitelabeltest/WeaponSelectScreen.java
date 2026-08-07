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
import whitelabeltest.player.WeaponLoadout;

/** Shown once, right after StartScreen's "press any key", so the player can pick which two
 *  weapons they start the run with (see WeaponLoadout) - Up/Down or the D-Pad move the highlighted
 *  option, Enter/Space/Z or the A button confirms it. Polls input directly the same way StartScreen
 *  does, rather than going through InputManager/KeyBindings, since neither a GameController nor an
 *  input-type choice exists yet at this point in Main's state machine. */
public class WeaponSelectScreen implements Disposable {
    private static final WeaponLoadout[] OPTIONS = WeaponLoadout.values();
    private static final String CONFIRM_SOUND = "audio/menu/pentest.mp3";

    // Selection-box styling for the highlighted loadout row - see drawSelectionBox().
    private static final Color SELECTION_BOX_COLOR = new Color(0.35f, 1f, 0.55f, 1f);
    private static final float SELECTION_BOX_PADDING_X = 0.15f;
    private static final float SELECTION_BOX_PADDING_Y = 0.08f;
    private static final float SELECTION_BOX_THICKNESS = 0.025f;

    private final BitmapFont font;
    private final BitmapFont titleFont;
    private final GlyphLayout layout;
    private final Texture whitePixel;
    private final float worldWidth, worldHeight;

    private int selectedIndex;
    private boolean prevDpadUpDown, prevDpadDownDown, prevConfirmDown;

    // Kept alive after this screen is disposed (see getConfirmSound()) so the cue can keep playing
    // while GameController loads; the caller is responsible for disposing it eventually.
    private Sound confirmSound;

    public WeaponSelectScreen(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/VT323-Regular.ttf"));
        FreeTypeFontParameter fontParams = new FreeTypeFontParameter();
        fontParams.size = 32;
        font = generator.generateFont(fontParams);
        titleFont = generator.generateFont(fontParams);
        generator.dispose();

        font.setUseIntegerPositions(false);
        font.getData().setScale(0.01171875f);
        titleFont.setUseIntegerPositions(false);
        titleFont.getData().setScale(0.0161953125f); // 1.382x font, matches OPTIONS' header proportions

        layout = new GlyphLayout();

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();
    }

    /** Returns the confirmed loadout the instant it's picked, null every frame before that. */
    public WeaponLoadout update(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) moveSelection(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) moveSelection(1);
        boolean confirmPressed = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.Z);

        Controller controller = Controllers.getCurrent();
        if (controller != null) {
            boolean dpadUpDown = controller.getButton(controller.getMapping().buttonDpadUp);
            boolean dpadDownDown = controller.getButton(controller.getMapping().buttonDpadDown);
            if (dpadUpDown && !prevDpadUpDown) moveSelection(-1);
            if (dpadDownDown && !prevDpadDownDown) moveSelection(1);
            prevDpadUpDown = dpadUpDown;
            prevDpadDownDown = dpadDownDown;

            boolean confirmDown = controller.getButton(controller.getMapping().buttonA);
            if (confirmDown && !prevConfirmDown) confirmPressed = true;
            prevConfirmDown = confirmDown;
        }

        if (!confirmPressed) return null;

        confirmSound = Gdx.audio.newSound(Gdx.files.internal(CONFIRM_SOUND));
        confirmSound.play();
        return OPTIONS[selectedIndex];
    }

    private void moveSelection(int delta) {
        selectedIndex = (selectedIndex + delta + OPTIONS.length) % OPTIONS.length;
    }

    public void draw(SpriteBatch batch) {
        float cx = worldWidth / 2f;

        titleFont.setColor(Color.WHITE);
        drawCentered(batch, titleFont, "CHOOSE YOUR LOADOUT", cx, worldHeight * 0.72f);

        float rowSpacing = worldHeight * 0.09f;
        float startY = worldHeight * 0.56f;
        for (int i = 0; i < OPTIONS.length; i++) {
            boolean selected = i == selectedIndex;
            float y = startY - i * rowSpacing;
            font.setColor(selected ? Color.YELLOW : Color.WHITE);
            String text = (selected ? "> " : "  ") + OPTIONS[i].label;
            drawCentered(batch, font, text, cx, y);
            if (selected) {
                layout.setText(font, text);
                drawSelectionBox(batch, cx, y, layout.width, layout.height);
            }
        }

        font.setColor(Color.LIGHT_GRAY);
        drawCentered(batch, font, "Up/Down - Move   Enter/Space - Confirm", cx, worldHeight * 0.1f);
        font.setColor(Color.WHITE);
    }

    private void drawCentered(SpriteBatch batch, BitmapFont f, String text, float cx, float y) {
        layout.setText(f, text);
        f.draw(batch, layout, cx - layout.width / 2f, y);
    }

    /** Green rectangle drawn around the currently highlighted loadout row - centerX/topY match
     *  drawCentered()'s own placement of that row's text (font.draw(batch, layout, x, y) treats y
     *  as the TOP of the rendered text, not its baseline, so the box hangs down from topY by
     *  textHeight rather than up from it), so the box tracks it exactly regardless of row width. */
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

    /** Returns the fire-and-forget confirm sound so the caller can dispose it once it's safe to
     * cut off (e.g. at app shutdown). Never disposed here, since this screen is torn down while
     * the sound is still meant to be playing. May be null if no loadout was confirmed yet. */
    public Sound getConfirmSound() {
        return confirmSound;
    }

    @Override
    public void dispose() {
        font.dispose();
        titleFont.dispose();
        whitePixel.dispose();
    }
}
