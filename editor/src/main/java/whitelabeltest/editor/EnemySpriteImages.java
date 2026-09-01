package whitelabeltest.editor;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import whitelabeltest.enemy.EnemyDefinition;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Loads and crops an enemy/sprite-cue's real texture to its actual in-game footprint - shared by
 *  TriggerNode (the placed-trigger icon on StageCanvas) and PlayerPreviewView (the simulated player
 *  view), so this sizing/cropping math lives in exactly one place instead of drifting between two
 *  copies. Same sizing rules the real game uses: GenericEnemy.initWithDefinition() for
 *  buildEnemyImage() (def.size along the frame's longer axis), EnemySpawnOps.spawnSpriteCue() for
 *  buildSpriteCueImage() (size is the drawn HEIGHT directly) - both converted to pixels at
 *  StageCanvas.PIXELS_PER_UNIT_X, the same real-world-unit scale every other width/height on the
 *  canvas already uses. */
public final class EnemySpriteImages {
    // Sanity ceiling on the sprite's rendered size, in pixels - guards against a pathological
    // EnemyDefinition.size/Trigger.size value blowing up the layout; every real boss in
    // data/enemies.json today (size up to ~3.5) renders well under this at
    // StageCanvas.PIXELS_PER_UNIT_X scale.
    public static final double MAX_SPRITE_PIXELS = 320;
    public static final double MIN_SPRITE_PIXELS = 4;

    private EnemySpriteImages() {}

    /** The real sprite for an enemy definition, cropped to its first frame - null if it has no
     *  texture, or the texture file doesn't exist on disk. */
    public static ImageView buildEnemyImage(EnemyDefinition def) {
        if (def == null || def.texture == null) return null;
        Image sheet = loadImage(def.texture);
        if (sheet == null) return null;
        int columns = Math.max(def.columns, 1);
        int rows = Math.max(def.rows, 1);
        double frameW = sheet.getWidth() / columns;
        double frameH = sheet.getHeight() / rows;
        if (frameW <= 0 || frameH <= 0) return null;

        double aspect = frameW / frameH;
        double worldW = aspect >= 1f ? def.size : def.size * aspect;
        double worldH = aspect >= 1f ? def.size / aspect : def.size;
        return cropFrame(sheet, frameW, frameH, worldW * StageCanvas.PIXELS_PER_UNIT_X, worldH * StageCanvas.PIXELS_PER_UNIT_X);
    }

    /** The real sprite for a Trigger.spriteTexture cue, cropped to its first frame - null if the
     *  texture path is null/missing on disk. */
    public static ImageView buildSpriteCueImage(String texturePath, int columns, int rows, float size) {
        if (texturePath == null) return null;
        Image sheet = loadImage(texturePath);
        if (sheet == null) return null;
        columns = Math.max(columns, 1);
        rows = Math.max(rows, 1);
        double frameW = sheet.getWidth() / columns;
        double frameH = sheet.getHeight() / rows;
        if (frameW <= 0 || frameH <= 0) return null;

        double worldH = size;
        double worldW = worldH * (frameW / frameH);
        return cropFrame(sheet, frameW, frameH, worldW * StageCanvas.PIXELS_PER_UNIT_X, worldH * StageCanvas.PIXELS_PER_UNIT_X);
    }

    // Keyed by the raw texturePath argument (pre-"images/"-stripping - fine, since every caller
    // passes the same convention consistently) - JavaFX Image decoding is the single biggest cost
    // in a rebuild (PlayerPreviewView/StageCanvas/TriggerNode all reload on every edit/scrub tick),
    // and the same handful of texture files get requested over and over across every enemy/layer
    // sharing them, so caching the decoded Image once per session (editor sessions are short-lived,
    // and re-decoding on external file changes was never supported here anyway) removes essentially
    // all of that cost after the first load. Single-threaded (JavaFX Application Thread only), so a
    // plain HashMap is fine - no concurrent access to guard against.
    private static final Map<String, Image> imageCache = new HashMap<>();

    public static Image loadImage(String texturePath) {
        if (imageCache.containsKey(texturePath)) return imageCache.get(texturePath);
        Image image = loadImageUncached(texturePath);
        imageCache.put(texturePath, image);
        return image;
    }

    private static Image loadImageUncached(String texturePath) {
        String relative = texturePath.startsWith("images/") ? texturePath.substring("images/".length()) : texturePath;
        File file = new File("images", relative);
        if (!file.exists()) return null;
        return new Image(file.toURI().toString());
    }

    public static ImageView cropFrame(Image sheet, double frameW, double frameH, double fitWidth, double fitHeight) {
        ImageView view = new ImageView(sheet);
        view.setViewport(new Rectangle2D(0, 0, frameW, frameH));
        view.setFitWidth(clamp(fitWidth, MIN_SPRITE_PIXELS, MAX_SPRITE_PIXELS));
        view.setFitHeight(clamp(fitHeight, MIN_SPRITE_PIXELS, MAX_SPRITE_PIXELS));
        return view;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
