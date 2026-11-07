// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared trust, MIME and stale-result boundaries; independent of the Android UI. */
public final class GifPolicy {
    private GifPolicy() {}
    public static boolean allowedMediaUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                && (host.equals("media.giphy.com") || host.matches("media[0-9]+\\.giphy\\.com")
                    || host.equals("i.giphy.com"));
        } catch (Exception ignored) { return false; }
    }
    public static boolean supportsGif(String[] types) {
        if (types != null) for (String type : types)
            if ("image/gif".equalsIgnoreCase(type) || "image/*".equals(type) || "*/*".equals(type)) return true;
        return false;
    }
    public static boolean isGif(byte[] bytes) {
        if (bytes == null || bytes.length < 10 || bytes[0] != 'G' || bytes[1] != 'I'
            || bytes[2] != 'F' || bytes[3] != '8' || (bytes[4] != '7' && bytes[4] != '9') || bytes[5] != 'a') return false;
        int width = (bytes[6] & 255) | ((bytes[7] & 255) << 8);
        int height = (bytes[8] & 255) | ((bytes[9] & 255) << 8);
        return width > 0 && height > 0 && width <= 640 && height <= 640;
    }
    public static final class Generation {
        private final AtomicInteger value = new AtomicInteger();
        public int next() { return value.incrementAndGet(); }
        public boolean current(int expected) { return value.get() == expected; }
    }
}
