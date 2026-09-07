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
        // A plain Pane never clips its own children to its layout bounds - so an enemy sitting
        // above/below/beside the play area (any entrance-from-above spawn while its own descent is
        // still in flight, especially a waveSpawnLift-boosted wave member - see stepMovement()'s own
        // doc) was being drawn in full at its real, off-screen position in the surrounding
        // EditorApp.wrapPlayerView() StackPane margin, rather than staying invisible there the way
        // the real game's own fixed camera viewport naturally makes it (nothing outside the
        // rendered framebuffer's bounds is ever drawn at all). Seeing it float there and only THEN
        // cross into this black rectangle read as a sudden "pop in" the instant it arrived, not the
        // gradual glide-in a clipped viewport actually shows. Sized to exactly this Pane's own
        // fixed WORLD_WIDTH/HEIGHT*SCALE bounds - the same rectangle `background` below fills - so
        // nothing outside it is ever visible, before OR after its entrance.
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

            float groundScrollSpeed = resolveGroundScrollSpeed(def, stageDef);

            if (trigger.waveShape != null) {
                drawWave(trigger, def, cameraSpeed, elapsed, groundScrollSpeed);
            } else {
                drawSpawn(trigger, def, loadPattern(trigger.movementPattern), trigger.inverseMovement, cameraSpeed, elapsed, groundScrollSpeed);
            }
        }
    }

    /** Fans a wave trigger out into every WaveSpawnPlanner.plan() member and draws each one
     *  independently, instead of the single anchor-point stand-in this used to draw - mirrors
     *  TriggerManager.fireWave()'s own per-member movement resolution (registerShiftedClone() under
     *  waveKeepFormation+an authored pattern, one shared synthesized angle under waveKeepFormation
     *  alone via singleAnchorProbe(), or each member flying independently otherwise) and
     *  computeWaveSpawnLift() - both duplicated locally (shiftedClone()/sharedFallbackAngle()/
     *  computeWaveSpawnLift() below) since this class resolves movement from a loaded
     *  MovementPatternDef object directly rather than through TriggerManager's PatternRegistry ids -
     *  so an enterFromAbove wave's members clear the exact same off-screen spawn height here as they
     *  do in real gameplay, the same reasoning EnemyEntranceMovement's own doc gives for sharing
     *  spawnY()/build() between the two. Without this, a wave with an enterFromAbove member (e.g.
     *  stage1's second IceSkull wave) could compute an anchor-only spawn/entrance that lands
     *  off-screen or reports isFinished() prematurely, silently skipping the draw entirely even
     *  though the underlying movement math was running fine. */
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

            // Same per-member entranceView shape TriggerManager.fireWave() builds - see that
            // method's own doc on why entranceView.y is always this member's own true slot.y with
            // no formation-wide adjustment, and why waveSpawnLift is the single additive delta that
            // preserves the formation's real shape instead.
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

    /** Builds, steps, and draws exactly one enemy sprite at `view`'s own x/y - shared by the
     *  ordinary single-spawn path (view == the trigger itself) and drawWave()'s per-member loop
     *  (view == a synthetic per-member entranceView). See EnemyEntranceMovement's own doc - the
     *  exact same computation TriggerManager.fire()/fireWave() (via GenericEnemy.
     *  initWithDefinition()) use in the real game, so this preview can never drift out of sync with
     *  it the way this class's own background rendering once did against StageCanvas's independent
     *  reimplementation of the same formula. spawnY() now NEEDS the real sprite height (see its own
     *  doc on why a fixed margin was wrong), so the sprite has to be built first (at its ordinary
     *  arrival position) and then repositioned - and, same as GenericEnemy.initWithDefinition()'s
     *  own order, the movement pattern has to be resolved BEFORE spawnY() too, so a
     *  WaypointPathMovement's own first-leg target can raise the spawn point above itself, not just
     *  above view.y - see the 4-arg spawnY() overload's own doc.
     *  @param inverseMovement always the SOURCE trigger's own field, never `view`'s (a wave's
     *  synthetic per-member entranceView has no inverseMovement of its own) - matches
     *  TriggerManager's dispatchPendingWaveSpawns() reading pending.source.inverseMovement rather
     *  than the per-member entranceView. */
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

    /** Same degenerate-anchor probe TriggerManager.singleAnchorProbe() uses - asks WaveSpawnPlanner
     *  for the angle it would compute for a single "point"-shape member sitting exactly at the
     *  anchor, so every member of a waveKeepFormation wave with no authored movementPattern shares
     *  the identical synthesized direction (a rigid body can't fly toward its own center) instead of
     *  fanning out per-member the way the independent, non-formation case does. */
    private float sharedFallbackAngle(Trigger trigger) {
        Trigger probe = new Trigger();
        probe.x = trigger.x;
        probe.y = trigger.y;
        probe.waveShape = "point";
        probe.waveOrientation = trigger.waveOrientation;
        probe.waveNumberOfSpawns = 1;
        return WaveSpawnPlanner.plan(probe, (float) WORLD_WIDTH / 2f, 1f).first().angleDeg;
    }

    /** Deep-clones `source` (a Json round-trip) and shifts every absolute target coordinate found
     *  anywhere in it by (dx, dy) - mirrors TriggerManager.registerShiftedClone()/shiftTargets()
     *  exactly, minus the PatternRegistry synthetic-id registration that method also does: this
     *  class hands a MovementPatternDef straight to PatternFactory.createMovement() rather than
     *  resolving one by id, so there's nothing to register the clone under. */
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

    /** A plain "Straight" MovementPatternDef - mirrors TriggerManager.registerStraight(), minus the
     *  PatternRegistry registration for the same reason shiftedClone() skips it. */
    private static MovementPatternDef straightDef(float speed, float angleDeg) {
        MovementPatternDef def = new MovementPatternDef();
        def.type = "Straight";
        def.speed = speed;
        def.movementAngle = angleDeg;
        return def;
    }

    /** Mirrors TriggerManager.computeWaveSpawnLift() exactly (see that method's own doc for the
     *  full reasoning) - takes the already-loaded `ownMovement` def directly rather than re-resolving
     *  it from a PatternRegistry id. */
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

    /** Resolves whichever pattern a trigger's own `movementPattern` id refers to, or null if it's
     *  unset or doesn't resolve to a file that actually exists on disk - see
     *  MovementPatternLibrary.resolveForTrigger()'s own doc (this takes a bare id instead of a
     *  Trigger since drawWave()'s per-member callers need to resolve the SOURCE wave trigger's own
     *  id once, up front, rather than per member). */
    private MovementPatternDef loadPattern(String patternId) {
        return (patternId != null && !patternId.isBlank() && patternLibrary.exists(patternId))
            ? patternLibrary.load(patternId) : null;
    }

    private MovementPattern resolveMovement(MovementPatternDef def, Sprite sprite) {
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
     * GenericEnemy.isOffScreen() checks) AFTER having already been on-screen at least once - the
     * same hasBeenOnScreen grace period that method itself applies (see its own doc): a spawn point
     * that starts outside those bounds on purpose (any entrance-from-above enemy, and ESPECIALLY a
     * waveSpawnLift-boosted wave member - see Trigger.waveSpawnLift's own doc on why every member
     * shares one lift sized for the formation's LOWEST member, so every other member spawns even
     * further above the tolerance) is never treated as "gone" for still being outside it during the
     * one/several real frames its entrance movement needs to actually carry it down - only once
     * it's genuinely been seen does wandering back out remove it, exactly as GenericEnemy's own
     * check does. Without this grace, a wave member whose spawnY sits meaningfully above the
     * single-spawn case's own (deliberately tight) margin - which every member but the formation's
     * single lowest one does, by construction - got silently dropped after its very first simulated
     * frame here, never drawn at all, even though the real game would have kept it alive the whole
     * time its entrance was carrying it down. Checked INSIDE the loop, not just at the end, so
     * stepping stops the instant the real game would have despawned this enemy (once truly gone,
     * not just not-yet-arrived) rather than continuing to burn cycles on (and risk rendering) a
     * pattern that happens to bring it back into view later, which the real game would never see
     * either since the entity's already gone by then. Once isSettled() is true, movement.update()
     * itself is skipped (it can only ever recompute the exact same position from here - see
     * MovementPattern.isSettled()'s own doc) but the loop keeps running for a ground enemy (still
     * applying drift/off-screen checks each step) and only truly breaks early once there's nothing
     * left ANY further step could change - settled AND not ground. A fixed nominal player hitbox
     * (bottom-center of the play area) stands in for the real player, since a scrub preview has no
     * live player to aim a "player direction" orientation waypoint at. */
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
