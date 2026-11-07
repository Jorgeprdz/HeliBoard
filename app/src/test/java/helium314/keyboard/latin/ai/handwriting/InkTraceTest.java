package helium314.keyboard.latin.ai.handwriting;
import org.junit.Test;
import static org.junit.Assert.*;
public class InkTraceTest {
 @Test public void cancelledStrokeIsNotRecognized(){InkTrace t=new InkTrace();t.begin(1,2,3);t.point(3,4,5);t.cancel();assertTrue(t.strokes().isEmpty());}
 @Test public void immutableSnapshotsAndUndoPreserveStrokeOrder(){InkTrace t=new InkTrace();t.begin(1,2,3);t.end(2,3,4);java.util.List<java.util.List<InkTrace.Point>> old=t.strokes();t.begin(3,4,5);t.end(4,5,6);assertEquals(1,old.size());assertEquals(2,t.strokes().size());t.undo();assertEquals(1,t.strokes().size());assertEquals(3,t.strokes().get(0).get(0).time);t.clear();assertTrue(t.strokes().isEmpty());}
}
