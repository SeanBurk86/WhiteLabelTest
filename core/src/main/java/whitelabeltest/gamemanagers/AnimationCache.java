package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ObjectMap;

public final class AnimationCache {
    private static final ObjectMap<Texture, ObjectMap<Integer, Animation<TextureRegion>>> cache = new ObjectMap<>();

    private AnimationCache() {}

    public static Animation<TextureRegion> get(Texture texture, int frameCount, float frameDuration, Animation.PlayMode playMode) {
        ObjectMap<Integer, Animation<TextureRegion>> byFrameCount = cache.get(texture);
        if (byFrameCount == null) {
            byFrameCount = new ObjectMap<>();
            cache.put(texture, byFrameCount);
        }

        Animation<TextureRegion> animation = byFrameCount.get(frameCount);
        if (animation == null) {
            int frameWidth = texture.getWidth() / frameCount;
            int frameHeight = texture.getHeight();
            TextureRegion[][] split = TextureRegion.split(texture, frameWidth, frameHeight);
            TextureRegion[] frames = new TextureRegion[frameCount];
            System.arraycopy(split[0], 0, frames, 0, frameCount);

            animation = new Animation<>(frameDuration, frames);
            animation.setPlayMode(playMode);
            byFrameCount.put(frameCount, animation);
        }
        return animation;
    }
}
