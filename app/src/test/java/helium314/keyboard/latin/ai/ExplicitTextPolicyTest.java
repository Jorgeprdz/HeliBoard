// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import org.junit.Test;import static org.junit.Assert.*;
public class ExplicitTextPolicyTest {
 private boolean accept(String action,String type,String text){try{return (boolean)Class.forName("helium314.keyboard.latin.ai.ExplicitTextPolicy").getMethod("accepts",String.class,String.class,CharSequence.class).invoke(null,action,type,text);}catch(Exception e){throw new AssertionError("Explicit share/selection policy missing",e);}}
 private boolean returns(String action,boolean readOnly){try{return (boolean)Class.forName("helium314.keyboard.latin.ai.ExplicitTextPolicy").getMethod("mayReturn",String.class,boolean.class).invoke(null,action,readOnly);}catch(Exception e){throw new AssertionError(e);}}
 @Test public void acceptsOnlyExplicitPlainTextActions(){assertTrue(accept("android.intent.action.SEND","text/plain","hola"));assertTrue(accept("android.intent.action.PROCESS_TEXT","text/plain","hola"));assertFalse(accept("android.intent.action.VIEW","text/plain","hola"));assertFalse(accept("android.intent.action.SEND","text/html","hola"));}
 @Test public void boundsExplicitSelection(){assertFalse(accept("android.intent.action.SEND","text/plain",null));assertFalse(accept("android.intent.action.SEND","text/plain","a".repeat(4097)));}
 @Test public void sharedOrReadonlyTextNeverReturnsReplacement(){assertFalse(returns("android.intent.action.SEND",false));assertFalse(returns("android.intent.action.PROCESS_TEXT",true));assertTrue(returns("android.intent.action.PROCESS_TEXT",false));}
}
