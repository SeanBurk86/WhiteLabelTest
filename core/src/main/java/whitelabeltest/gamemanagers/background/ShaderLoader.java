package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** Compiles GLSL from assets/shaders/ .vert/.frag files. */
public final class ShaderLoader {
    private ShaderLoader() {}

    static String read(String fileName) {
        return Gdx.files.internal("shaders/" + fileName).readString();
    }

    public static ShaderProgram compile(String label, String vertexFileName, String fragmentFileName) {
        return compileSource(label, read(vertexFileName), read(fragmentFileName));
    }

    /** compile() with `#define` lines prepended to the fragment source (for quality variants). Safe
     *  because none of the shaders declare a #version. */
    public static ShaderProgram compile(String label, String vertexFileName, String fragmentFileName, String... defines) {
        StringBuilder fragment = new StringBuilder();
        for (String define : defines) fragment.append("#define ").append(define).append('\n');
        return compileSource(label, read(vertexFileName), fragment.append(read(fragmentFileName)).toString());
    }

    static ShaderProgram compileSource(String label, String vertexSource, String fragmentSource) {
        ShaderProgram.pedantic = false;
        ShaderProgram program = new ShaderProgram(vertexSource, fragmentSource);
        if (!program.isCompiled()) {
            throw new IllegalStateException(label + " failed to compile: " + program.getLog());
        }
        return program;
    }
}
