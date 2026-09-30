package whitelabeltest.editor;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import whitelabeltest.enemy.EnemyDefinition;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** First-frame images of enemies and sprite cues at in-game size (StageCanvas scale), sized like
 *  the game: enemies by def.size on the longer axis, sprite cues by height. Images are cached. */
public final class EnemySpriteImages {
    // Guards the layout against absurd sizes.
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

    // Decoding is the main cost of a rebuild, so decoded images are cached (FX thread only).
    private static final Map<String, Image> imageCache = new HashMap<>();

    /** Misses aren't cached, so art added while the editor is running shows up on the next rebuild. */
    public static Image loadImage(String texturePath) {
        Image cached = imageCache.get(texturePath);
        if (cached != null) return cached;
        Image image = loadImageUncached(texturePath);
        if (image != null) imageCache.put(texturePath, image);
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
