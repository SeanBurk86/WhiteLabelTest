package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** Reads GLSL source out of assets/shaders/ and compiles it, so shader source lives in .vert/.frag
 *  files (editable with normal GLSL tooling/syntax highlighting) instead of Java string literals. */
public final class ShaderLoader {
    private ShaderLoader() {}

    static String read(String fileName) {
        return Gdx.files.internal("shaders/" + fileName).readString();
    }

    public static ShaderProgram compile(String label, String vertexFileName, String fragmentFileName) {
        return compileSource(label, read(vertexFileName), read(fragmentFileName));
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
