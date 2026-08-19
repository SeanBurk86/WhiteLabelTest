package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class TutorialBoxTunnelShader implements BackgroundShader {
    private static final String VERTEX_SHADER =
        "attribute vec4 a_position;\n" +
        "attribute vec4 a_color;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main() {\n" +
        "    v_texCoords = a_texCoord0;\n" +
        "    gl_Position = u_projTrans * a_position;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "#ifdef GL_ES\n" +
        "precision highp float;\n" +
        "#endif\n" +
        "varying vec2 v_texCoords;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_resolution;\n" +
        "\n" +
        "float gTime = 0.0;\n" +
        "\n" +
        "mat2 rot(float a) {\n" +
        "    float c = cos(a), s = sin(a);\n" +
        "    return mat2(c, s, -s, c);\n" +
        "}\n" +
        "\n" +
        "float sdBox(vec3 p, vec3 b) {\n" +
        "    vec3 q = abs(p) - b;\n" +
        "    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);\n" +
        "}\n" +
        "\n" +
        "float box(vec3 pos, float scale) {\n" +
            "pos *= scale;\n" +
            "float base = sdBox(pos, vec3(.4,.6,.2)) /1.5;\n" +
            "pos.xy *= 5.;\n" +
            "pos.y -= 3.5;\n" +
            "pos.xy *= rot(.75);\n" +
            "float cut = sdBox(pos  , vec3(1.,1.,1.));\n" +
            "float result = min(-base,cut) *2.;\n" +
            "return result;\n" +
            "}" +
        "\n" +
        "float box_set(vec3 pos) {\n" +
        "    vec3 pos_origin = pos;\n" +
        "    pos = pos_origin;\n" +
        "    pos.y += sin(gTime * 0.4) * 2.5;\n" +
        "    pos.xy *= rot(0.8);\n" +
        "    float box1 = box(pos, 2.0 - abs(sin(gTime * 0.4)) * 1.5);\n" +
        "    pos = pos_origin;\n" +
        "    pos.y -= sin(gTime * 0.4) * 2.5;\n" +
        "    pos.xy *= rot(0.8);\n" +
        "    float box2 = box(pos, 2.0 - abs(sin(gTime * 0.4)) * 1.5);\n" +
        "    pos = pos_origin;\n" +
        "    pos.x += sin(gTime * 0.4) * 2.5;\n" +
        "    pos.xy *= rot(0.8);\n" +
        "    float box3 = box(pos, 2.0 - abs(sin(gTime * 0.4)) * 1.5);\n" +
        "    pos = pos_origin;\n" +
        "    pos.x -= sin(gTime * 0.4) * 2.5;\n" +
        "    pos.xy *= rot(0.8);\n" +
        "    float box4 = box(pos, 2.0 - abs(sin(gTime * 0.4)) * 1.5);\n" +
        "    pos = pos_origin;\n" +
        "    pos.xy *= rot(0.8);\n" +
        "    float box5 = box(pos, 0.5) * 6.0;\n" +
        "    pos = pos_origin;\n" +
        "    float box6 = box(pos, 0.5) * 6.0;\n" +
        "    float result = max(max(max(max(max(box1, box2), box3), box4), box5), box6);\n" +
        "    return result;\n" +
        "}\n" +
        "\n" +
        "float map(vec3 pos) {\n" +
        "    return box_set(pos);\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 fragCoord = v_texCoords * u_resolution;\n" +
        "    vec2 p = (fragCoord.xy * 2.0 - u_resolution.xy) / min(u_resolution.x, u_resolution.y);\n" +
        "    vec3 ro = vec3(0.0, -0.2, u_time * 4.0);\n" +
        "    vec3 ray = normalize(vec3(p, 1.5));\n" +
        "    ray.xy = ray.xy * rot(sin(u_time * 0.03) * 5.0);\n" +
        "    ray.yz = ray.yz * rot(sin(u_time * 0.05) * 0.2);\n" +
        "    float t = 0.1;\n" +
        "    vec3 col = vec3(0.0);\n" +
        "    float ac = 0.0;\n" +
        "\n" +
        "    for (int i = 0; i < 99; i++) {\n" +
        "        vec3 pos = ro + ray * t;\n" +
        "        pos = mod(pos - 2.0, 4.0) - 2.0;\n" +
        "        gTime = u_time - float(i) * 0.01;\n" +
        "\n" +
        "        float d = map(pos);\n" +
        "\n" +
        "        d = max(abs(d), 0.01);\n" +
        "        ac += exp(-d * 23.0);\n" +
        "\n" +
        "        t += d * 0.55;\n" +
        "    }\n" +
        "\n" +
        "    col = vec3(ac * 0.02);\n" +
        "    col += vec3(0.35 + sin(u_time) * 0.2);\n" +
        "\n" +
        "    gl_FragColor = vec4(col, 1.0 - t * (0.02 + 0.02 * sin(u_time)));\n" +
        "}\n";

    private final ShaderProgram shader;
    private float time;

    public TutorialBoxTunnelShader() {
        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("TutorialBoxTunnelShader failed to compile: " + shader.getLog());
        }
    }

    @Override
    public void update(float delta) {
        time += delta;
    }

    @Override
    public void resetTime() {
        time = 0f;
    }

    @Override
    public void render(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();

        batch.setShader(shader);
        batch.setColor(Color.WHITE);

        shader.setUniformf("u_time", time);
        shader.setUniformf("u_resolution", worldWidth, worldHeight);

        batch.draw(quadTexture, 0, 0, worldWidth, worldHeight);

        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
    }

    @Override
    public void dispose() {
        shader.dispose();
    }
}
