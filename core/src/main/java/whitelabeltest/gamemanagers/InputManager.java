package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.math.Vector2;

public class InputManager {
    private final Vector2 moveDirection = new Vector2();
    private boolean isShooting;
    private boolean bombJustPressed;
    private boolean restartJustPressed;
    private boolean quitJustPressed;
    private boolean debugToggleJustPressed;

    public void update() {
        // Reset state
        moveDirection.set(0, 0);
        isShooting = false;
        bombJustPressed = false;
        restartJustPressed = false;
        quitJustPressed = false;
        debugToggleJustPressed = false;

        // Keyboard Movement
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) moveDirection.x -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) moveDirection.x += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) moveDirection.y += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) moveDirection.y -= 1;

        // Keyboard Actions
        isShooting = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        bombJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT);
        restartJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.R);
        quitJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.Q);
        debugToggleJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.F12);

        // Gamepad Input
        Controller controller = Controllers.getCurrent();
        if (controller != null) {
            // Analog sticks
            float axisX = controller.getAxis(controller.getMapping().axisLeftX);
            float axisY = controller.getAxis(controller.getMapping().axisLeftY);

            if (Math.abs(axisX) > 0.2f) moveDirection.x += axisX;
            if (Math.abs(axisY) > 0.2f) moveDirection.y -= axisY;

            // D-Pad Support
            if (controller.getButton(controller.getMapping().buttonDpadLeft)) moveDirection.x -= 1;
            if (controller.getButton(controller.getMapping().buttonDpadRight)) moveDirection.x += 1;
            if (controller.getButton(controller.getMapping().buttonDpadUp)) moveDirection.y += 1;
            if (controller.getButton(controller.getMapping().buttonDpadDown)) moveDirection.y -= 1;

            // Buttons
            isShooting |= controller.getButton(controller.getMapping().buttonA);
            isShooting |= controller.getButton(controller.getMapping().buttonR1);

            if (controller.getButton(controller.getMapping().buttonB)) bombJustPressed = true;

            if (controller.getButton(controller.getMapping().buttonStart)) restartJustPressed = true;
            if (controller.getButton(controller.getMapping().buttonBack)) quitJustPressed = true;
        }

        if (moveDirection.len() > 1.0f) {
            moveDirection.nor();
        }
    }

    public Vector2 getMoveDirection() { return moveDirection; }
    public boolean isShooting() { return isShooting; }
    public boolean isBombJustPressed() {return bombJustPressed; }
    public boolean isRestartJustPressed() { return restartJustPressed; }
    public boolean isQuitJustPressed() { return quitJustPressed; }
    public boolean isDebugToggleJustPressed() { return debugToggleJustPressed; }
}
