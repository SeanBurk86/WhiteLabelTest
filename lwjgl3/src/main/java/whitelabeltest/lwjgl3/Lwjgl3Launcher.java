package whitelabeltest.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import whitelabeltest.Main;
import whitelabeltest.gamemanagers.input.InputType;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
        // gdx-video leaves a non-daemon thread running that would keep the JVM alive after the window closes.
        System.exit(0);
    }

    /** The editor's Quick Play settings: quickPlay.* system properties (passed through from
     *  -PquickPlay* Gradle properties by lwjgl3/build.gradle). Null when quickPlay.stage isn't set. */
    private static Main.QuickPlayConfig readQuickPlayConfig() {
        String stageId = System.getProperty("quickPlay.stage");
        if (stageId == null || stageId.isBlank()) return null;
        float distance = parseFloat(System.getProperty("quickPlay.distance"), 0f);
        String slotA = System.getProperty("quickPlay.slotA");
        String slotB = System.getProperty("quickPlay.slotB");
        int slotALevel = parseInt(System.getProperty("quickPlay.slotALevel"), 0);
        int slotBLevel = parseInt(System.getProperty("quickPlay.slotBLevel"), 0);
        InputType inputType = parseInputType(System.getProperty("quickPlay.input"));
        return new Main.QuickPlayConfig(stageId, distance, blankToNull(slotA), blankToNull(slotB), slotALevel, slotBLevel, inputType);
    }

    /** An InputType name; KEYBOARD if missing or unknown. */
    private static InputType parseInputType(String value) {
        if (value == null || value.isBlank()) return InputType.KEYBOARD;
        try {
            return InputType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return InputType.KEYBOARD;
        }
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new Main(readQuickPlayConfig()), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("WhiteLabelTest");
        //// Vsync limits the frames per second to what your hardware can display, and helps eliminate
        //// screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
        configuration.useVsync(true);
        // A fallback cap only; vsync must pace frames. 2x refresh, because GLFW rounds fractional refresh
        // rates down, and a cap below the real rate causes periodic missed vsyncs.
        // -Dperf.fpsCap=<n> overrides it.
        configuration.setForegroundFPS(Integer.getInteger("perf.fpsCap", Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate * 2));
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.

        // Set to full screen mode using the current display mode
        configuration.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());

        //// You can change these files; they are in lwjgl3/src/main/resources/ .
        //// They can also be loaded from the root of assets/ .
        configuration.setWindowIcon("icons/testskull128.png", "icons/testskull64.png", "icons/testskull32.png", "icons/testskull16.png");

        // 64 simultaneous sources (default 16 runs out and audio drops out); default buffers.
        configuration.setAudioConfig(64, 512, 9);

        //// This could improve compatibility with Windows machines with buggy OpenGL drivers, Macs
        //// with Apple Silicon that have to emulate compatibility with OpenGL anyway, and more.
        //// This uses the dependency `com.badlogicgames.gdx:gdx-lwjgl3-angle` to function.
        //// You would need to add this line to lwjgl3/build.gradle , below the dependency on `gdx-backend-lwjgl3`:
        ////     implementation "com.badlogicgames.gdx:gdx-lwjgl3-angle:$gdxVersion"
        //// You can choose to add the following line and the mentioned dependency if you want; they
        //// are not intended for games that use GL30 (which is compatibility with OpenGL ES 3.0).
        //// Know that it might not work well in some cases.
//        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0);

        return configuration;
    }
}
