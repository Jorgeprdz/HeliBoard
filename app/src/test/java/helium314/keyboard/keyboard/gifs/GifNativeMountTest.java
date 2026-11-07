package helium314.keyboard.keyboard.gifs;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import helium314.keyboard.keyboard.KeyboardSwitcher;
import helium314.keyboard.keyboard.MainKeyboardView;
import helium314.keyboard.latin.InputView;
import helium314.keyboard.latin.KeyboardWrapperView;
import helium314.keyboard.latin.suggestions.SuggestionStripView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class GifNativeMountTest {
 @Test public void browsingKeepsNativeHeightTypingUsesRealKeysAndCloseRestoresVisibility()throws Exception {
  Context context=RuntimeEnvironment.getApplication();KeyboardSwitcher switcher=KeyboardSwitcher.getInstance();Map<Field,Object> originals=new LinkedHashMap<>();
  InputView root=new InputView(context,null);View frame=new View(context);FrameLayout strip=new FrameLayout(context);
  frame.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.EXACTLY));frame.layout(0,7,360,287);
  strip.layout(0,0,360,42);MainKeyboardView keyboard=mock(MainKeyboardView.class);KeyboardWrapperView wrapper=mock(KeyboardWrapperView.class);
  root.addView(frame);
  // Real View is needed for attachment; use a real panel with a harmless service host.
  GifPanel mounted=new GifPanel(context,mock(helium314.keyboard.latin.LatinIME.class));
  try {
   put(switcher,originals,"mState",null);put(switcher,originals,"mLatinIME",null);
   put(switcher,originals,"mCurrentInputView",root);put(switcher,originals,"mMainKeyboardFrame",frame);put(switcher,originals,"mStripContainer",strip);
   put(switcher,originals,"mKeyboardView",keyboard);put(switcher,originals,"mKeyboardViewWrapper",wrapper);put(switcher,originals,"mSuggestionStripView",mock(SuggestionStripView.class));
   switcher.showInternalInputPanel(mounted);assertEquals(280,mounted.getLayoutParams().height);assertEquals(View.INVISIBLE,frame.getVisibility());
   switcher.setGifSearchTyping(mounted,true);assertEquals(42,mounted.getLayoutParams().height);assertEquals(View.VISIBLE,frame.getVisibility());
   assertSame(wrapper,switcher.getWrapperView());assertSame(keyboard,switcher.getVisibleKeyboardView());
   helium314.keyboard.keyboard.emoji.EmojiPalettesView emoji=mock(helium314.keyboard.keyboard.emoji.EmojiPalettesView.class);
   put(switcher,originals,"mEmojiPalettesView",emoji);when(emoji.isShown()).thenReturn(true);
   assertSame("native emoji remains the visible input surface",emoji,switcher.getVisibleKeyboardView());
   assertSame(wrapper,switcher.getWrapperView());when(emoji.isShown()).thenReturn(false);
   assertEquals(7,((FrameLayout.LayoutParams)mounted.getLayoutParams()).topMargin);
   switcher.setGifSearchTyping(mounted,false);assertEquals(280,mounted.getLayoutParams().height);assertSame(mounted,switcher.getWrapperView());
   switcher.closeInternalInputPanel();assertEquals(View.VISIBLE,frame.getVisibility());assertNull(mounted.getParent());
   frame.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.EXACTLY));frame.layout(0,0,0,0);
   GifPanel early=new GifPanel(context,mock(helium314.keyboard.latin.LatinIME.class));switcher.showInternalInputPanel(early);
   assertTrue("opening before first layout still has native-derived positive height",early.getLayoutParams().height>0);
  }finally{switcher.closeInternalInputPanel();for(Map.Entry<Field,Object> entry:originals.entrySet())entry.getKey().set(switcher,entry.getValue());}
 }
 private static void put(Object target,Map<Field,Object> previous,String name,Object value)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);previous.put(field,field.get(target));field.set(target,value);}
}
