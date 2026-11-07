// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings;

import android.content.Context;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class OneUiMenuStyleTest {
    @Test public void restylingPreservesInputPrivacyValuesAndActions() {
        Context context=RuntimeEnvironment.getApplication();
        LinearLayout form=new LinearLayout(context);
        EditText field=new EditText(context);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setText("test-value");form.addView(field);
        CheckBox consent=new CheckBox(context);consent.setChecked(false);form.addView(consent);
        AtomicInteger calls=new AtomicInteger();
        Button action=new Button(context);action.setOnClickListener(v->calls.incrementAndGet());form.addView(action);
        Button unavailable=new Button(context);unavailable.setEnabled(false);form.addView(unavailable);
        OneUiMenuStyle.applyForm(form);
        assertEquals("test-value",field.getText().toString());
        assertTrue(field.getTransformationMethod() instanceof PasswordTransformationMethod);
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,field.getInputType()&InputType.TYPE_MASK_VARIATION);
        assertFalse(consent.isChecked());assertFalse(unavailable.isEnabled());
        action.performClick();assertEquals(1,calls.get());
    }
}
