package helium314.keyboard.latin.ai.handwriting;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import androidx.test.core.app.ApplicationProvider;
import helium314.keyboard.latin.ai.panels.PanelHost;
import helium314.keyboard.latin.ai.panels.PanelViews;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class HandwritingPanelTest {
 static final class Host implements PanelHost {
  int commits;public Object editorToken(){return "editor";}public boolean canUseNetwork(){return false;}
  public boolean commitText(String s){commits++;return true;}public void closePanel(){}
 }
 @Test public void drawingFits250DpAndCloseClearsInkWithoutCommit(){
  Context c=ApplicationProvider.getApplicationContext();Host host=new Host();HandwritingPanel panel=new HandwritingPanel(c,host);
  int w=PanelViews.dp(c,360),h=PanelViews.dp(c,250);
  panel.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));panel.layout(0,0,w,h);
  InkPadView pad=(InkPadView)panel.getChildAt(3);assertTrue(pad.getHeight()>0);assertTrue(panel.getChildAt(4).getBottom()<=h);
  MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20,20,0),up=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,25,25,0);
  pad.onTouchEvent(down);pad.onTouchEvent(up);down.recycle();up.recycle();assertEquals(1,pad.strokes().size());assertEquals(0,host.commits);
  panel.close();panel.close();assertTrue(pad.strokes().isEmpty());assertEquals(0,((LinearLayout)panel.getChildAt(2)).getChildCount());assertEquals(0,host.commits);
 }
}
