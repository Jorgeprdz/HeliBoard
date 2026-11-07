// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocalGestureDecoderTest {
 private List<LocalGestureDecoder.KeyPoint> keys(float shift){
  return Arrays.asList(new LocalGestureDecoder.KeyPoint('c',shift,100,80),
   new LocalGestureDecoder.KeyPoint('a',shift+100,0,80),new LocalGestureDecoder.KeyPoint('u',shift+100,180,80),
   new LocalGestureDecoder.KeyPoint('t',shift+200,100,80),new LocalGestureDecoder.KeyPoint('b',shift+100,100,80));
 }
 private LocalGestureDecoder decoder(float shift){return new LocalGestureDecoder(keys(shift),Arrays.asList(
  new LocalGestureDecoder.Word("cat",180),new LocalGestureDecoder.Word("cut",250),new LocalGestureDecoder.Word("cab",200)));}
 @Test public void ranksActualBentTraceAboveMoreFrequentWrongShape(){
  LocalGestureDecoder d=decoder(0);
  List<LocalGestureDecoder.Match> result=d.decode(new int[]{0,50,100,150,200},new int[]{100,50,0,50,100},5,3);
  assertFalse(result.isEmpty());assertEquals("cat",result.get(0).word);
 }
 @Test public void usesCurrentKeyboardGeometryAndRejectsOldCoordinates(){
  LocalGestureDecoder d=decoder(1000);
  assertEquals("cat",d.decode(new int[]{1000,1100,1200},new int[]{100,0,100},3,3).get(0).word);
  assertTrue(d.decode(new int[]{0,100,200},new int[]{100,0,100},3,3).isEmpty());
 }
 @Test public void samplingRateDoesNotChangeBestWord(){
  LocalGestureDecoder d=decoder(0);
  assertEquals("cat",d.decode(new int[]{0,100,200},new int[]{100,0,100},3,3).get(0).word);
  assertEquals("cat",d.decode(new int[]{0,0,25,50,75,100,100,125,150,175,200},new int[]{100,100,75,50,25,0,0,25,50,75,100},11,3).get(0).word);
 }
 @Test public void malformedShortAndDistantTracesAbstain(){
  LocalGestureDecoder d=decoder(0);
  assertTrue(d.decode(new int[]{0},new int[]{100},1,3).isEmpty());
  assertTrue(d.decode(new int[]{900,1000,1200},new int[]{900,900,900},3,3).isEmpty());
  assertTrue(d.decode(new int[]{0,100},new int[]{100},2,3).isEmpty());
  assertTrue(d.decode(new int[]{0,100,200},new int[]{100,0,100},3,0).isEmpty());
 }
 @Test public void unmappableWordsAndPhrasesNeverBecomeCandidates(){
  LocalGestureDecoder d=new LocalGestureDecoder(keys(0),Arrays.asList(new LocalGestureDecoder.Word("a phrase",255),new LocalGestureDecoder.Word("cxt",255)));
  assertTrue(d.decode(new int[]{0,100,200},new int[]{100,0,100},3,3).isEmpty());
 }
}
