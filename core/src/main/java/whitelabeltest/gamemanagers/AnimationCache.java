package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ObjectMap;

public final class AnimationCache {
    private static final ObjectMap<Texture, ObjectMap<String, Animation<TextureRegion>>> cache = new ObjectMap<>();

    private AnimationCache() {}

    public static Animation<TextureRegion> get(Texture texture, int frameCount, float frameDuration, Animation.PlayMode playMode) {
        return get(texture, frameCount, 1, frameCount, frameDuration, playMode);
    }

    public static Animation<TextureRegion> get(Texture texture, int columns, int rows, int frameCount, float frameDuration, Animation.PlayMode playMode) {
        ObjectMap<String, Animation<TextureRegion>> byLayout = cache.get(texture);
        if (byLayout == null) {
            byLayout = new ObjectMap<>();
            cache.put(texture, byLayout);
        }

        String key = columns + "x" + rows + ":" + frameCount;
        Animation<TextureRegion> animation = byLayout.get(key);
        if (animation == null) {
            int frameWidth = texture.getWidth() / columns;
            int frameHeight = texture.getHeight() / rows;
            TextureRegion[][] split = TextureRegion.split(texture, frameWidth, frameHeight);

            TextureRegion[] frames = new TextureRegion[frameCount];
            int index = 0;
            for (int r = 0; r < rows && index < frameCount; r++) {
                for (int c = 0; c < columns && index < frameCount; c++) {
                    frames[index++] = split[r][c];
                }
            }

            animation = new Animation<>(frameDuration, frames);
            animation.setPlayMode(playMode);
            byLayout.put(key, animation);
        }
        return animation;
    }
}
