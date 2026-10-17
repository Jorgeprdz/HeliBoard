// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.text.InputType;

/** DebugNoMinify-only real InputConnection host. No production activity is installed. */
public final class R252bHostActivity extends Activity {
    private EditText editor;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        editor = new EditText(this);
        editor.setId(android.R.id.edit);
        editor.setInputType(InputType.TYPE_CLASS_TEXT);
        editor.setSingleLine(false);
        editor.setText("start ");
        root.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        editor.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    public EditText getEditorForTest() { return editor; }
    public void showImeForTest() {
        editor.requestFocus();
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
            .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
    }
}
