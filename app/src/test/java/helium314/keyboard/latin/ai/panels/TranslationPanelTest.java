package helium314.keyboard.latin.ai.panels;
import android.content.Context;
import android.widget.TextView;
import android.view.View;
import helium314.keyboard.latin.ai.AiMenuTheme;
import android.graphics.drawable.ColorDrawable;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class TranslationPanelTest {
 static final class Host implements PanelHost {
  int commits;Object token="editor";public Object editorToken(){return token;}
  public boolean canUseNetwork(){return false;}public boolean commitText(String s){commits++;return true;}public void closePanel(){}
 }
 @Test public void localTypingAndClosingNeverCommitDraftIntoEditor(){
  Context c=ApplicationProvider.getApplicationContext();Host h=new Host();TranslationPanel panel=new TranslationPanel(c,h);
  panel.onTextInput("hola");assertEquals("hola",((TextView)((android.widget.LinearLayout)((android.widget.ScrollView)panel.getChildAt(1)).getChildAt(0)).getChildAt(0)).getText().toString());assertEquals(0,h.commits);
  panel.close();panel.onTextInput("secreto");assertEquals("",((TextView)((android.widget.LinearLayout)((android.widget.ScrollView)panel.getChildAt(1)).getChildAt(0)).getChildAt(0)).getText().toString());assertEquals(0,h.commits);
 }
 @Test public void allControlsFit250DpPanelAndUseKeyboardPalette(){
  Context c=ApplicationProvider.getApplicationContext();TranslationPanel panel=new TranslationPanel(c,new Host());
  int w=PanelViews.dp(c,360),h=PanelViews.dp(c,250);
  panel.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));panel.layout(0,0,w,h);
  assertTrue(panel.getChildAt(panel.getChildCount()-1).getBottom()<=h);
  assertEquals(AiMenuTheme.resolve(c).surface,((ColorDrawable)panel.getBackground()).getColor());panel.close();
 }
}
