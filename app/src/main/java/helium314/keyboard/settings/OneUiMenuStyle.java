// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import helium314.keyboard.latin.R;

/** One UI 8 visual adaptation for app menus. Never styles native typing keys or changes data.
 * Reference: github.com/tribalfs/oneui-design (SESL8), independently rendered for API21/Compose.
 */
public final class OneUiMenuStyle {
    private OneUiMenuStyle() {}
    public static final class Palette {
        public final int background,surface,foreground,secondary,divider,accent;
        private Palette(boolean dark,int accent){
            background=dark?0xff010101:0xfff2f2f2;
            surface=dark?0xff171717:0xfffafafa;
            foreground=dark?0xfffafafa:0xff010101;
            secondary=dark?0xffa8a9a9:0xff666666;
            divider=dark?0xff333333:0xffe4e4e4;
            this.accent=accent;
        }
    }
    public static Palette palette(Context context){
        return palette((context.getResources().getConfiguration().uiMode
            &Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES,
            ContextCompat.getColor(context,R.color.accent));
    }
    public static Palette palette(boolean dark,int accent){return new Palette(dark,accent);}
    public static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    public static GradientDrawable rounded(Context context,int color,int radius){
        GradientDrawable background=new GradientDrawable();background.setColor(color);
        background.setCornerRadius(dp(context,radius));return background;
    }
    public static void applyForm(LinearLayout form){
        Context context=form.getContext();Palette palette=palette(context);
        form.setBackgroundColor(palette.background);
        if(context instanceof Activity && !(form.getChildAt(0) instanceof Button
                && "Volver".contentEquals(((Button)form.getChildAt(0)).getText()))){
            Button back=new Button(context);back.setText("Volver");back.setContentDescription("Volver");
            back.setOnClickListener(v->((Activity)context).finish());
            form.addView(back,0,new LinearLayout.LayoutParams(-1,-2));
        }
        styleChildren(form,palette);
        if(form.getParent() instanceof View){
            View root=(View)form.getParent();
            ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
                androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    |WindowInsetsCompat.Type.displayCutout());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
            });
            ViewCompat.requestApplyInsets(root);
        }
    }
    private static void styleChildren(View view,Palette palette){
        Context context=view.getContext();
        if(view instanceof EditText){
            EditText field=(EditText)view;
            field.setTextColor(palette.foreground);field.setHintTextColor(palette.secondary);
            field.setTextSize(17);field.setMinimumHeight(dp(context,48));
            field.setPadding(dp(context,16),dp(context,12),dp(context,16),dp(context,12));
            field.setBackground(rounded(context,palette.surface,16));
        }else if(view instanceof CompoundButton){
            CompoundButton toggle=(CompoundButton)view;
            toggle.setTextSize(17);toggle.setTextColor(palette.foreground);
            toggle.setMinHeight(dp(context,48));
            toggle.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},
                new int[]{palette.accent,palette.secondary}));
        }else if(view instanceof Button){
            Button button=(Button)view;
            button.setAllCaps(false);button.setTextSize(17);button.setMinHeight(dp(context,48));
            button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},
                new int[]{palette.secondary,palette.accent}));
            button.setPadding(dp(context,16),dp(context,10),dp(context,16),dp(context,10));
            boolean back="Volver".contentEquals(button.getText());
            if(button instanceof com.google.android.material.button.MaterialButton){
                com.google.android.material.button.MaterialButton material=(com.google.android.material.button.MaterialButton)button;
                material.setCornerRadius(dp(context,26));
                material.setBackgroundTintList(ColorStateList.valueOf(back?palette.background:palette.surface));
            }else button.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.divider),
                rounded(context,back?palette.background:palette.surface,26),null));
            if(back)button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        }else if(view instanceof TextView){
            TextView label=(TextView)view;
            float size=label.getTextSize()/context.getResources().getDisplayMetrics().scaledDensity;
            label.setTextColor(size<=14?palette.secondary:palette.foreground);
            label.setTextSize(size>=24?28:size<=14?13:17);
            if(size>=24)label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        }
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)styleChildren(group.getChildAt(i),palette);
        }
    }
}
