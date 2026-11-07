// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gifs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.SystemClock;
import android.view.View;
import helium314.keyboard.latin.gifs.GifPolicy;

/** Small bounded GIF rendition; Movie supports animation on API21 as well. */
@SuppressWarnings("deprecation")
final class GifPreviewView extends View {
    private Movie movie; private long start;private int mediaWidth,mediaHeight,maximum;
    GifPreviewView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void dimensions(int width,int height,int maxHeight){mediaWidth=width;mediaHeight=height;maximum=maxHeight;requestLayout();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        int height=helium314.keyboard.latin.gifs.GifPanelGeometry.previewHeight(width,mediaWidth,mediaHeight,maximum);
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
    }
    void load(byte[] bytes){
        if(!GifPolicy.isGif(bytes))return;
        try{movie=Movie.decodeByteArray(bytes,0,bytes.length);if(movie!=null&&(mediaWidth<=0||mediaHeight<=0)){mediaWidth=movie.width();mediaHeight=movie.height();requestLayout();}start=SystemClock.uptimeMillis();invalidate();}
        catch(OutOfMemoryError ignored){movie=null;}
    }
    void stop(){movie=null;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);Movie current=movie;if(current==null||!isShown())return;
        current.setTime((int)((SystemClock.uptimeMillis()-start)%Math.max(100,current.duration())));
        float scale=Math.min(getWidth()/(float)Math.max(1,current.width()),getHeight()/(float)Math.max(1,current.height()));
        int save=canvas.save();canvas.translate((getWidth()-current.width()*scale)/2,(getHeight()-current.height()*scale)/2);
        canvas.scale(scale,scale);current.draw(canvas,0,0);canvas.restoreToCount(save);postInvalidateDelayed(50);
    }
}
