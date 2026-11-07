package helium314.keyboard.latin.gifs;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class TextStickerRendererTest {
    @Test public void transparent512PngContainsVisibleSpanishEmojiRtlAndLongWord() throws Exception {
        for(String text:new String[]{"¡Hola! 😊", "á 👩🏽‍💻 🇲🇽 ❤️", "مرحبا بالعالم", repeat("W",120)}) {
            byte[] png=TextStickerRenderer.render(text);
            TextStickerStore.validatePng(png);
            Bitmap image=BitmapFactory.decodeByteArray(png,0,png.length);
            assertNotNull(image);assertEquals(512,image.getWidth());assertEquals(512,image.getHeight());
            assertEquals(0,android.graphics.Color.alpha(image.getPixel(0,0)));
            int visible=0;for(int y=96;y<416;y++)for(int x=80;x<432;x++)if(android.graphics.Color.alpha(image.getPixel(x,y))>0)visible++;
            assertTrue("text area contains visible ink beyond decorative marks",visible>100);
            for(int i=0;i<512;i++){
                assertEquals("top margin",0,android.graphics.Color.alpha(image.getPixel(i,0)));
                assertEquals("bottom margin",0,android.graphics.Color.alpha(image.getPixel(i,511)));
                assertEquals("left margin",0,android.graphics.Color.alpha(image.getPixel(0,i)));
                assertEquals("right margin",0,android.graphics.Color.alpha(image.getPixel(511,i)));
            }
            image.recycle();
        }
    }
    @Test(expected=IllegalArgumentException.class) public void emptyTextRefused(){TextStickerRenderer.render(" \n ");}
    @Test(expected=IllegalArgumentException.class) public void overLimitRefused(){TextStickerRenderer.render(repeat("😊",121));}
    private static String repeat(String value,int count){StringBuilder result=new StringBuilder();for(int i=0;i<count;i++)result.append(value);return result.toString();}
}
