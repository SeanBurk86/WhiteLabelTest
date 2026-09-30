package whitelabeltest.headless;

import com.badlogic.gdx.graphics.GL20;

import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/** A GL20 that accepts every call and draws nothing. The headless backend leaves Gdx.gl null, but
 *  the simulation still creates Textures: sprite and hitbox sizes come from their dimensions,
 *  which Pixmap decoding provides without a GPU.
 *
 *  glGen* calls return fresh ids, status queries report success, and everything else returns
 *  0 / false / "". */
final class NoOpGL {
    private NoOpGL() {}

    static GL20 create() {
        AtomicInteger nextId = new AtomicInteger(1);
        return (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[] { GL20.class }, (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "glCheckFramebufferStatus":
                    return GL20.GL_FRAMEBUFFER_COMPLETE;
                case "glGetShaderiv":
                case "glGetProgramiv":
                    // Report compile/link success so a ShaderProgram created by mistake doesn't throw.
                    if (args != null && args.length == 3 && args[2] instanceof IntBuffer buffer) buffer.put(0, 1);
                    return null;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "NoOpGL";
                default:
                    break;
            }
            if (name.startsWith("glGen") || name.equals("glCreateShader") || name.equals("glCreateProgram")) {
                if (method.getReturnType() == int.class) return nextId.getAndIncrement();
            }
            Class<?> type = method.getReturnType();
            if (type == int.class) return 0;
            if (type == boolean.class) return false;
            if (type == float.class) return 0f;
            if (type == long.class) return 0L;
            if (type == String.class) return "";
            return null;
        });
    }
}
