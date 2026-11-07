// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Local original text art. No network, credentials, editor access or persistent side effects. */
public final class TextStickerRenderer {
    public static final int SIZE=512;
    private static final int CONTENT_WIDTH=414,CONTENT_HEIGHT=398;
    private TextStickerRenderer() {}
    public static String validate(String text){return TextStickerText.validate(text);}

    /** Call on a worker thread; owns and recycles its 1MiB bitmap. */
    @SuppressWarnings("deprecation")
    public static byte[] render(String source) {
        String text=validate(source);
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        paint.setColor(Color.WHITE);paint.setTextSize(78);
        StaticLayout layout=null;
        for(int size=78;size>=4;size-=2){
            paint.setTextSize(size);
            boolean clusterFits=true;
            for(String cluster:TextStickerText.graphemes(text))if(paint.measureText(cluster)>CONTENT_WIDTH-8){clusterFits=false;break;}
            if(!clusterFits&&size>4)continue;
            String wrapped=wrap(text,paint,CONTENT_WIDTH-8);
            layout=new StaticLayout(wrapped,paint,CONTENT_WIDTH,Layout.Alignment.ALIGN_CENTER,1.06f,0,false);
            if(layout.getHeight()<=CONTENT_HEIGHT)break;
        }
        Bitmap bitmap=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas=new Canvas(bitmap);
            Paint accent=new Paint(Paint.ANTI_ALIAS_FLAG);accent.setColor(0xffffcb45);
            star(canvas,accent,52,66,18);star(canvas,accent,462,445,16);
            accent.setColor(0xff9b72ef);canvas.drawCircle(468,83,7,accent);canvas.drawCircle(43,435,6,accent);
            int save=canvas.save();canvas.rotate(-3,SIZE/2f,SIZE/2f);
            canvas.translate((SIZE-CONTENT_WIDTH)/2f,(SIZE-layout.getHeight())/2f);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(Math.min(13,paint.getTextSize()*.19f));
            paint.setColor(0xff352447);paint.setShadowLayer(5,2,5,0x55352447);layout.draw(canvas);
            paint.clearShadowLayer();paint.setStyle(Paint.Style.FILL);paint.setShader(new LinearGradient(0,0,0,Math.max(1,layout.getHeight()),
                new int[]{0xfffff3a4,0xffff91c2,0xffbb9cff},null,Shader.TileMode.CLAMP));
            layout.draw(canvas);paint.setShader(null);canvas.restoreToCount(save);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes))throw new IllegalStateException("No se pudo crear el PNG");
            return bytes.toByteArray();
        } finally {bitmap.recycle();}
    }
    /** Prewrap only between complete combining/emoji clusters, including unspaced long words. */
    private static String wrap(String text,TextPaint paint,float width){
        List<String> clusters=TextStickerText.graphemes(text);StringBuilder result=new StringBuilder(),line=new StringBuilder();
        for(String cluster:clusters){
            if(cluster.equals("\n")||cluster.equals("\r\n")){
                result.append(line).append('\n');line.setLength(0);continue;
            }
            String candidate=line.toString()+cluster;
            if(line.length()>0&&paint.measureText(candidate)>width){
                int space=line.lastIndexOf(" ");
                if(space>0){result.append(line.substring(0,space)).append('\n');String rest=line.substring(space+1);line.setLength(0);line.append(rest);}
                else{result.append(line).append('\n');line.setLength(0);}
            }
            if(line.length()>0&&paint.measureText(line.toString()+cluster)>width){
                result.append(line).append('\n');line.setLength(0);
            }
            line.append(cluster);
        }
        return result.append(line).toString();
    }
    private static void star(Canvas canvas,Paint paint,float cx,float cy,float radius){
        Path path=new Path();path.moveTo(cx,cy-radius);path.lineTo(cx+radius*.27f,cy-radius*.27f);
        path.lineTo(cx+radius,cy);path.lineTo(cx+radius*.27f,cy+radius*.27f);path.lineTo(cx,cy+radius);
        path.lineTo(cx-radius*.27f,cy+radius*.27f);path.lineTo(cx-radius,cy);path.lineTo(cx-radius*.27f,cy-radius*.27f);path.close();canvas.drawPath(path,paint);
    }
}
