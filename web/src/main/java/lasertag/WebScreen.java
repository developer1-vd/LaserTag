package lasertag;

import java.util.function.Consumer;

/** {@link Screen} drawing onto the page's canvas through {@link Web}. */
final class WebScreen implements Screen {
    @Override
    public void fill(int x, int y, int w, int h, int argb) {
        Web.fill(x, y, w, h, argb);
    }

    @Override
    public void line(int x0, int y0, int x1, int y1, int width, int argb) {
        Web.line(x0, y0, x1, y1, width, argb);
    }

    @Override
    public void cached(String key, int x, int y, int w, int h, Consumer<Screen> painter) {
        if (!Web.hasCache(key)) {
            Web.beginCache(key, w, h);
            painter.accept(this);
            Web.endCache();
        }
        Web.drawCache(key, x, y);
    }
}
