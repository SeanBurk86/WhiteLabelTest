package whitelabeltest.editor;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.enemy.PatternFactory;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.gamemanagers.background.ScrollingBackground;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
import whitelabeltest.gamemanagers.trigger.EnemyEntranceMovement;
import whitelabeltest.gamemanagers.trigger.Trigger;
import whitelabeltest.gamemanagers.trigger.WaveSpawnPlanner;

/** The "Player View" tab: the game screen at a scrubbed distance. Background art at its real
 *  scroll position, and every spawned enemy stepped through the game's own MovementPattern classes
 *  (they're plain math and need no GL context; sprites are drawn with JavaFX ImageViews). Enemies
 *  and background only: no bullets, player or cues. */
public class PlayerPreviewView extends Pane {
    // Keep in sync with Main.PLAY_AREA_WIDTH / PLAY_AREA_HEIGHT.
    private static final double WORLD_WIDTH = 9.0;
    private static final double WORLD_HEIGHT = 12.0;
    private static final double SCALE = StageCanvas.PIXELS_PER_UNIT_X;
    private static final float FIXED_DELTA = 1f / 60f;
    // Caps simulated time per enemy so scrubbing stays responsive. An enemy still moving at the cap
    // is drawn where it got to (not removed); settled patterns stop earlier on their own.
    private static final float MAX_SIMULATED_SECONDS = 20f;

    private final EditorDocument document;
    private final StageLibrary library;
    private final MovementPatternLibrary patternLibrary = new MovementPatternLibrary();
    private final Rectangle background = new Rectangle();
    private Float previewDistance;

    public PlayerPreviewView(EditorDocument document, StageLibrary library) {
        this.document = document;
        this.library = library;
        setPrefSize(WORLD_WIDTH * SCALE, WORLD_HEIGHT * SCALE);
        setMinSize(WORLD_WIDTH * SCALE, WORLD_HEIGHT * SCALE);
        // Stops the wrapping StackPane stretching this pane, so it stays centered.
        setMaxSize(WORLD_WIDTH * SCALE, WORLD_HEIGHT * SCALE);
        setStyle("-fx-background-color: #0c0d10;");
        // Clip to the screen like the game does, so off-screen enemies aren't drawn in the margin.
        setClip(new Rectangle(WORLD_WIDTH * SCALE, WORLD_HEIGHT * SCALE));
        background.setWidth(WORLD_WIDTH * SCALE);
        background.setHeight(WORLD_HEIGHT * SCALE);
        background.setFill(Color.web("#0c0d10"));
        getChildren().add(background);
        document.addChangeListener(this::rebuild);
    }

    public void setPreviewDistance(Float distance) {
        this.previewDistance = distance;
        rebuild();
    }

    private void rebuild() {
        getChildren().setAll(background);
        StageDefinition stageDef = document.getStageDefinition();
        if (stageDef == null || previewDistance == null) return;

        float cameraSpeed = document.getFile().cameraSpeed > 0 ? document.getFile().cameraSpeed : 1f;
        float elapsedSinceStart = previewDistance / cameraSpeed;

        drawBackground(stageDef, elapsedSinceStart);
        drawEnemies(cameraSpeed, stageDef);
    }

    // --- background (shared BackgroundWindowRenderer, same as StageCanvas) ---

    private void drawBackground(StageDefinition stageDef, float elapsedSinceStart) {
        if (stageDef.backgroundLayers == null) return;
        for (StageDefinition.BackgroundLayerDef layerDef : stageDef.backgroundLayers) {
            getChildren().addAll(BackgroundWindowRenderer.buildWindow(layerDef, elapsedSinceStart, WORLD_WIDTH, WORLD_HEIGHT, SCALE));
        }
    }

    // --- enemies (MovementPattern simulation in fixed steps from the spawn position) ---

    private void drawEnemies(float cameraSpeed, StageDefinition stageDef) {
        for (Trigger trigger : document.getTriggers()) {
            if (!isEnemySpawn(trigger)) continue;
            // Same arm distance as TriggerManager (spawn lead applied, clamped at 0).
            float armDistance = Math.max(0f, trigger.distance - trigger.spawnLead);
            if (armDistance > previewDistance) continue;
            if (Float.isNaN(trigger.x) || Float.isNaN(trigger.y)) continue;

            float elapsed = (previewDistance - armDistance) / cameraSpeed;

            EnemyDefinition def = library.findEnemy(trigger.type);
            if (def == null || def.texture == null) continue;

            float groundScrollSpeed = resolveGroundScrollSpeed(def, stageDef);

            if (trigger.waveShape != null) {
                drawWave(trigger, def, cameraSpeed, elapsed, groundScrollSpeed);
            } else {
                drawSpawn(trigger, def, loadPattern(trigger.movementPattern), trigger.inverseMovement, cameraSpeed, elapsed, groundScrollSpeed);
            }
        }
    }

    /** Draws each wave member, resolving movement and spawn lift the same way
     *  TriggerManager.fireWave() does (re-implemented here with MovementPatternDef objects rather
     *  than PatternRegistry ids). */
    private void drawWave(Trigger trigger, EnemyDefinition def, float cameraSpeed, float elapsed, float groundScrollSpeed) {
        Array<WaveSpawnPlanner.Slot> slots = WaveSpawnPlanner.plan(trigger, (float) WORLD_WIDTH / 2f, 1f);
        if (slots.size == 0) return;

        MovementPatternDef ownMovement = loadPattern(trigger.movementPattern);
        boolean hasOwnMovement = ownMovement != null;
        float fallbackAngle = trigger.waveKeepFormation && !hasOwnMovement ? sharedFallbackAngle(trigger) : 0f;
        float waveSpawnLift = trigger.waveKeepFormation ? computeWaveSpawnLift(trigger, slots, ownMovement) : Float.NaN;

        for (WaveSpawnPlanner.Slot slot : slots) {
            MovementPatternDef memberDef;
            if (trigger.waveKeepFormation) {
                memberDef = hasOwnMovement ? shiftedClone(ownMovement, slot.x - trigger.x, slot.y - trigger.y) : straightDef(trigger.waveSpeed, fallbackAngle);
            } else {
                memberDef = hasOwnMovement ? ownMovement : straightDef(trigger.waveSpeed, slot.angleDeg);
            }

            // Same per-member view as fireWave(): the slot's own y; the shared lift keeps the shape.
            Trigger memberView = new Trigger();
            memberView.x = slot.x;
            memberView.y = slot.y;
            memberView.enterFromAbove = trigger.enterFromAbove;
            memberView.spawnLead = trigger.spawnLead;
            memberView.distance = trigger.distance;
            memberView.waveSpawnLift = waveSpawnLift;

            drawSpawn(memberView, def, memberDef, trigger.inverseMovement, cameraSpeed, elapsed, groundScrollSpeed);
        }
    }

    /** Builds, steps and draws one enemy at view's x/y (the trigger, or a wave member's view),
     *  using EnemyEntranceMovement like the game. The sprite and movement are built before spawnY(),
     *  which needs the sprite height and the first waypoint.
     *  @param inverseMovement from the source trigger (wave member views don't carry it) */
    private void drawSpawn(Trigger view, EnemyDefinition def, MovementPatternDef movementDef, boolean inverseMovement,
                            float cameraSpeed, float elapsed, float groundScrollSpeed) {
        Sprite sprite = buildSprite(def, view, view.y);
        MovementPattern afterEntrance = resolveMovement(movementDef, sprite);
        if (view.enterFromAbove && !Float.isNaN(view.y)) {
            sprite.setPosition(view.x, EnemyEntranceMovement.spawnY(view, (float) WORLD_HEIGHT, sprite.getHeight(), afterEntrance));
        }
        MovementPattern entrance = EnemyEntranceMovement.build(view, cameraSpeed, (float) WORLD_HEIGHT, sprite.getWidth(), sprite.getHeight(), afterEntrance);
        MovementPattern movement = entrance != null ? entrance : afterEntrance;
        if (!stepMovement(movement, sprite, elapsed, inverseMovement, def.isGround, groundScrollSpeed)) return;

        drawEnemySprite(def, sprite);
    }

    /** As TriggerManager.singleAnchorProbe(): the one direction a kept formation flies in. */
    private float sharedFallbackAngle(Trigger trigger) {
        Trigger probe = new Trigger();
        probe.x = trigger.x;
        probe.y = trigger.y;
        probe.waveShape = "point";
        probe.waveOrientation = trigger.waveOrientation;
        probe.waveNumberOfSpawns = 1;
        return WaveSpawnPlanner.plan(probe, (float) WORLD_WIDTH / 2f, 1f).first().angleDeg;
    }

    /** As TriggerManager.registerShiftedClone(), without registering. */
    private static MovementPatternDef shiftedClone(MovementPatternDef source, float dx, float dy) {
        Json json = new Json();
        MovementPatternDef clone = json.fromJson(MovementPatternDef.class, json.toJson(source, MovementPatternDef.class));
        shiftTargets(clone, dx, dy);
        return clone;
    }

    private static void shiftTargets(MovementPatternDef def, float dx, float dy) {
        if (def == null) return;
        if (!Float.isNaN(def.targetX)) def.targetX += dx;
        if (!Float.isNaN(def.targetY)) def.targetY += dy;
        if (def.patterns != null) for (MovementPatternDef sub : def.patterns) shiftTargets(sub, dx, dy);
        shiftTargets(def.pattern, dx, dy);
    }

    /** As TriggerManager.registerStraight(), without registering. */
    private static MovementPatternDef straightDef(float speed, float angleDeg) {
        MovementPatternDef def = new MovementPatternDef();
        def.type = "Straight";
        def.speed = speed;
        def.movementAngle = angleDeg;
        return def;
    }

    /** As TriggerManager.computeWaveSpawnLift(). */
    private float computeWaveSpawnLift(Trigger trigger, Array<WaveSpawnPlanner.Slot> slots, MovementPatternDef ownMovement) {
        if (!trigger.enterFromAbove) return Float.NaN;
        float minSlotY = Float.POSITIVE_INFINITY;
        for (WaveSpawnPlanner.Slot slot : slots) minSlotY = Math.min(minSlotY, slot.y);
        float lift = (float) WORLD_HEIGHT - minSlotY;

        if (ownMovement != null && "WaypointPath".equals(ownMovement.type) && ownMovement.patterns != null && ownMovement.patterns.size > 0) {
            float baseTargetY = ownMovement.patterns.first().targetY;
            if (!Float.isNaN(baseTargetY)) lift = Math.max(lift, baseTargetY - trigger.y);
        }
        return lift;
    }

    /** As EntityManager.resolveGroundScrollSpeed(): its layer's scroll speed, else the stage default. */
    private float resolveGroundScrollSpeed(EnemyDefinition def, StageDefinition stageDef) {
        float stageDefault = stageDef.groundScrollSpeed != null ? stageDef.groundScrollSpeed : ScrollingBackground.DEFAULT_SCROLL_SPEED;
        if (def.backgroundLayer < 0 || stageDef.backgroundLayers == null || def.backgroundLayer >= stageDef.backgroundLayers.size) {
            return stageDefault;
        }
        float layerSpeed = stageDef.backgroundLayers.get(def.backgroundLayer).scrollSpeed;
        return Float.isNaN(layerSpeed) ? ScrollingBackground.DEFAULT_SCROLL_SPEED : layerSpeed;
    }

    private static boolean isEnemySpawn(Trigger t) {
        return t.type != null && t.sound == null && t.spriteTexture == null && t.setSpeed == null
            && !t.silence && !t.despawn && !t.waypointGem && t.swapWeaponId == null;
    }

    /** Sized and placed like GenericEnemy: def.size on the longer axis; x/y is the bottom-left corner.
     *  @param startY trigger.y, or the entrance spawn y */
    private Sprite buildSprite(EnemyDefinition def, Trigger trigger, float startY) {
        Image sheet = EnemySpriteImages.loadImage(def.texture);
        Sprite sprite = new Sprite();
        float worldW = def.size, worldH = def.size;
        if (sheet != null) {
            int columns = Math.max(def.columns, 1);
            int rows = Math.max(def.rows, 1);
            double frameW = sheet.getWidth() / columns;
            double frameH = sheet.getHeight() / rows;
            if (frameW > 0 && frameH > 0) {
                double aspect = frameW / frameH;
                worldW = (float) (aspect >= 1f ? def.size : def.size * aspect);
                worldH = (float) (aspect >= 1f ? def.size / aspect : def.size);
            }
        }
        sprite.setSize(worldW, worldH);
        sprite.setOriginCenter();
        sprite.setPosition(trigger.x, startY);
        return sprite;
    }

    /** The pattern for an id, or null if unset or missing. */
    private MovementPatternDef loadPattern(String patternId) {
        return (patternId != null && !patternId.isBlank() && patternLibrary.exists(patternId))
            ? patternLibrary.load(patternId) : null;
    }

    private MovementPattern resolveMovement(MovementPatternDef def, Sprite sprite) {
        return PatternFactory.createMovement(def, (float) WORLD_WIDTH, (float) WORLD_HEIGHT,
            sprite.getX() + sprite.getWidth() / 2f, Float.NaN, Float.NaN);
    }

    /** Steps the movement (plus ground scroll drift, as BaseEnemy.update() does) in fixed steps for
     *  up to `elapsed` seconds. Ground enemies keep drifting after their path settles.
     *
     *  Returns false once the pattern finishes, or the sprite leaves the screen after having been on
     *  it (the same grace GenericEnemy gives entering enemies). Checked every step, so an enemy the
     *  game would have removed is never drawn. Stops early once settled, unless it's a ground
     *  enemy. A nominal player at the bottom center stands in for the real one. */
    private boolean stepMovement(MovementPattern movement, Sprite sprite, float elapsed, boolean inverseMovement,
                                  boolean isGround, float groundScrollSpeed) {
        Circle dummyPlayerHitbox = new Circle((float) WORLD_WIDTH / 2f, 1f, 0.3f);
        com.badlogic.gdx.math.Rectangle rect = new com.badlogic.gdx.math.Rectangle(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
        float simulateSeconds = Math.min(elapsed, MAX_SIMULATED_SECONDS);
        boolean hasBeenOnScreen = false;
        for (float t = 0f; t < simulateSeconds; t += FIXED_DELTA) {
            if (movement.isFinished()) return false;
            boolean settled = movement.isSettled();
            if (!settled) {
                movement.update(FIXED_DELTA, sprite, rect, (float) WORLD_WIDTH, (float) WORLD_HEIGHT, dummyPlayerHitbox, inverseMovement);
            }
            if (isGround) {
                float dy = groundScrollSpeed * FIXED_DELTA;
                movement.applyGroundScroll(dy);
                sprite.translate(0f, dy);
                rect.setPosition(sprite.getX(), sprite.getY());
            }
            boolean outsideBounds = isOffScreen(sprite);
            if (!outsideBounds) {
                hasBeenOnScreen = true;
            } else if (hasBeenOnScreen) {
                return false;
            }
            if (settled && !isGround) break;
        }
        return !movement.isFinished();
    }

    /** GenericEnemy.isOffScreen()'s bounds. */
    private boolean isOffScreen(Sprite sprite) {
        return sprite.getY() < -sprite.getHeight() * 2f || sprite.getY() > WORLD_HEIGHT + sprite.getHeight() * 2f
            || sprite.getX() + sprite.getWidth() < -sprite.getWidth() * 2f || sprite.getX() > WORLD_WIDTH + sprite.getWidth() * 2f;
    }

    private void drawEnemySprite(EnemyDefinition def, Sprite sprite) {
        ImageView view = EnemySpriteImages.buildEnemyImage(def);
        if (view == null) return;
        // True size, without the icon size clamp.
        view.setFitWidth(sprite.getWidth() * SCALE);
        view.setFitHeight(sprite.getHeight() * SCALE);
        view.setLayoutX(sprite.getX() * SCALE);
        // Bottom-up world-Y -> top-down screen-Y, same flip the background layers above use.
        view.setLayoutY((WORLD_HEIGHT - (sprite.getY() + sprite.getHeight())) * SCALE);
        // libGDX rotates counterclockwise (Y up), JavaFX clockwise (Y down).
        view.setRotate(-sprite.getRotation());
        getChildren().add(view);
    }
}
