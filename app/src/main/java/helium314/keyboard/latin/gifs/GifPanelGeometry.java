// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

/** Pixel-only sizing policy; never changes the native keyboard's geometry or preferences. */
public final class GifPanelGeometry {
    private GifPanelGeometry() {}
    public static int browseHeight(int normal, int available) {
        int base=Math.max(1,normal);
        long expanded=(base*5L+3)/4;
        long limit=available>0?Math.max(base,available*3L/5):expanded;
        return (int)Math.min(Integer.MAX_VALUE,Math.min(expanded,limit));
    }
    public static int previewHeight(int width,int mediaWidth,int mediaHeight,int maximum) {
        int w=Math.max(1,width),limit=Math.max(1,maximum);
        long natural=mediaWidth>0&&mediaHeight>0?((long)w*mediaHeight+mediaWidth/2L)/mediaWidth:w;
        return (int)Math.max(1,Math.min(limit,natural));
    }
}
