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
 @Test public void nativeEmojiRetainsItsTextAndTapWhenStickerPreviewArrives(){
  Context context=ApplicationProvider.getApplicationContext();
  helium314.keyboard.accessibility.AccessibilityUtils.init(context);
  helium314.keyboard.latin.settings.Settings.getInstance().loadSettings(context);
  SuggestionStripView strip=new SuggestionStripView(context,null);
  strip.setToolbarVisibility(false);
  strip.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(160,View.MeasureSpec.EXACTLY));
  strip.layout(0,0,1080,160);
  SuggestedWordInfo emoji=new SuggestedWordInfo("🐈","",80,SuggestedWordInfo.KIND_SHORTCUT,Dictionary.DICTIONARY_HARDCODED,-1,-1);
  ArrayList<SuggestedWordInfo> predictions=new ArrayList<>(Arrays.asList(
   new SuggestedWordInfo("gato","",100,SuggestedWordInfo.KIND_TYPED,Dictionary.DICTIONARY_HARDCODED,-1,-1),
   new SuggestedWordInfo("gatos","",90,SuggestedWordInfo.KIND_CORRECTION,Dictionary.DICTIONARY_HARDCODED,-1,-1),
   new SuggestedWordInfo("gata","",85,SuggestedWordInfo.KIND_CORRECTION,Dictionary.DICTIONARY_HARDCODED,-1,-1),emoji));
  strip.setSuggestions(new SuggestedWords(predictions,null,null,true,false,false,SuggestedWords.INPUT_STYLE_TYPING,1),false);
  strip.setToolbarVisibility(false);
  SuggestedWordInfo[] selected={null};
  View nativeInput=org.mockito.Mockito.mock(View.class);
  org.mockito.Mockito.when(nativeInput.findViewById(R.id.keyboard_view))
    .thenReturn(org.mockito.Mockito.mock(helium314.keyboard.keyboard.MainKeyboardView.class));
  strip.setListener(new SuggestionStripView.Listener(){
   public void pickSuggestionManually(SuggestedWordInfo word){selected[0]=word;}
   public void onCodeInput(int code,int x,int y,boolean repeat){}
   public void removeSuggestion(String word){} public void removeExternalSuggestions(){}
   public void onSwipeDownOnToolbar(){} public void onAiAction(String action){}
  },nativeInput);
  ViewGroup row=strip.findViewById(R.id.suggestions_strip);TextView emojiSlot=null;
  for(int i=0;i<row.getChildCount();i++)if(row.getChildAt(i) instanceof TextView&&"🐈".contentEquals(((TextView)row.getChildAt(i)).getText()))emojiSlot=(TextView)row.getChildAt(i);
  assertNotNull("A native emoji must be visible in the three-slot strip",emojiSlot);
  int children=row.getChildCount();Bitmap bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);
  // Another slot may hold a sticker, but the emoji must remain visible and selectable.
  strip.showTextStickerSuggestion(bitmap,()->fail("sticker sent"),()->fail("sticker saved"));
  assertEquals("🐈",emojiSlot.getText().toString());assertEquals(children,row.getChildCount());
  emojiSlot.performClick();assertSame("The original native emoji tap must survive",emoji,selected[0]);
  assertEquals("Presentation must not reorder the correction engine's input", "gata",predictions.get(2).mWord);
  bitmap.recycle();
 }

}
