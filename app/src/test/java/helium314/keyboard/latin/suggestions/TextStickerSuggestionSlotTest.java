// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.SuggestedWords;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.dictionary.Dictionary;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class TextStickerSuggestionSlotTest {
 @Test public void twoNativePredictionsEmptyRightSlotBecomesInteractiveAndRestoresDisabledState(){
  Context context=ApplicationProvider.getApplicationContext();
  helium314.keyboard.accessibility.AccessibilityUtils.init(context);
  helium314.keyboard.latin.settings.Settings.getInstance().loadSettings(context);
  SuggestionStripView strip=new SuggestionStripView(context,null);
  strip.setToolbarVisibility(false);
  strip.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(160,View.MeasureSpec.EXACTLY));
  strip.layout(0,0,1080,160);
  ArrayList<SuggestedWordInfo> predictions=new ArrayList<>(Arrays.asList(
   new SuggestedWordInfo("hola","",100,SuggestedWordInfo.KIND_PREDICTION,Dictionary.DICTIONARY_HARDCODED,-1,-1),
   new SuggestedWordInfo("buenas","",90,SuggestedWordInfo.KIND_PREDICTION,Dictionary.DICTIONARY_HARDCODED,-1,-1)));
  strip.setSuggestions(new SuggestedWords(predictions,null,null,false,false,false,SuggestedWords.INPUT_STYLE_PREDICTION,1),false);
  strip.setToolbarVisibility(false);
  ViewGroup row=strip.findViewById(R.id.suggestions_strip);
  TextView right=null;int slots=0;
  for(int i=0;i<row.getChildCount();i++)if(row.getChildAt(i) instanceof TextView){right=(TextView)row.getChildAt(i);slots++;}
  assertEquals(3,slots);assertNotNull(right);assertEquals("",right.getText().toString());assertFalse(right.isEnabled());
  ViewGroup.LayoutParams nativeLayout=right.getLayoutParams();int childCount=row.getChildCount();
  int[] sends={0},saves={0};Bitmap image=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);
  assertTrue(strip.showTextStickerSuggestion(image,()->sends[0]++,()->saves[0]++));
  assertTrue("A sticker in an empty native slot must accept touches",right.isEnabled());
  right.performClick();assertTrue(right.performLongClick());assertEquals(1,sends[0]);assertEquals(1,saves[0]);
  assertSame(nativeLayout,right.getLayoutParams());assertEquals(childCount,row.getChildCount());
  strip.clearTextStickerSuggestion();
  assertFalse(right.isEnabled());assertEquals("",right.getText().toString());
  image.recycle();
 }
}
