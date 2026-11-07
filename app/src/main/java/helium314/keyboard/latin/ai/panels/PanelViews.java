// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.panels;
import android.content.Context;
import helium314.keyboard.latin.ai.AiMenuTheme;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
/** Styling stays entirely on temporary panels, away from the normal keyboard. */
public final class PanelViews {
 private PanelViews(){}
 public static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
 public static void style(LinearLayout view){
  view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(view.getContext(),8),dp(view.getContext(),4),dp(view.getContext(),8),dp(view.getContext(),4));
  view.setBackgroundColor(AiMenuTheme.resolveKeyboard(view.getContext()).background);
 }
 public static TextView label(Context c,String text){TextView v=new TextView(c);v.setText(text);v.setTextColor(AiMenuTheme.resolveKeyboard(c).onSurface);v.setTextSize(15);v.setPadding(dp(c,8),dp(c,3),dp(c,8),dp(c,3));return v;}
 public static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(android.view.Gravity.CENTER_VERTICAL);return v;}
 public static Button button(LinearLayout row,String text,Runnable action){
  Button b=new Button(row.getContext());b.setText(text);b.setAllCaps(false);b.setTextColor(AiMenuTheme.resolveKeyboard(row.getContext()).onSurface);b.setTextSize(13);b.setMinHeight(dp(row.getContext(),40));b.setMinimumHeight(dp(row.getContext(),40));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(row.getContext(),4),dp(row.getContext(),4),dp(row.getContext(),4),dp(row.getContext(),4));b.setMaxLines(2);
  GradientDrawable bg=new GradientDrawable();bg.setColor(AiMenuTheme.resolveKeyboard(row.getContext()).surface);bg.setCornerRadius(dp(row.getContext(),20));b.setBackground(bg);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(row.getContext(),2),dp(row.getContext(),2),dp(row.getContext(),2),dp(row.getContext(),2));row.addView(b,p);b.setOnClickListener(v->action.run());return b;
 }
}
