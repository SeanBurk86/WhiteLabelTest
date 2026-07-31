package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.player.Player;

public class BasicWeapon extends BaseWeapon {
    private static final float STREAM_BULLET_SPACING = 0.2f;
    private static final float STREAM_PAIR_CLEARANCE = 0.3f;
    private static final float OUTER_SPREAD_ANGLE = 15f;
    private static final float INNER_SPREAD_ANGLE = 6f;

    private WeaponDefinition def;

    public void init(WeaponDefinition def, Texture texture, float x, float y, Vector2 dir, float speed) {
        this.def = def;

        this.animation = AnimationCache.get(texture, def.columns > 0 ? def.columns : def.frameCount, def.rows, def.frameCount, def.frameDuration, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        int frameWidth = frames[0].getRegionWidth();
        int frameHeight = frames[0].getRegionHeight();
        sprite.setSize(def.size, def.size * ((float) frameHeight / frameWidth));
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setY(y);

        this.velocity.set(dir).scl(speed);
        this.damage = def.getDamage(level);
        this.shootSpeedMultiplier = def.shootSpeedMultiplier;
        this.chainWindow = def.chainWindow;
        this.hitAnimation = def.hitAnimation;
        this.hitEffectSize = def.hitSize;
        this.animationTime = 0;
        this.path = null;

        sprite.setRotation(velocity.angleDeg() - 90);
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        spawnStreams(activeWeapons, texture, x, y, -1);
    }

    /** Fires only this level's even-indexed streams (the center stream, plus every other spread
     *  pair) - the player's share while the halo is out (see Player.handleShooting), so the two
     *  firing points split the pattern instead of doubling its total bullet count. */
    public void spawnPlayerPortion(Array<Weapon> activeWeapons, Texture texture, float x, float y) {
        spawnStreams(activeWeapons, texture, x, y, 0);
    }

    /** Fires only this level's odd-indexed streams - the halo's share while detached (see
     *  Player.updateHaloFiring). Complements spawnPlayerPortion() so together they add up to
     *  exactly one full pattern, split rather than doubled, between the two firing points. */
    public void spawnHaloPortion(Array<Weapon> activeWeapons, Texture texture, float x, float y) {
        spawnStreams(activeWeapons, texture, x, y, 1);
    }

    /** Builds this level's pattern, but every original stream now fires as two parallel copies
     *  side by side (see spawnStreamPair()) instead of one - doubling the number of independent
     *  streams so the parity split below always has an even number to divide, letting the player
     *  and the detached halo split evenly even at level 1's single stream. Skips whichever half of
     *  each pair doesn't match parity (0 or 1), or fires everything when parity is negative, the
     *  normal single-source case. Pairs are indexed in the order listed below: center first, then
     *  each spread pair outward, each one claiming the next two slots. */
    private void spawnStreams(Array<Weapon> activeWeapons, Texture texture, float x, float y, int parity) {
        float baseSpeed = def.getSpeed(level);
        if (level == 1) {
            spawnStreamPair(activeWeapons, texture, x, y, 0f, 1, baseSpeed, 0, parity);
        } else if (level == 2) {
            spawnStreamPair(activeWeapons, texture, x, y, 0f, 4, baseSpeed, 0, parity);
        } else if (level == 3) {
            spawnStreamPair(activeWeapons, texture, x, y, 0f, 4, baseSpeed, 0, parity);
            spawnStreamPair(activeWeapons, texture, x, y, -OUTER_SPREAD_ANGLE, 2, baseSpeed, 2, parity);
            spawnStreamPair(activeWeapons, texture, x, y, OUTER_SPREAD_ANGLE, 2, baseSpeed, 4, parity);
        } else {
            spawnStreamPair(activeWeapons, texture, x, y, 0f, 4, baseSpeed, 0, parity);
            spawnStreamPair(activeWeapons, texture, x, y, -OUTER_SPREAD_ANGLE, 4, baseSpeed, 2, parity);
            spawnStreamPair(activeWeapons, texture, x, y, OUTER_SPREAD_ANGLE, 4, baseSpeed, 4, parity);
            spawnStreamPair(activeWeapons, texture, x, y, -INNER_SPREAD_ANGLE, 4, baseSpeed, 6, parity);
            spawnStreamPair(activeWeapons, texture, x, y, INNER_SPREAD_ANGLE, 4, baseSpeed, 8, parity);
        }
    }

    private static boolean included(int streamIndex, int parity) {
        return parity < 0 || (streamIndex % 2) == parity;
    }

    /** One original stream, fired as two copies side by side (perpendicular to travel direction)
     *  of where the single stream used to be - each copy is its own entry in the parity split, at
     *  pairIndex and pairIndex + 1.
     *  When both copies fire together (parity < 0, the attached/single-source case), they're
     *  pushed apart by each copy's own width plus STREAM_PAIR_CLEARANCE, so the two bulletCount-
     *  wide formations never overlap regardless of level. Once detached, only one copy of a given
     *  pair ever fires at a time, and it fires from an entirely different point (the player's vs.
     *  the halo's) rather than sharing this origin - so it's centered on x,y with no extra offset,
     *  instead of staying shifted to the side it would've used as half of an attached pair. */
    private void spawnStreamPair(Array<Weapon> activeWeapons, Texture texture, float x, float y, float angleOffsetDeg, int bulletCount, float speed, int pairIndex, int parity) {
        Vector2 dir = new Vector2(0, 1).rotateDeg(angleOffsetDeg);
        float perpX = -dir.y, perpY = dir.x;
        float groupWidth = (bulletCount - 1) * STREAM_BULLET_SPACING;
        float half = parity < 0 ? groupWidth / 2f + STREAM_PAIR_CLEARANCE / 2f : 0f;

        if (included(pairIndex, parity)) {
            spawnStream(activeWeapons, texture, x - perpX * half, y - perpY * half, angleOffsetDeg, bulletCount, speed);
        }
        if (included(pairIndex + 1, parity)) {
            spawnStream(activeWeapons, texture, x + perpX * half, y + perpY * half, angleOffsetDeg, bulletCount, speed);
        }
    }

    /** Spawns bulletCount bullets side-by-side (evenly spaced perpendicular to their travel
     *  direction), all fired in the same direction: straight up, rotated by angleOffsetDeg. */
    private void spawnStream(Array<Weapon> activeWeapons, Texture texture, float x, float y, float angleOffsetDeg, int bulletCount, float speed) {
        Vector2 dir = new Vector2(0, 1).rotateDeg(angleOffsetDeg);
        float perpX = -dir.y, perpY = dir.x;
        for (int i = 0; i < bulletCount; i++) {
            float offset = (i - (bulletCount - 1) / 2f) * STREAM_BULLET_SPACING;
            spawnSingle(activeWeapons, texture, x + perpX * offset, y + perpY * offset, new Vector2(dir), speed);
        }
    }

    private void spawnSingle(Array<Weapon> activeWeapons, Texture texture, float x, float y, Vector2 dir, float speed) {
        BasicWeapon w = ObjectPools.weaponPool.obtain();
        w.setLevel(this.level);
        w.init(def, texture, x, y, dir.nor(), speed);
        activeWeapons.add(w);
    }

    @Override
    public float getFireRate() {
        return def.getFireRate(level);
    }

    @Override
    public void playFireSound(AudioManager audio, int level) {
        audio.playBasicWeaponSound(level);
    }

    /** BasicWeapon's Hyper Attack: launches the graze halo forward a short distance - dealing
     *  damage to anything it clips along the way (see Player.triggerBasicHyperAttack()) - where
     *  it then stays detached from the player until this fires again, gliding back instead of
     *  snapping back into place. */
    @Override
    public void hyperAttack(Player player, Array<Weapon> activeWeapons, Array<Enemy> enemies, AssetManager assets, AudioManager audio) {
        player.triggerBasicHyperAttack(audio);
    }
}
