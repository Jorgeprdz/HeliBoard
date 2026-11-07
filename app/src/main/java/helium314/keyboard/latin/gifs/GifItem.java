// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import org.json.JSONObject;

/** Only provider metadata is persisted. Media bytes are always transient. */
public final class GifItem {
    public final String id, title, url, preview, link;
    public final int width,height;
    public GifItem(String id, String title, String url, String preview, String link) {
        this(id,title,url,preview,link,0,0);
    }
    public GifItem(String id,String title,String url,String preview,String link,int width,int height) {
        this.id=id; this.title=title; this.url=url; this.preview=preview; this.link=link;
        this.width=width>0&&width<=10000?width:0;
        this.height=height>0&&height<=10000?height:0;
    }
    public JSONObject metadata() throws Exception {
        return new JSONObject().put("id",id).put("title",title).put("url",url).put("preview",preview).put("link",link).put("width",width).put("height",height);
    }
    public static GifItem fromMetadata(JSONObject value) {
        String id=value.optString("id");
        String url=value.optString("url"), preview=value.optString("preview");
        if(!id.matches("[a-zA-Z0-9_-]{1,100}") || !GifPolicy.allowedMediaUrl(url)
            || !GifPolicy.allowedMediaUrl(preview)) return null;
        return new GifItem(id,value.optString("title"),url,preview,"https://giphy.com/gifs/"+id,value.optInt("width"),value.optInt("height"));
    }
}
