package whitelabeltest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.audio.AudioSettings;
import whitelabeltest.gamemanagers.input.InputType;

public class StartScreen implements Disposable {
    private enum Phase { SELECTING, MENU, FADING, DONE }

    private static final float FADE_DURATION = 0.7f;
    // On confirming ARCADE MODE or TUTORIAL.
    private static final String ARCADE_CONFIRM_SOUND = "audio/menu/arcadeselectsoundmenu.mp3";
    private static final String MENU_SELECT_SOUND = "audio/menu/selectsoundmenu.mp3";
    // On confirming OPTIONS / REPLAYS (the generic menu cue).
    private static final String OPTIONS_CONFIRM_SOUND = "audio/menu/confirmsoundmenu.mp3";

    // Looping music, faded in over MUSIC_FADE_IN_DURATION.
    private static final String OPENING_MUSIC = "audio/music/openingmusic.mp3";
    private static final float MUSIC_FADE_IN_DURATION = 2f;

    // Green box around the highlighted item.
    private static final Color SELECTION_BOX_COLOR = new Color(0.35f, 1f, 0.55f, 1f);
    private static final float SELECTION_BOX_PADDING_X = 0.15f;
    private static final float SELECTION_BOX_PADDING_Y = 0.08f;
    private static final float SELECTION_BOX_THICKNESS = 0.025f;

    // Replaces the "press any button" sign after the first press. OPTIONS/REPLAYS leave this
    // screen in the MENU phase so it's still showing when the player comes back.
    private static final String[] MENU_ITEMS = { "ARCADE MODE", "TUTORIAL", "REPLAYS", "OPTIONS", "EXIT" };
    private static final int MENU_ARCADE_MODE = 0;
    private static final int MENU_TUTORIAL = 1;
    private static final int MENU_REPLAYS = 2;
    private static final int MENU_OPTIONS = 3;
    private static final int MENU_EXIT = 4;

    /** One looping sprite-sheet sign in the title stack. Height follows from width and the frame's
     *  aspect ratio. */
    private static final class Sign {
        final String file;
        final int columns, rows;
        final float width;
        Texture texture;
        Animation<TextureRegion> animation;

        Sign(String file, int columns, int rows, float width) {
            this.file = file;
            this.columns = columns;
            this.rows = rows;
            this.width = width;
        }

        void load(float frameDuration) {
            texture = new Texture(Gdx.files.internal(file));
            animation = AnimationCache.get(texture, columns, rows, columns * rows, frameDuration, Animation.PlayMode.LOOP);
        }

        float height() {
            float frameAspect = (texture.getWidth() / (float) columns) / (texture.getHeight() / (float) rows);
            return width / frameAspect;
        }
    }

    private static final float SIGN_FRAME_DURATION = 0.09f;

    // Top to bottom: wordmark, subtitle, revision tag, gap, prompt; the studio credit is pinned
    // near the bottom.
    private final Sign penTestSign = new Sign("images/ui/ThePenTestSign.png", 3, 4, 8.0f);
    private final Sign scathachSign = new Sign("images/ui/ScathachSign.png", 2, 6, 6.5f);
    private final Sign revisionSign = new Sign("images/ui/2ndRevSign.png", 3, 4, 5.0f);
    private final Sign pressButtonSign = new Sign("images/ui/PressButtonSign.png", 2, 6, 4.6f);
    private final Sign swanSoftSign = new Sign("images/ui/SwanSoftSign.png", 2, 6, 4.2f);
    private final Sign[] signs = { penTestSign, scathachSign, revisionSign, pressButtonSign, swanSoftSign };

    private static final int[] KEYBOARD_DETECT_KEYS = {
        Input.Keys.SPACE, Input.Keys.ENTER, Input.Keys.Z, Input.Keys.X,
        Input.Keys.LEFT, Input.Keys.RIGHT, Input.Keys.UP, Input.Keys.DOWN,
        Input.Keys.W, Input.Keys.A, Input.Keys.S, Input.Keys.D,
        Input.Keys.SHIFT_LEFT, Input.Keys.CONTROL_LEFT
    };

    private final BitmapFont font;
    private final GlyphLayout layout;
    private final Texture fadePixel;
    private final float worldWidth, worldHeight;

    private final AudioSettings audioSettings;
    private final Music openingMusic;
    private float musicFadeTimer;

    private InputType detectedInput;
    private Phase phase = Phase.SELECTING;
    private float fadeTimer;
    private float animTime;
    private boolean prevAnyButtonDown;

    private int menuIndex;
    private boolean optionsRequested;
    private boolean replaysRequested;
    // Tells Main whether the run being started is the tutorial or arcade mode.
    private boolean tutorialSelected;
    private boolean prevMenuDpadUpDown, prevMenuDpadDownDown, prevMenuConfirmDown;

    // Outlives the screen so the cue keeps playing while the game loads; the caller disposes it.
    private Sound confirmSound;
    private final Sound menuSelectSound;
    private final Sound optionsConfirmSound;

    public StartScreen(float worldWidth, float worldHeight, AudioSettings audioSettings) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.audioSettings = audioSettings;

        for (Sign sign : signs) sign.load(SIGN_FRAME_DURATION);
        menuSelectSound = Gdx.audio.newSound(Gdx.files.internal(MENU_SELECT_SOUND));
        optionsConfirmSound = Gdx.audio.newSound(Gdx.files.internal(OPTIONS_CONFIRM_SOUND));

        openingMusic = Gdx.audio.newMusic(Gdx.files.internal(OPENING_MUSIC));
        openingMusic.setLooping(true);
        openingMusic.setVolume(0f);
        openingMusic.play();

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/VT323-Regular.ttf"));
        FreeTypeFontParameter fontParams = new FreeTypeFontParameter();
        fontParams.size = 32;
        font = generator.generateFont(fontParams);
        generator.dispose();

        font.setUseIntegerPositions(false);
        font.getData().setScale(0.01171875f); // matches the on-screen size the old default font had at 0.025f
        layout = new GlyphLayout();

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        fadePixel = new Texture(pm);
        pm.dispose();
    }

    public InputType update(float delta) {
        animTime += delta;
        musicFadeTimer = Math.min(musicFadeTimer + delta, MUSIC_FADE_IN_DURATION);
        applyMusicVolume();

        switch (phase) {
            case SELECTING:
                if (anyKeyJustPressed()) {
                    detectedInput = InputType.KEYBOARD;
                    enterMenuPhase();
                } else {
                    Controller c = Controllers.getCurrent();
                    if (c != null && anyButtonJustPressed(c)) {
                        detectedInput = InputType.GAMEPAD;
                        enterMenuPhase();
                    }
                }
                break;

            case MENU:
                updateMenu();
                break;

            case FADING:
                fadeTimer += delta;
                if (fadeTimer >= FADE_DURATION) {
                    phase = Phase.DONE;
                }
                break;

            case DONE:
                return detectedInput;
        }
        return null;
    }

    /** Separate from update() so Main can keep the music volume live while Options is open. */
    public void applyMusicVolume() {
        float fadeFraction = musicFadeTimer / MUSIC_FADE_IN_DURATION;
        openingMusic.setVolume(fadeFraction * audioSettings.getEffectiveMusicVolume());
    }

    /** Seeds the button states so the still-held press that opened the menu isn't read as a new one. */
    private void enterMenuPhase() {
        phase = Phase.MENU;
        Controller controller = Controllers.getCurrent();
        prevMenuDpadUpDown = controller != null && controller.getButton(controller.getMapping().buttonDpadUp);
        prevMenuDpadDownDown = controller != null && controller.getButton(controller.getMapping().buttonDpadDown);
        prevMenuConfirmDown = controller != null && controller.getButton(controller.getMapping().buttonA);
    }

    private void updateMenu() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) moveMenuSelection(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) moveMenuSelection(1);
        boolean confirmPressed = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.Z);

        Controller controller = Controllers.getCurrent();
        if (controller != null) {
            boolean dpadUpDown = controller.getButton(controller.getMapping().buttonDpadUp);
            boolean dpadDownDown = controller.getButton(controller.getMapping().buttonDpadDown);
            if (dpadUpDown && !prevMenuDpadUpDown) moveMenuSelection(-1);
            if (dpadDownDown && !prevMenuDpadDownDown) moveMenuSelection(1);
            prevMenuDpadUpDown = dpadUpDown;
            prevMenuDpadDownDown = dpadDownDown;

            boolean confirmDown = controller.getButton(controller.getMapping().buttonA);
            if (confirmDown && !prevMenuConfirmDown) confirmPressed = true;
            prevMenuConfirmDown = confirmDown;
        }

        if (!confirmPressed) return;

        if (menuIndex == MENU_ARCADE_MODE || menuIndex == MENU_TUTORIAL) {
            tutorialSelected = menuIndex == MENU_TUTORIAL;
            confirmSound = Gdx.audio.newSound(Gdx.files.internal(ARCADE_CONFIRM_SOUND));
            confirmSound.play(audioSettings.getEffectiveSfxVolume());
            phase = Phase.FADING;
        } else if (menuIndex == MENU_REPLAYS) {
            optionsConfirmSound.play(audioSettings.getEffectiveSfxVolume());
            replaysRequested = true;
        } else if (menuIndex == MENU_OPTIONS) {
            optionsConfirmSound.play(audioSettings.getEffectiveSfxVolume());
            optionsRequested = true;
        } else if (menuIndex == MENU_EXIT) {
            optionsConfirmSound.play(audioSettings.getEffectiveSfxVolume());
            Gdx.app.exit();
        }
    }

    private void moveMenuSelection(int delta) {
        int newIndex = (menuIndex + delta + MENU_ITEMS.length) % MENU_ITEMS.length;
        if (newIndex != menuIndex) menuSelectSound.play(audioSettings.getEffectiveSfxVolume());
        menuIndex = newIndex;
    }

    /** One-shot flag read by Main. */
    public boolean consumeOptionsRequested() {
        boolean requested = optionsRequested;
        optionsRequested = false;
        return requested;
    }

    /** One-shot flag read by Main. */
    public boolean consumeReplaysRequested() {
        boolean requested = replaysRequested;
        replaysRequested = false;
        return requested;
    }

    /** Valid once update() returns non-null. */
    public boolean isTutorialSelected() {
        return tutorialSelected;
    }

    public void draw(SpriteBatch batch) {
        batch.setColor(Color.WHITE);

        float centerX = worldWidth / 2f;
        float y = worldHeight - 0.8f;
        y = drawSign(batch, penTestSign, centerX, y) - 0.15f;
        y = drawSign(batch, scathachSign, centerX, y) - 0.15f;
        drawSign(batch, revisionSign, centerX, y);

        if (phase == Phase.SELECTING) {
            drawSign(batch, pressButtonSign, centerX, worldHeight * 0.42f);
        } else {
            drawMenu(batch, centerX);
        }
        drawSign(batch, swanSoftSign, centerX, swanSoftSign.height() + 0.5f);

        if (phase == Phase.FADING || phase == Phase.DONE) {
            float alpha = Math.min(fadeTimer / FADE_DURATION, 1f);
            batch.setColor(0f, 0f, 0f, alpha);
            batch.draw(fadePixel, 0, 0, worldWidth, worldHeight);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawMenu(SpriteBatch batch, float centerX) {
        float startY = worldHeight * 0.46f;
        float rowSpacing = worldHeight * 0.09f;
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            boolean selected = i == menuIndex;
            float y = startY - i * rowSpacing;
            font.setColor(selected ? Color.YELLOW : Color.WHITE);
            String text = (selected ? "> " : "  ") + MENU_ITEMS[i];
            drawCentered(batch, text, centerX, y);
            if (selected) {
                layout.setText(font, text);
                drawSelectionBox(batch, centerX, y, layout.width, layout.height);
            }
        }
        font.setColor(Color.WHITE);
    }

    /** topY is the text's top (font.draw's y), so the box hangs down from it. */
    private void drawSelectionBox(SpriteBatch batch, float centerX, float topY, float textWidth, float textHeight) {
        float x = centerX - textWidth / 2f - SELECTION_BOX_PADDING_X;
        float y = topY - textHeight - SELECTION_BOX_PADDING_Y;
        float width = textWidth + SELECTION_BOX_PADDING_X * 2f;
        float height = textHeight + SELECTION_BOX_PADDING_Y * 2f;
        float t = SELECTION_BOX_THICKNESS;

        batch.setColor(SELECTION_BOX_COLOR);
        batch.draw(fadePixel, x, y, width, t);
        batch.draw(fadePixel, x, y + height - t, width, t);
        batch.draw(fadePixel, x, y, t, height);
        batch.draw(fadePixel, x + width - t, y, t, height);
        batch.setColor(Color.WHITE);
    }

    /** Draws sign with its top edge at topY, centered on centerX, and returns its bottom edge so
     *  callers can chain signs into a top-down stack. */
    private float drawSign(SpriteBatch batch, Sign sign, float centerX, float topY) {
        TextureRegion frame = sign.animation.getKeyFrame(animTime);
        float h = sign.height();
        batch.draw(frame, centerX - sign.width / 2f, topY - h, sign.width, h);
        return topY - h;
    }

    private void drawCentered(SpriteBatch batch, String text, float cx, float y) {
        layout.setText(font, text);
        font.draw(batch, layout, cx - layout.width / 2f, y);
    }

    private boolean anyKeyJustPressed() {
        for (int key : KEYBOARD_DETECT_KEYS) {
            if (Gdx.input.isKeyJustPressed(key)) return true;
        }
        return false;
    }

    private boolean anyButtonJustPressed(Controller controller) {
        int[] buttons = {
            controller.getMapping().buttonA,
            controller.getMapping().buttonB,
            controller.getMapping().buttonX,
            controller.getMapping().buttonY,
            controller.getMapping().buttonStart,
            controller.getMapping().buttonR1,
            controller.getMapping().buttonL1,
            controller.getMapping().buttonDpadUp,
            controller.getMapping().buttonDpadDown,
            controller.getMapping().buttonDpadLeft,
            controller.getMapping().buttonDpadRight,
        };
        boolean anyDown = false;
        for (int button : buttons) {
            if (controller.getButton(button)) { anyDown = true; break; }
        }
        boolean justPressed = !prevAnyButtonDown && anyDown;
        prevAnyButtonDown = anyDown;
        return justPressed;
    }

    /** The caller disposes this (e.g. at shutdown). Null if no run was started. */
    public Sound getConfirmSound() {
        return confirmSound;
    }

    @Override
    public void dispose() {
        for (Sign sign : signs) sign.texture.dispose();
        font.dispose();
        fadePixel.dispose();
        menuSelectSound.dispose();
        optionsConfirmSound.dispose();
        openingMusic.dispose();
    }
}
