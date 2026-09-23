package whitelabeltest.enemy;

/** One named, alternate sprite-sheet animation an enemy can switch to mid-fight - an entry in
 *  EnemyDefinition.animations, selected by a HealthPhase's `animation` key (see
 *  BaseEnemy.enterHealthPhase()). Same sheet-layout fields as EnemyDefinition's own base animation
 *  (texture/frameCount/columns/rows/frameDuration), so a boss whose phases each have their own art
 *  (idle, kick, run...) is authored as one definition plus a few of these rather than as separate
 *  enemies swapped in by triggers. */
public class EnemyAnimationDef {
    public String texture;
    public int frameCount = 1;
    // 0 = every frame on one row (columns = frameCount), same convention as EnemyDefinition.columns.
    public int columns = 0;
    public int rows = 1;
    public float frameDuration = 0.1f;
    // False plays the sheet once and holds its last frame instead of looping.
    public boolean loop = true;

    public EnemyAnimationDef() {}
}
