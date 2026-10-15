// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.content.Context;
import android.content.res.Configuration;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class AiMenuThemeTest {
    private int color(Object palette, String name) throws Exception {
        return palette.getClass().getField(name).getInt(palette);
    }
    private Object palette(boolean dark) throws Exception {
        Context app=ApplicationProvider.getApplicationContext();
        Configuration config=new Configuration(app.getResources().getConfiguration());
        config.uiMode=(config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
            | (dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
        Class<?> type;
        try { type=Class.forName("helium314.keyboard.latin.ai.AiMenuTheme"); }
        catch(ClassNotFoundException missing){throw new AssertionError("Gemini surfaces need an independent light/dark theme",missing);}
        return type.getMethod("resolve",Context.class).invoke(null,app.createConfigurationContext(config));
    }
    private double luminance(int c) {
        double result=0;
        for(int i=0;i<3;i++){
            double value=((c>>(16-8*i))&255)/255.0;
            value=value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);
            result+=value*(i==0?.2126:i==1?.7152:.0722);
        }
        return result;
    }
    private void contrast(int a,int b) {
        double x=luminance(a),y=luminance(b);
        assertTrue("Contrast must be at least 4.5", (Math.max(x,y)+.05)/(Math.min(x,y)+.05)>=4.5);
    }
    @Test public void readableOpaqueSurfacesAndStatesInBothModes() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            Object p=palette(dark);
            for(String bg:new String[]{"surface","pressed","focused"}) {
                assertEquals(255,color(p,bg)>>>24);
                contrast(color(p,"onSurface"),color(p,bg));
            }
        }
    }
    @Test public void changingNightConfigurationResolvesNewPalette() throws Exception {
        assertNotEquals(color(palette(false),"surface"),color(palette(true),"surface"));
    }
}
