package helium314.keyboard.keyboard.gifs;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;
import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class GifPanelTest {
    @Test public void queryUsesNativeTypingBridgeAndNeverCommitsToDraft() {
        Context context=RuntimeEnvironment.getApplication();LatinIME ime=mock(LatinIME.class);
        InputConnection connection=mock(InputConnection.class);EditorInfo editor=new EditorInfo();editor.packageName="test.editor";
        when(ime.getCurrentInputConnection()).thenReturn(connection);when(ime.getCurrentInputEditorInfo()).thenReturn(editor);
        GifPanel panel=new GifPanel(context,ime);shadowOf(Looper.getMainLooper()).idle();
        TextView query=panel.findViewWithTag("gif-query");query.performClick();
        verify(ime).setGifSearchTyping(panel,true);assertTrue(panel.isSearchTyping());
        StringBuilder emoji=new StringBuilder();for(int i=0;i<51;i++)emoji.append("😊");panel.onTextInput(emoji.toString());
        String value=query.getText().toString();assertEquals(50,value.codePointCount(0,value.length()));
        assertFalse(Character.isHighSurrogate(value.charAt(value.length()-1)));
        panel.onCodeInput(KeyCode.DELETE);assertEquals(49,query.getText().toString().codePointCount(0,query.getText().length()));
        panel.onCodeInput(10);assertFalse(panel.isSearchTyping());
        verify(ime,atLeastOnce()).setGifSearchTyping(panel,false);verifyNoInteractions(connection);panel.close();
    }
    @Test public void noKeyHasDeliberateSetupStateWithinNormalPanelAndNoImitationKeys() {
        Context context=RuntimeEnvironment.getApplication();LatinIME ime=mock(LatinIME.class);
        GifPanel panel=new GifPanel(context,ime);shadowOf(Looper.getMainLooper()).idle();
        panel.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY));
        panel.layout(0,0,360,300);
        assertEquals(300,panel.getMeasuredHeight());
        View empty=panel.findViewWithTag("gif-empty-state");assertNotNull(empty);
        assertEquals(View.VISIBLE,empty.getVisibility());
        assertTrue("setup state has visible viewport",panel.findViewWithTag("gif-results").getHeight()>60);
        assertNotNull(findText(panel,"Conectar GIPHY"));assertNull(findText(panel,"espacio"));assertNull(findText(panel,"Traducir"));
        panel.close();
    }
    @Test public void recentGalleryHasTwoLargeColumnsWithinNativeSizedViewport() {
        Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("giphy_metadata",Context.MODE_PRIVATE).edit().clear().commit();
        helium314.keyboard.latin.gifs.GifHistory history=new helium314.keyboard.latin.gifs.GifHistory(context);
        for(int i=0;i<6;i++)history.put("recent",new helium314.keyboard.latin.gifs.GifItem("test"+i,"Test GIF",
            "https://media.giphy.com/media/test/giphy.gif","https://media.giphy.com/media/test/100.gif","https://giphy.com/gifs/test"+i),false);
        GifPanel panel=new GifPanel(context,mock(LatinIME.class));shadowOf(Looper.getMainLooper()).idle();
        findText(panel,"Recientes").performClick();
        panel.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.EXACTLY));panel.layout(0,0,360,280);
        LinearLayout grid=panel.findViewWithTag("gif-results");LinearLayout columns=(LinearLayout)grid.getChildAt(0);
        assertEquals(2,columns.getChildCount());assertTrue(columns.getChildAt(0).getWidth()>=160);
        LinearLayout firstColumn=(LinearLayout)columns.getChildAt(0);assertEquals(3,firstColumn.getChildCount());
        assertTrue("large tile replaces thumbnail strip",firstColumn.getChildAt(0).getHeight()>=140);
        LinearLayout cell=(LinearLayout)firstColumn.getChildAt(0);
        assertTrue("actions belong below media, not over it",cell.getChildAt(0) instanceof GifPreviewView);
        assertTrue(cell.getChildAt(1).getTop()>=cell.getChildAt(0).getBottom());
        assertSame("pagination scrolls with results",grid,findText(panel,"Más resultados").getParent());
        assertTrue("compact navigation leaves room for a large preview",((View)grid.getParent()).getHeight()>=140);
        panel.close();context.getSharedPreferences("giphy_metadata",Context.MODE_PRIVATE).edit().clear().commit();
    }
    private static TextView findText(View view,String text){
        if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView found=findText(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
}
