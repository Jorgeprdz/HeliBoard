// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import android.content.Context;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public final class GifHistory {
    private final android.content.SharedPreferences prefs;
    public GifHistory(Context context){prefs=context.getSharedPreferences("giphy_metadata",Context.MODE_PRIVATE);}
    public List<GifItem> read(String category){
        List<GifItem> result=new ArrayList<>();
        try{JSONArray array=new JSONArray(prefs.getString(category,"[]"));for(int i=0;i<array.length()&&i<50;i++){
            GifItem item=GifItem.fromMetadata(array.getJSONObject(i));if(item!=null)result.add(item);
        }}catch(Exception ignored){}return result;
    }
    public boolean contains(String category,GifItem item){for(GifItem saved:read(category))if(saved.id.equals(item.id))return true;return false;}
    public void put(String category,GifItem item,boolean toggle){
        List<GifItem> items=read(category);boolean existed=items.removeIf(old->old.id.equals(item.id));
        if(!toggle||!existed)items.add(0,item);
        JSONArray array=new JSONArray();for(int i=0;i<Math.min(items.size(),"recent".equals(category)?30:50);i++)try{array.put(items.get(i).metadata());}catch(Exception ignored){}
        prefs.edit().putString(category,array.toString()).apply();
    }
}
