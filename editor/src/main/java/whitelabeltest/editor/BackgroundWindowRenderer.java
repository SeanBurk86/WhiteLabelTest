package whitelabeltest.editor;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import whitelabeltest.gamemanagers.background.ScrollingBackground;
import whitelabeltest.gamemanagers.spawning.StageDefinition;

import java.util.ArrayList;
import java.util.List;

/** One background layer's true-scale, real-formula appearance at one instant - the exact scroll/
 *  clamp math ScrollingBackground.Layer runs in the real game (scrollY = scrollSpeed*elapsedTime,
 *  held at minScrollY once a layer's own art is fully exhausted, never repeating/tiling past that
 *  point), shared by both PlayerPreviewView (one such window, at whatever distance is scrubbed to)
 *  and StageCanvas (many of these stacked down the whole canvas - see StageCanvas.drawBackgroundArt()'s
 *  own doc for why that replaced its previous "one stretched panorama per layer" approximation: a
 *  single shared distance-to-pixel axis can only ever be true-scale for ONE background layer's own
 *  scroll rate at a time, so any OTHER layer drawn against that same axis reads at the wrong visual
 *  scale relative to sprites/paths, which always render true-scale - the only way to keep both a
 *  navigable-by-distance canvas AND every layer at its real, gameplay-matching scale is to stop
 *  drawing one static image and instead resample this exact per-instant computation repeatedly, the
 *  same one PlayerPreviewView already used for its own single snapshot). Being ONE shared method
 *  rather than two independently-hand-matched reimplementations of the same formula is the actual
 *  fix - it's how the two views drifted apart in the first place. */
public final class BackgroundWindowRenderer {
    private BackgroundWindowRenderer() {}

    /** Builds every ImageView needed to show layerDef's content at real elapsed time
     *  elapsedSinceStart, inside a worldWidth x worldHeight viewport rendered at `scale` pixels per
     *  world-unit. Positioned in the window's OWN local frame - (0,0) is the window's top-left corner
     *  (world-Y = worldHeight, the top of the play area) - the caller translates the returned nodes
     *  into its own coordinate space. Empty if this layer has no texture(s) that actually exist on
     *  disk. */
    public static List<ImageView> buildWindow(StageDefinition.BackgroundLayerDef layerDef,
            float elapsedSinceStart, double worldWidth, double worldHeight, double scale) {
        List<Image> textures = loadTextures(layerDef);
        if (textures.isEmpty()) return List.of();

        float[] heights = new float[textures.size()];
        float[] cumulativeTop = new float[textures.size()];
        float cumulative = 0f;
        for (int i = 0; i < textures.size(); i++) {
            Image tex = textures.get(i);
            heights[i] = (float) (worldWidth * (tex.getHeight() / tex.getWidth()));
            cumulativeTop[i] = cumulative;
            cumulative += heights[i];
        }
        float minScrollY = (float) worldHeight - cumulative;

        float scrollSpeed = Float.isNaN(layerDef.scrollSpeed) ? ScrollingBackground.DEFAULT_SCROLL_SPEED : layerDef.scrollSpeed;
        float scrollY = scrollSpeed * elapsedSinceStart;
        if (scrollY <= minScrollY) scrollY = minScrollY;

        List<ImageView> views = new ArrayList<>();
        for (int i = 0; i < textures.size(); i++) {
            float y = scrollY + cumulativeTop[i];
            float height = heights[i];
            if (y + height <= 0f || y >= worldHeight) continue;

            ImageView view = new ImageView(textures.get(i));
            view.setPreserveRatio(false);
            view.setFitWidth(worldWidth * scale);
            view.setFitHeight(height * scale);
            view.setLayoutX(0);
            // Bottom-up world-Y -> top-down local screen-Y, same flip StageCanvas.distanceToCanvasY()
            // makes for the distance axis.
            view.setLayoutY((worldHeight - (y + height)) * scale);
            views.add(view);
        }
        return views;
    }

    private static List<Image> loadTextures(StageDefinition.BackgroundLayerDef layerDef) {
        List<Image> textures = new ArrayList<>();
        if (layerDef.textureSequence != null && layerDef.textureSequence.size > 0) {
            for (String path : layerDef.textureSequence) {
                Image img = EnemySpriteImages.loadImage(path);
                if (img != null) textures.add(img);
            }
        } else if (layerDef.texture != null) {
            Image img = EnemySpriteImages.loadImage(layerDef.texture);
            if (img != null) textures.add(img);
        }
        return textures;
    }
}
