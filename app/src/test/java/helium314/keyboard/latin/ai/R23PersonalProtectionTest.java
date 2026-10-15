package helium314.keyboard.latin.ai;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
import helium314.keyboard.latin.ai.local.LocalContextReviewer;
public class R23PersonalProtectionTest {
 private boolean allowed(String text,String replacement,Set<String> rejected)throws Exception{
  int start=text.indexOf("teh");
  return (boolean)LocalContextReviewer.class.getMethod("allowsPersonal",String.class,int.class,int.class,String.class,Set.class)
   .invoke(new LocalContextReviewer(),text,start,start+3,replacement,rejected);
 }
 @Test public void usesExistingQuoteCodeUrlAndRejectionProtection()throws Exception{
  for(String text:new String[]{"she wrote \"a teh ","`teh ","https://teh.com ","a@teh.com ","teh_foo "})
   assertFalse(allowed(text,"the",Collections.emptySet()));
  assertFalse(allowed("teh ","the",Collections.singleton("teh→the")));
  assertTrue(allowed("a teh ","the",Collections.emptySet()));
 }
}
