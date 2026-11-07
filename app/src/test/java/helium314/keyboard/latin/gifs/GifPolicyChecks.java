package helium314.keyboard.latin.gifs;

/** Standalone assertions also run without the Android/Gradle toolchain. */
public final class GifPolicyChecks {
    public static void main(String[] args) {
        check(GifPolicy.allowedMediaUrl("https://media2.giphy.com/media/abc/giphy.gif"), "provider media");
        check(!GifPolicy.allowedMediaUrl("http://media.giphy.com/a.gif"), "HTTPS required");
        check(!GifPolicy.allowedMediaUrl("https://media.giphy.com.attacker.test/a.gif"), "host boundary");
        check(!GifPolicy.allowedMediaUrl("https://user@media.giphy.com/a.gif"), "userinfo rejected");
        check(!GifPolicy.allowedMediaUrl("https://media.giphy.com:444/a.gif"), "nonstandard port rejected");
        check(GifPolicy.supportsGif(new String[]{"image/*"}), "wildcard image MIME");
        check(!GifPolicy.supportsGif(new String[]{"text/plain", "image/png"}), "non GIF editor");
        check(GifPolicy.isGif(new byte[]{71,73,70,56,57,97,1,0,1,0}), "GIF89a");
        check(!GifPolicy.isGif(new byte[]{60,104,116,109,108,62}), "HTML refused");
        check(!GifPolicy.isGif(new byte[]{71,73,70,56,57,97,0,0,1,0}), "zero dimension refused");
        check(!GifPolicy.isGif(new byte[]{71,73,70,56,57,97,0,16,0,16}), "oversized dimensions refused");
        GifPolicy.Generation generation = new GifPolicy.Generation();
        int first = generation.next();
        check(generation.current(first), "current request");
        generation.next();
        check(!generation.current(first), "old query/editor result discarded");
        System.out.println("GifPolicyChecks: 13 assertions passed");
    }
    private static void check(boolean result, String name) { if (!result) throw new AssertionError(name); }
}
