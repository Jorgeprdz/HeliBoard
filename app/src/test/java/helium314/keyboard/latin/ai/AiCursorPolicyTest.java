// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import org.junit.Test;import static org.junit.Assert.*;
public class AiCursorPolicyTest {
 private int cursor(int a,int b,int start,int end,int length){try{return (int)Class.forName("helium314.keyboard.latin.ai.AiCursorPolicy").getMethod("afterReplacement",int.class,int.class,int.class,int.class,int.class).invoke(null,a,b,start,end,length);}catch(Exception e){throw new AssertionError("Cursor preservation missing",e);}}
 @Test public void earlierCorrectionRetainsPositionInSentence(){assertEquals(22,cursor(20,20,4,9,7));}
 @Test public void selectedRewriteMovesToResultEnd(){assertEquals(11,cursor(4,9,4,9,7));}
 @Test public void dictationAdvancesCursor(){assertEquals(27,cursor(20,20,20,20,7));}
}
