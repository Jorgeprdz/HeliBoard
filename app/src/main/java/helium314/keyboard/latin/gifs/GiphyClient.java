// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded client. A cancellation disconnects the current socket, including downloads. */
public final class GiphyClient {
    private volatile HttpsURLConnection active;
    private volatile boolean cancelled;
    public void cancel() { cancelled=true; HttpsURLConnection c=active; if(c!=null)c.disconnect(); }
    public static final class Page {
        public final List<GifItem> items; public final boolean more;
        Page(List<GifItem> items,boolean more){this.items=items;this.more=more;}
    }
    public Page search(String key,String query,boolean stickers,int offset) throws Exception {
        if(offset<0 || offset>499 || query.length()>50) throw new IllegalArgumentException();
        String base="https://api.giphy.com/v1/"+(stickers?"stickers/":"gifs/")+(query.isEmpty()?"trending":"search");
        String address=base+"?api_key="+URLEncoder.encode(key,"UTF-8")+"&limit=12&offset="+offset+"&rating=pg&lang=es"
            +(query.isEmpty()?"":"&q="+URLEncoder.encode(query,"UTF-8"));
        JSONObject root=new JSONObject(new String(fetch(address,1024*1024,false),StandardCharsets.UTF_8));
        JSONArray data=root.getJSONArray("data");List<GifItem> items=new ArrayList<>();
        for(int i=0;i<data.length() && items.size()<12;i++){
            JSONObject item=data.getJSONObject(i),images=item.optJSONObject("images"); if(images==null)continue;
            JSONObject small=images.optJSONObject("fixed_height_small"),send=images.optJSONObject("downsized_medium");
            if(send==null || send.optInt("width",1000)>640 || send.optInt("height",1000)>640)send=images.optJSONObject("fixed_height");
            if(small==null||send==null)continue;
            GifItem parsed=GifItem.fromMetadata(new JSONObject().put("id",item.optString("id"))
                .put("title",item.optString("title")).put("url",send.optString("url")).put("preview",small.optString("url"))
                .put("width",small.optInt("width")).put("height",small.optInt("height")));
            if(parsed!=null)items.add(parsed);
        }
        JSONObject pagination=root.optJSONObject("pagination");
        return new Page(items,pagination!=null && offset+data.length()<Math.min(500,pagination.optInt("total_count")) && data.length()>0);
    }
    public byte[] media(String address, boolean preview) throws Exception {
        byte[] bytes=fetch(address,preview?512*1024:5*1024*1024,true);
        if(!GifPolicy.isGif(bytes))throw new java.io.IOException("Unsupported media");
        return bytes;
    }
    private byte[] fetch(String address,int limit,boolean media) throws Exception {
        if(cancelled||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
        if(media && !GifPolicy.allowedMediaUrl(address))throw new java.io.IOException("Untrusted media");
        URL url=new URL(address);
        if(!media && !("https".equals(url.getProtocol())&&"api.giphy.com".equals(url.getHost())))throw new java.io.IOException("Untrusted API");
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();active=c;
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(8000);
        try{
            int code=c.getResponseCode();
            if(code==429)throw new java.io.IOException("RATE_LIMIT");
            if(code==401||code==403)throw new java.io.IOException("KEY_REJECTED");
            if(code!=200)throw new java.io.IOException("HTTP_ERROR");
            if(c.getContentLength()>limit)throw new java.io.IOException("MEDIA_TOO_LARGE");
            long deadline=System.nanoTime()+15000000000L;
            try(InputStream stream=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=stream.read(buffer))!=-1){
                    if(cancelled||Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new java.io.InterruptedIOException();
                    if(out.size()+n>limit)throw new java.io.IOException("MEDIA_TOO_LARGE");
                    out.write(buffer,0,n);
                }
                return out.toByteArray();
            }
        }finally{c.disconnect();active=null;}
    }
}
