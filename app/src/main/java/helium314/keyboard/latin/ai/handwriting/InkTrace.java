// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.handwriting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Bounded ordered strokes; recognizer receives completed strokes only. */
public final class InkTrace {
 public static final class Point {public final float x,y;public final long time;Point(float x,float y,long t){this.x=x;this.y=y;time=t;}}
 private final List<List<Point>> strokes=new ArrayList<>();private List<Point> active;
 public void begin(float x,float y,long time){active=new ArrayList<>();point(x,y,time);}
 public void point(float x,float y,long time){if(active!=null&&active.size()<2048)active.add(new Point(x,y,time));}
 public void end(float x,float y,long time){point(x,y,time);if(active!=null&&strokes.size()<128)strokes.add(Collections.unmodifiableList(active));active=null;}
 public void cancel(){active=null;}
 public void clear(){strokes.clear();active=null;}
 public void undo(){cancel();if(!strokes.isEmpty())strokes.remove(strokes.size()-1);}
 public List<List<Point>> strokes(){return Collections.unmodifiableList(new ArrayList<>(strokes));}
 public List<Point> active(){return active==null?Collections.emptyList():Collections.unmodifiableList(active);}
}
