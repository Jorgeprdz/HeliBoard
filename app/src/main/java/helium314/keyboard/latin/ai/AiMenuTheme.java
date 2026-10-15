// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Colors/styles owned exclusively by temporary Gemini surfaces; never mutate keyboard colors. */
public final class AiMenuTheme {
    public final int surface, onSurface, pressed, focused;
    private AiMenuTheme(int surface, int onSurface, int pressed, int focused) {
        this.surface=surface; this.onSurface=onSurface; this.pressed=pressed; this.focused=focused;
    }
    public static AiMenuTheme resolve(Context context) {
        boolean dark=(context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        if(context instanceof helium314.keyboard.latin.LatinIME || context instanceof AiSettingsActivity){
            try{
                int background=helium314.keyboard.latin.settings.Settings.getValues().mColors.get(
                    helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND);
                dark=androidx.core.graphics.ColorUtils.calculateLuminance(background)<.45;
            }catch(RuntimeException notInitialized){ /* Configuration remains the fallback before settings load. */ }
        }
        return resolve(context,dark);
    }
    public static AiMenuTheme resolve(Context context,boolean dark){
        if(Build.VERSION.SDK_INT>=31) {
            return new AiMenuTheme(
                context.getColor(dark?android.R.color.system_neutral1_900:android.R.color.system_neutral1_50),
                context.getColor(dark?android.R.color.system_neutral1_50:android.R.color.system_neutral1_900),
                context.getColor(dark?android.R.color.system_neutral2_700:android.R.color.system_neutral2_100),
                context.getColor(dark?android.R.color.system_neutral2_700:android.R.color.system_neutral2_200));
        }
        return dark?new AiMenuTheme(0xff1d1b20,0xffe6e1e5,0xff49454f,0xff49454f)
            :new AiMenuTheme(0xfff7f2fa,0xff1d1b20,0xffe8def8,0xffeaddff);
    }
    public static AiMenuTheme resolveKeyboard(Context context){
        try{
            int background=helium314.keyboard.latin.settings.Settings.getValues().mColors.get(
                helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND);
            return resolve(context,androidx.core.graphics.ColorUtils.calculateLuminance(background)<.45);
        }catch(RuntimeException notInitialized){return resolve(context);}
    }
    public static Context expressiveContext(Context context){
        AiMenuTheme colors=resolveKeyboard(context);
        boolean dark=androidx.core.graphics.ColorUtils.calculateLuminance(colors.surface)<.45;
        Configuration configuration=new Configuration(context.getResources().getConfiguration());
        configuration.uiMode=(configuration.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|
            (dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
        Context themed=new android.view.ContextThemeWrapper(context.createConfigurationContext(configuration),
            dark?helium314.keyboard.latin.R.style.AiExpressiveDark:helium314.keyboard.latin.R.style.AiExpressiveLight);
        return com.google.android.material.color.DynamicColors.wrapContextIfAvailable(themed);
    }
    public static void applyActivity(android.app.Activity activity){
        AiMenuTheme palette=resolveKeyboard(activity);
        activity.setTheme(androidx.core.graphics.ColorUtils.calculateLuminance(palette.surface)<.45?
            helium314.keyboard.latin.R.style.AiExpressiveDark:helium314.keyboard.latin.R.style.AiExpressiveLight);
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(activity);
    }
    public GradientDrawable shape(Context context,int color,int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(radius*context.getResources().getDisplayMetrics().density);
        return shape;
    }
    public void styleChoice(TextView item) {
        Context context=item.getContext();
        item.setTextColor(onSurface);
        item.setShadowLayer(0,0,0,0);
        item.setBackgroundTintList(null);
        item.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        item.setTextSize(14);
        item.setFocusable(true);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},shape(context,pressed,18));
        states.addState(new int[]{android.R.attr.state_focused},shape(context,focused,18));
        states.addState(new int[]{android.R.attr.state_hovered},shape(context,focused,18));
        states.addState(new int[]{},shape(context,surface,18));
        item.setBackground(new RippleDrawable(ColorStateList.valueOf(pressed),states,shape(context,0xffffffff,18)));
    }
    public void styleDialog(android.app.Dialog dialog) {
        if(dialog.getWindow()==null)return;
        dialog.getWindow().setBackgroundDrawable(shape(dialog.getContext(),surface,28));
        tintText(dialog.getWindow().getDecorView());
    }
    public void styleSurface(View view) {
        view.setBackgroundColor(surface);tintText(view);
    }
    private void tintText(View view) {
        if(view instanceof TextView)((TextView)view).setTextColor(onSurface);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            tintText(((ViewGroup)view).getChildAt(i));
    }
}
