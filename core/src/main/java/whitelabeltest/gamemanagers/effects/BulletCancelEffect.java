package whitelabeltest.gamemanagers.effects;
import whitelabeltest.gamemanagers.AssetManager;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Plays where an enemy bullet is cancelled (by a bomb or its source's death), using one of five
 *  sheet variants at random so a screen-wide cancel doesn't look uniform. */
public class BulletCancelEffect extends SingleShotAnimation {
    private static final int COLUMNS = 4;
    private static final int ROWS = 3;
    private static final int FRAME_COUNT = 12;
    private static final float FRAME_DURATION = 1f / 30f;

    public void init(AssetManager assets, float x, float y, float size) {
        Texture[] variants = assets.bulletCancelTextures;
        Texture texture = variants[MathUtils.random(variants.length - 1)];
        Animation<TextureRegion> animation = AnimationCache.get(texture, COLUMNS, ROWS, FRAME_COUNT, FRAME_DURATION, Animation.PlayMode.NORMAL);
        init(animation, x, y, size);
    }
}
