// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class AiResultReviewTest {
 private final Object editor=new Object();
 private EditorSnapshot snapshot(int start){return new EditorSnapshot(editor,"editor",7,1,1,start,start+4,"hola");}
 private Object review(){try{return Class.forName("helium314.keyboard.latin.ai.AiResultReview").getConstructor().newInstance();}catch(Exception e){throw new AssertionError("Review acceptance gate missing",e);}}
 private Object call(Object r,String name,Class<?>[] types,Object...args){try{return r.getClass().getMethod(name,types).invoke(r,args);}catch(Exception e){throw new AssertionError(e);}}
 private void offer(Object r,boolean complete){call(r,"offer",new Class[]{EditorSnapshot.class,String.class,boolean.class},snapshot(0),"Hola",complete);}
 private boolean allowed(Object r,int start){return (boolean)call(r,"canApply",new Class[]{EditorSnapshot.class},snapshot(start));}
 @Test public void requiresExplicitReviewBeforeAccept(){Object r=review();offer(r,true);assertFalse(allowed(r,0));call(r,"reviewed",new Class[]{EditorSnapshot.class},snapshot(0));assertTrue(allowed(r,0));}
 @Test public void truncatedResponseCannotBecomeAcceptableThroughPreview(){Object r=review();offer(r,false);call(r,"reviewed",new Class[]{EditorSnapshot.class},snapshot(0));assertFalse(allowed(r,0));}
 @Test public void identicalTextAtAnotherRangeCannotAccept(){Object r=review();offer(r,true);call(r,"reviewed",new Class[]{EditorSnapshot.class},snapshot(0));assertFalse(allowed(r,12));}
 @Test public void replacementResultResetsPreviousReview(){Object r=review();offer(r,true);call(r,"reviewed",new Class[]{EditorSnapshot.class},snapshot(0));offer(r,true);assertFalse(allowed(r,0));}
 @Test public void changedNegationAndAmountsAreFlagged(){Object r=review();EditorSnapshot s=new EditorSnapshot(editor,"editor",7,1,1,0,11,"No pago 500");call(r,"offer",new Class[]{EditorSnapshot.class,String.class,boolean.class},s,"Pago 5000",true);assertTrue((boolean)call(r,"meaningRisk",new Class[]{}));}
}
