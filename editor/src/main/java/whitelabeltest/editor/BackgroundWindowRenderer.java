package whitelabeltest.editor;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import whitelabeltest.gamemanagers.background.ScrollingBackground;
import whitelabeltest.gamemanagers.spawning.StageDefinition;

import java.util.ArrayList;
import java.util.List;

/** One background layer as it looks at a given time, using ScrollingBackground.Layer's math
 *  (scrollY = scrollSpeed * time, frozen once the art runs out). Shared by PlayerPreviewView and
 *  StageCanvas so the two always agree. */
public final class BackgroundWindowRenderer {
    private BackgroundWindowRenderer() {}

    /** ImageViews for the layer at elapsedSinceStart, in the window's local frame ((0,0) = top-left);
     *  the caller translates them. Empty if no texture exists. */
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
            // World y is up, screen y is down.
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
