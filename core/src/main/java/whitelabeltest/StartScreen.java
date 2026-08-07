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
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.AudioSettings;
import whitelabeltest.gamemanagers.InputType;

public class StartScreen implements Disposable {
    private enum Phase { SELECTING, MENU, FADING, DONE }

    private static final float FADE_DURATION = 0.7f;
    // Played the instant ARCADE MODE is confirmed (see updateMenu()) - pentest.mp3, previously
    // played here at the end of the fade, now plays instead when the weapon loadout is confirmed
    // on WeaponSelectScreen (see WeaponSelectScreen.CONFIRM_SOUND).
    private static final String ARCADE_CONFIRM_SOUND = "audio/menu/arcadeselectsoundmenu.mp3";
    private static final String MENU_SELECT_SOUND = "audio/menu/selectsoundmenu.mp3";
    // Played when OPTIONS is confirmed instead - the same generic confirm cue OptionsScreen's own
    // menu uses internally, since ARCADE_CONFIRM_SOUND is specifically an arcade-mode-start cue.
    private static final String OPTIONS_CONFIRM_SOUND = "audio/menu/confirmsoundmenu.mp3";

    // Looping background music for this screen, faded in from silence over MUSIC_FADE_IN_DURATION
    // rather than starting at full volume - see update(). Stopped/disposed alongside the rest of
    // this screen once a run actually starts (see dispose()).
    private static final String OPENING_MUSIC = "audio/music/openingmusic.mp3";
    private static final float MUSIC_FADE_IN_DURATION = 2f;

    // Shown in place of PressButtonSign once the player presses anything in SELECTING - Up/Down or
    // the D-Pad move the highlight, Enter/Space/Z or the A button confirms (same scheme as
    // WeaponSelectScreen, which follows right after this). Index 0 starts the run as before;
    // index 1 signals Main to open OptionsScreen (see consumeOptionsRequested()) without leaving
    // this phase, so the menu is still showing when Options closes.
    private static final String[] MENU_ITEMS = { "ARCADE MODE", "OPTIONS" };
    private static final int MENU_ARCADE_MODE = 0;
    private static final int MENU_OPTIONS = 1;

    /** One looping animated sign in the opening screen's stacked composition (reference mockup:
     *  Screenshot 2026-08-07 145711.png) - replaces the old single openingscreen.webm loop with
     *  several independently-looping sprite-sheet flicker animations layered over a plain black
     *  background. width is the sign's on-screen width in world units; its drawn height follows
     *  from that plus the sheet's own per-frame aspect ratio, so it doesn't need to be measured by
     *  hand. */
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

    // Order/grid layout from the reference mockup: publisher wordmark, subtitle, revision tag,
    // then a big gap down to the "press any button" prompt, with the studio credit pinned near
    // the bottom independent of the rest of the stack.
    private final Sign penTestSign = new Sign("images/ui/ThePenTestSign.png", 3, 4, 8.0f);
    private final Sign scathachSign = new Sign("images/ui/ScathachSign.png", 2, 6, 6.5f);
    private final Sign revisionSign = new Sign("images/ui/1stRevSign.png", 3, 4, 5.0f);
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
    private boolean prevMenuDpadUpDown, prevMenuDpadDownDown, prevMenuConfirmDown;

    // Kept alive after this screen is disposed (see getConfirmSound()) so the cue can keep
    // playing while GameController loads; the caller is responsible for disposing it eventually.
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

        if (musicFadeTimer < MUSIC_FADE_IN_DURATION) {
            musicFadeTimer += delta;
            float fadeFraction = Math.min(musicFadeTimer / MUSIC_FADE_IN_DURATION, 1f);
            openingMusic.setVolume(fadeFraction * audioSettings.getMusicVolume());
        }

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

    /** Whatever gamepad button just triggered SELECTING -> MENU (commonly buttonA, which is also
     *  the menu's own confirm button) is very likely still physically held down on the first frame
     *  MENU runs - seeding prevMenu*Down from the controller's actual current state here (instead
     *  of leaving them at their false default) stops updateMenu() from misreading that same held
     *  press as a fresh confirm/nav input and instantly selecting ARCADE MODE. */
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

        if (menuIndex == MENU_ARCADE_MODE) {
            confirmSound = Gdx.audio.newSound(Gdx.files.internal(ARCADE_CONFIRM_SOUND));
            confirmSound.play();
            phase = Phase.FADING;
        } else if (menuIndex == MENU_OPTIONS) {
            optionsConfirmSound.play();
            optionsRequested = true;
        }
    }

    private void moveMenuSelection(int delta) {
        int newIndex = (menuIndex + delta + MENU_ITEMS.length) % MENU_ITEMS.length;
        if (newIndex != menuIndex) menuSelectSound.play();
        menuIndex = newIndex;
    }

    /** Consumed by Main once it opens OptionsScreen in response - this phase (MENU) is left
     *  untouched either way, so the menu (still on whichever item was highlighted) is what's
     *  showing again once Options closes, rather than reopening the PressButtonSign prompt. */
    public boolean consumeOptionsRequested() {
        boolean requested = optionsRequested;
        optionsRequested = false;
        return requested;
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
            font.setColor(selected ? Color.YELLOW : Color.WHITE);
            String text = (selected ? "> " : "  ") + MENU_ITEMS[i];
            drawCentered(batch, text, centerX, startY - i * rowSpacing);
        }
        font.setColor(Color.WHITE);
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

    /** Returns the fire-and-forget arcade-confirm sound so the caller can dispose it once it's
     * safe to cut off (e.g. at app shutdown). Never disposed here, since this screen is torn down
     * while the sound is still meant to be playing. May be null if ARCADE MODE was never
     * confirmed. */
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
