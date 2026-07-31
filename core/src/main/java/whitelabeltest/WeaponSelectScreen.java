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
import whitelabeltest.player.WeaponLoadout;

/** Shown once, right after StartScreen's "press any key", so the player can pick which two
 *  weapons they start the run with (see WeaponLoadout) - Up/Down or the D-Pad move the highlighted
 *  option, Enter/Space/Z or the A button confirms it. Polls input directly the same way StartScreen
 *  does, rather than going through InputManager/KeyBindings, since neither a GameController nor an
 *  input-type choice exists yet at this point in Main's state machine. */
public class WeaponSelectScreen implements Disposable {
    private static final WeaponLoadout[] OPTIONS = WeaponLoadout.values();

    private final BitmapFont font;
    private final BitmapFont titleFont;
    private final GlyphLayout layout;
    private final float worldWidth, worldHeight;

    private int selectedIndex;
    private boolean prevDpadUpDown, prevDpadDownDown, prevConfirmDown;

    public WeaponSelectScreen(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("VT323-Regular.ttf"));
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

        return confirmPressed ? OPTIONS[selectedIndex] : null;
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
            font.setColor(selected ? Color.YELLOW : Color.WHITE);
            String text = (selected ? "> " : "  ") + OPTIONS[i].label;
            drawCentered(batch, font, text, cx, startY - i * rowSpacing);
        }

        font.setColor(Color.LIGHT_GRAY);
        drawCentered(batch, font, "Up/Down - Move   Enter/Space - Confirm", cx, worldHeight * 0.1f);
        font.setColor(Color.WHITE);
    }

    private void drawCentered(SpriteBatch batch, BitmapFont f, String text, float cx, float y) {
        layout.setText(f, text);
        f.draw(batch, layout, cx - layout.width / 2f, y);
    }

    @Override
    public void dispose() {
        font.dispose();
        titleFont.dispose();
    }
}
