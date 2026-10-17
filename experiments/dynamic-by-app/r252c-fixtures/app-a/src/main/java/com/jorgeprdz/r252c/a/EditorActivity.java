package com.jorgeprdz.r252c.a;
import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
/** Real separate-process, separate-UID editor fixture. */
public final class EditorActivity extends Activity {
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout layout=new LinearLayout(this);
  layout.setOrientation(LinearLayout.VERTICAL);
  layout.setPadding(32,52,32,32);
  TextView title=new TextView(this); title.setText("R252C App A"); title.setTextSize(22);
  layout.addView(title);
  EditText edit=new EditText(this);
  edit.setId(0x10042); edit.setSingleLine(true);
  edit.setInputType(InputType.TYPE_CLASS_TEXT);
  edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
  edit.setHint("Real EditText — identical inputType");
  layout.addView(edit,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
  setContentView(layout);
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
  edit.requestFocus();
  edit.postDelayed(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
   .showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT),200);
 }
}
