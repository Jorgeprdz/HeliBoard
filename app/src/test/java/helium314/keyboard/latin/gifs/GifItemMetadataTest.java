package helium314.keyboard.latin.gifs;
import org.json.JSONObject;import org.junit.Test;import static org.junit.Assert.*;
public class GifItemMetadataTest {
 private static final String URL="https://media.giphy.com/media/test/giphy.gif";
 @Test public void oldFavoritesRemainReadableWithoutDimensions()throws Exception{GifItem item=GifItem.fromMetadata(new JSONObject().put("id","old_favorite").put("title","Old").put("url",URL).put("preview",URL));assertNotNull(item);assertEquals("old_favorite",item.id);assertEquals(0,item.width);assertEquals(0,item.height);}
 @Test public void portraitDimensionsSurviveRecentHistoryRoundTrip()throws Exception{GifItem source=new GifItem("portrait","Portrait",URL,URL,"https://giphy.com/gifs/portrait",100,180);GifItem result=GifItem.fromMetadata(source.metadata());assertNotNull(result);assertEquals(100,result.width);assertEquals(180,result.height);assertEquals(source.id,result.id);}
 @Test public void untrustedMediaNeverBecomesAHistoryItem()throws Exception{assertNull(GifItem.fromMetadata(new JSONObject().put("id","safe").put("url","https://attacker.example/a.gif").put("preview",URL)));}
}
