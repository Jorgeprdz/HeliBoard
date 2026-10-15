package helium314.keyboard.latin.ai;
import android.content.Context;import android.content.res.Configuration;
import com.google.android.material.button.MaterialButton;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class R23ExpressiveTest {
 @Test public void expressiveSurfacesInflateAndRemainClickableWithReadableStates(){
  Context app=ApplicationProvider.getApplicationContext();
  for(boolean dark:new boolean[]{false,true}){
   Configuration config=new Configuration(app.getResources().getConfiguration());
   config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
   Context source=app.createConfigurationContext(config);
   int original=source.getResources().getConfiguration().uiMode;
   Context themed=AiMenuTheme.expressiveContext(source);
   MaterialButton choice=new MaterialButton(themed);
   AiMenuTheme colors=AiMenuTheme.resolve(source,dark);
   colors.styleChoice(choice);choice.setText("Aplicar");
   final int[] applied={0};choice.setOnClickListener(v->applied[0]++);
   choice.performClick();assertEquals(1,applied[0]);
   assertEquals(colors.onSurface,choice.getCurrentTextColor());
   for(int background:new int[]{colors.surface,colors.pressed,colors.focused})
    assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(colors.onSurface,background)>=4.5);
   assertEquals(original,source.getResources().getConfiguration().uiMode);
  }
 }
}
