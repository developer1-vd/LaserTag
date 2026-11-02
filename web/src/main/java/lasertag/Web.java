package lasertag;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Calls into the browser glue (glue.js, the global {@code LT} object). */
final class Web {
    private Web() {}

    @JSFunctor
    interface Frame extends JSObject {
        void run(double dt);
    }

    @JSBody(params = "frame", script = "LT.start(frame);")
    static native void start(Frame frame);

    @JSBody(params = {"x", "y"}, script = "LT.begin(x, y);")
    static native void begin(int shakeX, int shakeY);

    @JSBody(params = {"x", "y", "w", "h", "c"}, script = "LT.fill(x, y, w, h, c);")
    static native void fill(int x, int y, int w, int h, int argb);

    @JSBody(params = {"x0", "y0", "x1", "y1", "w", "c"}, script = "LT.line(x0, y0, x1, y1, w, c);")
    static native void line(int x0, int y0, int x1, int y1, int width, int argb);

    @JSBody(params = "key", script = "return LT.hasCache(key);")
    static native boolean hasCache(String key);

    @JSBody(params = {"key", "w", "h"}, script = "LT.beginCache(key, w, h);")
    static native void beginCache(String key, int w, int h);

    @JSBody(script = "LT.endCache();")
    static native void endCache();

    @JSBody(params = {"key", "x", "y"}, script = "LT.drawCache(key, x, y);")
    static native void drawCache(String key, int x, int y);

    @JSBody(params = "on", script = "LT.scanlines(on);")
    static native void scanlines(boolean on);

    @JSBody(params = "code", script = "return LT.key(code);")
    static native boolean key(String code);

    /** The next key pressed since the last call (a KeyboardEvent.code), or null. */
    @JSBody(script = "return LT.nextKey();")
    static native String nextKey();

    @JSBody(script = "return LT.mouseX();")
    static native double mouseX();

    @JSBody(script = "return LT.mouseY();")
    static native double mouseY();

    @JSBody(script = "return LT.mouseDown();")
    static native boolean mouseDown();

    @JSBody(script = "return LT.touchMode();")
    static native boolean touchMode();

    @JSBody(script = "return LT.coarsePointer();")
    static native boolean coarsePointer();

    /** Takes the next click or tap; read it with {@link #clickX()} / {@link #clickY()}. */
    @JSBody(script = "return LT.takeClick();")
    static native boolean takeClick();

    @JSBody(script = "return LT.clickX();")
    static native double clickX();

    @JSBody(script = "return LT.clickY();")
    static native double clickY();

    @JSBody(script = "return LT.stickActive();")
    static native boolean stickActive();

    @JSBody(script = "return LT.stickOriginX();")
    static native double stickOriginX();

    @JSBody(script = "return LT.stickOriginY();")
    static native double stickOriginY();

    @JSBody(script = "return LT.stickX();")
    static native double stickX();

    @JSBody(script = "return LT.stickY();")
    static native double stickY();

    @JSBody(script = "return LT.aimActive();")
    static native boolean aimActive();

    @JSBody(script = "return LT.aimX();")
    static native double aimX();

    @JSBody(script = "return LT.aimY();")
    static native double aimY();

    @JSBody(params = "id", script = "LT.sfx(id);")
    static native void sfx(int id);

    @JSBody(params = "m", script = "LT.setMuted(m);")
    static native void setMuted(boolean muted);

    @JSBody(params = "key", script = "return LT.load(key);")
    static native String load(String key);

    @JSBody(params = {"key", "value"}, script = "LT.save(key, value);")
    static native void save(String key, String value);

    @JSBody(params = "url", script = "LT.open(url);")
    static native void open(String url);

    static int loadInt(String key, int fallback) {
        String v = load(key);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
