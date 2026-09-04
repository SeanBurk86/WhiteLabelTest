package whitelabeltest.editor;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
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

/** "Player View" tab - a true simulation (not a static snapshot) of what the player actually sees
 *  at a scrubbed distance: background art scrolled to its real position, and every already-spawned
 *  enemy at its real, physics-stepped position - reusing the exact same MovementPattern
 *  implementations the game itself runs (PatternFactory.createMovement()), confirmed to run
 *  correctly with zero GL/LibGDX-Application context (Sprite/Rectangle/Circle are plain math
 *  objects; only Sprite.draw(Batch) needs a real GL context, which this class never calls - it reads
 *  back sprite.getX()/getY()/getRotation() and renders with a plain JavaFX ImageView instead).
 *
 * Deliberately scoped to background + enemy sprites only - no bullets, no player character, no
 * sprite/text cues. There is no "player" to meaningfully simulate here (bullets/AI depend on live
 * input this tool has none of); this mirrors Camera View's own scope (trigger/spawn timing, not
 * full gameplay). Not zoomable - unlike Camera View, this is meant to read as the game's own fixed
 * viewport, not an editing surface. */
public class PlayerPreviewView extends Pane {
    // Matches Main.PLAY_AREA_WIDTH/HEIGHT - see StageCanvas.WORLD_WIDTH's own "not read from the
    // game module since it's private there" doc.
    private static final double WORLD_WIDTH = 9.0;
    private static final double WORLD_HEIGHT = 12.0;
    private static final double SCALE = StageCanvas.PIXELS_PER_UNIT_X;
    private static final float FIXED_DELTA = 1f / 60f;
    // Caps how much simulated flight time any single enemy's stepMovement() call actually steps
    // through, regardless of how far previewDistance is scrubbed past its own spawn distance - keeps
    // a scrub-drag (which rebuilds this view on every tick) responsive even on a stage with 100+
    // spawns. NOT "treat as gone past this point" (an enemy can now legitimately hold at a fixed
    // on-screen position forever - see WaypointPathMovement.isFinished()'s own doc - so scrubbing far
    // enough past its spawn will always eventually exceed ANY fixed cutoff); stepMovement() only ever
    // stops here for a pattern that's still genuinely in motion this far out, drawing it wherever
    // that many steps land it rather than skipping it - see that method's own doc. A pattern that
    // reaches MovementPattern.isSettled() (a held WaypointPathMovement, in practice) stops stepping
    // well before this on its own, at its true final position, every time.
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
        // Without this, EditorApp's StackPane wrapper (which centers a child ONLY if it isn't
        // resized to fill available space) stretches this Pane to the whole tab area instead - its
        // own content stays pinned at its absolute world-space layoutX/Y coordinates near (0,0)
        // regardless, so the stretch just left it reading as pinned to the top-left instead of
        // centered. Capping max size to match pref/min stops the stretch.
        setMaxSize(WORLD_WIDTH * SCALE, WORLD_HEIGHT * SCALE);
        setStyle("-fx-background-color: #0c0d10;");
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

    // --- background - a single BackgroundWindowRenderer.buildWindow() call per layer (see that
    // class's own doc), so this view and StageCanvas's own background rendering can never drift
    // apart the way two independent reimplementations of the same formula once did. ---------------

    private void drawBackground(StageDefinition stageDef, float elapsedSinceStart) {
        if (stageDef.backgroundLayers == null) return;
        for (StageDefinition.BackgroundLayerDef layerDef : stageDef.backgroundLayers) {
            getChildren().addAll(BackgroundWindowRenderer.buildWindow(layerDef, elapsedSinceStart, WORLD_WIDTH, WORLD_HEIGHT, SCALE));
        }
    }

    // --- enemies - real MovementPattern simulation, stepped forward in fixed increments from each
    // spawn's authored start position exactly like GenericEnemy.initWithDefinition() sets it up. ---

    private void drawEnemies(float cameraSpeed, StageDefinition stageDef) {
        for (Trigger trigger : document.getTriggers()) {
            if (!isEnemySpawn(trigger)) continue;
            // Mirrors TriggerManager.update()'s own (identically-clamped) armDistance - see
            // Trigger.spawnLead's own doc - so a trigger authored to spawn early shows up (and has
            // had that much extra simulated time) here exactly as early as it actually does in real
            // gameplay, including a lead bigger than the trigger's own distance meaning "present from
            // distance 0", not "never" (the camera can't have covered negative distance).
            float armDistance = Math.max(0f, trigger.distance - trigger.spawnLead);
            if (armDistance > previewDistance) continue;
            if (Float.isNaN(trigger.x) || Float.isNaN(trigger.y)) continue;

            float elapsed = (previewDistance - armDistance) / cameraSpeed;

            EnemyDefinition def = library.findEnemy(trigger.type);
            if (def == null || def.texture == null) continue;

            // See EnemyEntranceMovement's own doc - the exact same computation TriggerManager.fire()
            // (via GenericEnemy.initWithDefinition()) uses in the real game, so this preview can
            // never drift out of sync with it the way this class's own background rendering once did
            // against StageCanvas's independent reimplementation of the same formula. spawnY() now
            // NEEDS the real sprite height (see its own doc on why a fixed margin was wrong), so the
            // sprite has to be built first (at its ordinary arrival position) and then repositioned -
            // and, same as GenericEnemy.initWithDefinition()'s own order, the movement pattern has to
            // be resolved BEFORE spawnY() too, so a WaypointPathMovement's own first-leg target can
            // raise the spawn point above itself, not just above trigger.y - see the 4-arg spawnY()
            // overload's own doc.
            Sprite sprite = buildSprite(def, trigger, trigger.y);
            MovementPattern afterEntrance = resolveMovement(trigger, sprite);
            if (trigger.enterFromAbove && !Float.isNaN(trigger.y)) {
                sprite.setPosition(trigger.x, EnemyEntranceMovement.spawnY(trigger, (float) WORLD_HEIGHT, sprite.getHeight(), afterEntrance));
            }
            MovementPattern entrance = EnemyEntranceMovement.build(trigger, cameraSpeed, (float) WORLD_HEIGHT, sprite.getWidth(), sprite.getHeight(), afterEntrance);
            MovementPattern movement = entrance != null ? entrance : afterEntrance;
            float groundScrollSpeed = resolveGroundScrollSpeed(def, stageDef);
            if (!stepMovement(movement, sprite, elapsed, trigger.inverseMovement, def.isGround, groundScrollSpeed)) continue;

            drawEnemySprite(def, sprite);
        }
    }

    /** Mirrors EntityManager.resolveGroundScrollSpeed()/GameController.loadStage()'s own resolution
     *  exactly: a ground enemy attached to a specific parallax layer (def.backgroundLayer >= 0)
     *  drifts at THAT layer's own scrollSpeed instead of the stage-wide default, so it stays visually
     *  planted on whichever layer it's actually drawn against - see EnemyDefinition.backgroundLayer's
     *  own doc. Unused (never even read) for a non-ground enemy - see stepMovement(). */
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

    /** Same sizing/positioning GenericEnemy.initWithDefinition() actually does: def.size along the
     *  frame's longer axis, sprite.setX/Y(trigger.x/startY) directly (the sprite's own bottom-left
     *  corner, NOT a center - matches the real game's convention exactly, even though StageCanvas's
     *  own trigger icons visually center on trigger.x/y for placement purposes).
     *  @param startY trigger.y normally, or EnemyEntranceMovement.spawnY() when this spawn enters
     *  from above - see the one caller's own doc. */
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

    private MovementPattern resolveMovement(Trigger trigger, Sprite sprite) {
        String patternId = trigger.movementPattern;
        MovementPatternDef def = (patternId != null && !patternId.isBlank() && patternLibrary.exists(patternId))
            ? patternLibrary.load(patternId) : null;
        return PatternFactory.createMovement(def, (float) WORLD_WIDTH, (float) WORLD_HEIGHT,
            sprite.getX() + sprite.getWidth() / 2f, Float.NaN, Float.NaN);
    }

    /** Steps `movement` forward in fixed increments for up to `elapsed` seconds (clamped to
     *  MAX_SIMULATED_SECONDS - see that field's own doc), mutating `sprite` in place - the exact same
     *  MovementPattern.update() + BaseEnemy.applyGroundScroll() sequence BaseEnemy.update() drives
     *  every frame in the real game, ground enemies included (see isGround/groundScrollSpeed - the
     *  real game's per-frame ground-scroll drift applies to EVERY frame of a ground enemy's life,
     *  including well after its own movement pattern has settled at a fixed point: an enemy "parked"
     *  by a WaypointPath is still drawn against a scrolling background, so it keeps sliding down the
     *  screen with it and can still go off-screen on its own well after its path is done, exactly the
     *  way the real game's identical per-frame math does - simulating movement alone and stopping the
     *  instant it settles, this method's original shape before ground enemies were accounted for,
     *  left a settled ground enemy frozen in place forever in this preview while the real game kept
     *  scrolling it toward - and eventually off - the bottom of the screen).
     *
     * Returns false once the pattern reports finished, OR the sprite goes off-screen (same bounds
     * GenericEnemy.isOffScreen() checks) - checked INSIDE the loop, not just at the end, so stepping
     * stops the instant the real game would have despawned this enemy rather than continuing to burn
     * cycles on (and risk rendering) a pattern that happens to bring it back into view later, which
     * the real game would never see either since the entity's already gone by then. Once isSettled()
     * is true, movement.update() itself is skipped (it can only ever recompute the exact same
     * position from here - see MovementPattern.isSettled()'s own doc) but the loop keeps running for
     * a ground enemy (still applying drift/off-screen checks each step) and only truly breaks early
     * once there's nothing left ANY further step could change - settled AND not ground. A fixed
     * nominal player hitbox (bottom-center of the play area) stands in for the real player, since a
     * scrub preview has no live player to aim a "player direction" orientation waypoint at. */
    private boolean stepMovement(MovementPattern movement, Sprite sprite, float elapsed, boolean inverseMovement,
                                  boolean isGround, float groundScrollSpeed) {
        Circle dummyPlayerHitbox = new Circle((float) WORLD_WIDTH / 2f, 1f, 0.3f);
        com.badlogic.gdx.math.Rectangle rect = new com.badlogic.gdx.math.Rectangle(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
        float simulateSeconds = Math.min(elapsed, MAX_SIMULATED_SECONDS);
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
            if (isOffScreen(sprite)) return false;
            if (settled && !isGround) break;
        }
        return !movement.isFinished();
    }

    /** Same bounds GenericEnemy.isOffScreen() checks (its own trailing fallback, after the
     *  movement.isFinished() check this class already applies separately in stepMovement()). */
    private boolean isOffScreen(Sprite sprite) {
        return sprite.getY() < -sprite.getHeight() * 2f || sprite.getY() > WORLD_HEIGHT + sprite.getHeight() * 2f
            || sprite.getX() + sprite.getWidth() < -sprite.getWidth() * 2f || sprite.getX() > WORLD_WIDTH + sprite.getWidth() * 2f;
    }

    private void drawEnemySprite(EnemyDefinition def, Sprite sprite) {
        ImageView view = EnemySpriteImages.buildEnemyImage(def);
        if (view == null) return;
        // Re-applies the same width/height buildEnemyImage() already computed internally, but
        // WITHOUT its MIN/MAX_SPRITE_PIXELS clamp (meant for TriggerNode's small draggable canvas
        // icon, not a real-scale gameplay preview) - a genuinely huge boss should render huge here,
        // matching true in-game size, not artificially capped.
        view.setFitWidth(sprite.getWidth() * SCALE);
        view.setFitHeight(sprite.getHeight() * SCALE);
        view.setLayoutX(sprite.getX() * SCALE);
        // Bottom-up world-Y -> top-down screen-Y, same flip the background layers above use.
        view.setLayoutY((WORLD_HEIGHT - (sprite.getY() + sprite.getHeight())) * SCALE);
        // libGDX rotation is counterclockwise-positive in its Y-up world space; JavaFX rotation is
        // clockwise-positive in Y-down screen space - negating keeps the visual facing consistent
        // with the same Y-flip every position above already applies.
        view.setRotate(-sprite.getRotation());
        getChildren().add(view);
    }
}
