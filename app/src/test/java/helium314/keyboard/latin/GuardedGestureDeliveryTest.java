// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin;

import android.os.Looper;
import helium314.keyboard.latin.inputlogic.InputLogic;
import helium314.keyboard.latin.settings.Settings;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

/** Regression: cancel/editor-switch between final enqueue and actual UI commit must win. */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class GuardedGestureDeliveryTest {
 private static void assign(LatinIME ime,String name,Object value)throws Exception{
  Field field=LatinIME.class.getDeclaredField(name);field.setAccessible(true);field.set(ime,value);
 }
 private static void enqueue(LatinIME.UIHandler handler,SuggestedWords words,BooleanSupplier current)throws Exception{
  handler.getClass().getMethod("showTailBatchInputResult",SuggestedWords.class,BooleanSupplier.class).invoke(handler,words,current);
 }
 @Test public void cancelledQueuedGestureCannotCommitIntoNewEditor()throws Exception{
  LatinIME ime=mock(LatinIME.class);InputLogic logic=mock(InputLogic.class);
  assign(ime,"mInputLogic",logic);assign(ime,"mSettings",mock(Settings.class));
  LatinIME.UIHandler handler=new LatinIME.UIHandler(ime);
  boolean[] current={true};
  enqueue(handler,SuggestedWords.getEmptyInstance(),()->current[0]);
  // Cancellation/editor replacement occurs after validation/enqueue, before message delivery.
  current[0]=false;shadowOf(Looper.getMainLooper()).idle();
  verifyNoInteractions(logic);
  verify(ime,never()).onTailBatchInputResultShown(any());
 }
 @Test public void stillCurrentGestureReachesExistingCommitExactlyOnce()throws Exception{
  LatinIME ime=mock(LatinIME.class);InputLogic logic=mock(InputLogic.class);
  assign(ime,"mInputLogic",logic);assign(ime,"mSettings",mock(Settings.class));
  LatinIME.UIHandler handler=new LatinIME.UIHandler(ime);
  SuggestedWords words=SuggestedWords.getEmptyInstance();
  enqueue(handler,words,()->true);shadowOf(Looper.getMainLooper()).idle();
  verify(logic,times(1)).onUpdateTailBatchInputCompleted(null,words,null);
  verify(ime,times(1)).onTailBatchInputResultShown(words);
 }
 @Test public void queuedPreviewCannotDisplayInReplacedEditor()throws Exception{
  LatinIME ime=mock(LatinIME.class);InputLogic logic=mock(InputLogic.class);
  assign(ime,"mInputLogic",logic);assign(ime,"mSettings",mock(Settings.class));
  LatinIME.UIHandler handler=new LatinIME.UIHandler(ime);
  boolean[] current={true};
  handler.getClass().getMethod("showGesturePreviewAndSetSuggestions",SuggestedWords.class,boolean.class,BooleanSupplier.class)
   .invoke(handler,SuggestedWords.getEmptyInstance(),false,(BooleanSupplier)()->current[0]);
  current[0]=false;shadowOf(Looper.getMainLooper()).idle();
  verifyNoInteractions(logic);
  verify(ime,never()).setSuggestions(any());
 }
 @Test public void currentQueuedPreviewUsesExistingKeyboardPreview()throws Exception{
  LatinIME ime=mock(LatinIME.class);
  helium314.keyboard.keyboard.KeyboardSwitcher switcher=mock(helium314.keyboard.keyboard.KeyboardSwitcher.class);
  helium314.keyboard.keyboard.MainKeyboardView view=mock(helium314.keyboard.keyboard.MainKeyboardView.class);
  when(switcher.getMainKeyboardView()).thenReturn(view);assign(ime,"mKeyboardSwitcher",switcher);
  LatinIME.UIHandler handler=new LatinIME.UIHandler(ime);
  SuggestedWords words=SuggestedWords.getEmptyInstance();
  handler.getClass().getMethod("showGesturePreviewAndSetSuggestions",SuggestedWords.class,boolean.class,BooleanSupplier.class)
   .invoke(handler,words,false,(BooleanSupplier)()->true);
  shadowOf(Looper.getMainLooper()).idle();
  verify(ime,times(1)).setSuggestions(words);
  verify(view,times(1)).showGestureFloatingPreviewText(words,false);
 }

}
