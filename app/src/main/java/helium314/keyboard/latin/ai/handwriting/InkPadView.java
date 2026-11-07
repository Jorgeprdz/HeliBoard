// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.handwriting;
import android.content.Context;
import android.graphics.Canvas;
import helium314.keyboard.latin.ai.AiMenuTheme;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;
/** Native finger/stylus surface with no text or image upload. */
public final class InkPadView extends View {
 private final InkTrace trace=new InkTrace();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Runnable changed;
 public InkPadView(Context c,Runnable changed){super(c);this.changed=changed;setContentDescription("Área de escritura a mano");AiMenuTheme colors=AiMenuTheme.resolve(c);setBackgroundColor(colors.focused);paint.setColor(colors.onSurface);paint.setStrokeWidth(5);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);}
 public List<List<InkTrace.Point>> strokes(){return trace.strokes();}
 public void clear(){trace.clear();invalidate();changed.run();}
 public void undo(){trace.undo();invalidate();changed.run();}
 @Override protected void onDraw(Canvas c){super.onDraw(c);for(List<InkTrace.Point> stroke:trace.strokes())draw(c,stroke);draw(c,trace.active());}
 private void draw(Canvas c,List<InkTrace.Point> points){if(points.isEmpty())return;Path p=new Path();p.moveTo(points.get(0).x,points.get(0).y);for(int i=1;i<points.size();i++)p.lineTo(points.get(i).x,points.get(i).y);if(points.size()==1)c.drawPoint(points.get(0).x,points.get(0).y,paint);else c.drawPath(p,paint);}
 @Override public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();long t=e.getEventTime();switch(e.getActionMasked()){
  case MotionEvent.ACTION_DOWN:getParent().requestDisallowInterceptTouchEvent(true);trace.begin(x,y,t);changed.run();break;
  case MotionEvent.ACTION_MOVE:for(int i=0;i<e.getHistorySize();i++)trace.point(e.getHistoricalX(i),e.getHistoricalY(i),e.getHistoricalEventTime(i));trace.point(x,y,t);break;
  case MotionEvent.ACTION_UP:trace.end(x,y,t);changed.run();performClick();break;
  case MotionEvent.ACTION_CANCEL:trace.cancel();changed.run();break;
  default:return true;
 }invalidate();return true;}
 @Override public boolean performClick(){super.performClick();return true;}
}
