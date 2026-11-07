// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Colors/styles owned exclusively by temporary Gemini surfaces; never mutate keyboard colors. */
public final class AiMenuTheme {
    public final int background, surface, onSurface, pressed, focused;
    private AiMenuTheme(helium314.keyboard.settings.OneUiMenuStyle.Palette palette) {
        background=palette.background;surface=palette.surface;onSurface=palette.foreground;
        pressed=androidx.core.graphics.ColorUtils.blendARGB(surface,onSurface,.08f);
        focused=androidx.core.graphics.ColorUtils.blendARGB(surface,onSurface,.13f);
    }
    public static AiMenuTheme resolve(Context context) {
        return new AiMenuTheme(helium314.keyboard.settings.OneUiMenuStyle.palette(context));
    }
    public static AiMenuTheme resolve(Context context,boolean dark){
        return new AiMenuTheme(helium314.keyboard.settings.OneUiMenuStyle.palette(dark,
            androidx.core.content.ContextCompat.getColor(context,helium314.keyboard.latin.R.color.accent)));
    }
    /** Legacy entry point: only secondary surfaces, never the native keyboard palette. */
    public static AiMenuTheme resolveKeyboard(Context context){return resolve(context);}
    public static Context expressiveContext(Context context){
        return new android.view.ContextThemeWrapper(context,helium314.keyboard.latin.R.style.OneUiMenuTheme);
    }
    public static void applyActivity(android.app.Activity activity){
        activity.setTheme(helium314.keyboard.latin.R.style.OneUiMenuTheme);
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
        item.setTextSize(17);
        item.setMinimumHeight(helium314.keyboard.settings.OneUiMenuStyle.dp(context,48));
        item.setFocusable(true);
        if(item instanceof com.google.android.material.button.MaterialButton){
            com.google.android.material.button.MaterialButton button=(com.google.android.material.button.MaterialButton)item;
            button.setBackgroundTintList(new ColorStateList(new int[][]{
                {android.R.attr.state_pressed},{android.R.attr.state_focused},{android.R.attr.state_hovered},{}},
                new int[]{pressed,focused,focused,surface}));
            button.setRippleColor(ColorStateList.valueOf(pressed));
            button.setCornerRadius(helium314.keyboard.settings.OneUiMenuStyle.dp(context,26));
            return;
        }
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},shape(context,pressed,18));
        states.addState(new int[]{android.R.attr.state_focused},shape(context,focused,18));
        states.addState(new int[]{android.R.attr.state_hovered},shape(context,focused,18));
        states.addState(new int[]{},shape(context,surface,18));
        item.setBackground(new RippleDrawable(ColorStateList.valueOf(pressed),states,shape(context,0xffffffff,18)));
    }
    public void styleDialog(android.app.Dialog dialog) {
        if(dialog.getWindow()==null)return;
        dialog.getWindow().setBackgroundDrawable(shape(dialog.getContext(),surface,26));
        tintText(dialog.getWindow().getDecorView());
    }
    public void styleSurface(View view) {
        view.setBackgroundColor(background);tintText(view);
        if(view instanceof android.widget.LinearLayout && view.getContext() instanceof android.app.Activity)
            helium314.keyboard.settings.OneUiMenuStyle.applyForm((android.widget.LinearLayout)view);
    }
    private void tintText(View view) {
        if(view instanceof TextView)((TextView)view).setTextColor(onSurface);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            tintText(((ViewGroup)view).getChildAt(i));
    }
}
