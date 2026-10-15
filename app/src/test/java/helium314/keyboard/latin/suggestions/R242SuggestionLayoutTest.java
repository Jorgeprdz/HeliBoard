// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;
import helium314.keyboard.latin.SuggestedWords;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class R242SuggestionLayoutTest {
 @Test public void contextProposalAtIndexZeroIsVisibleInExistingStripWithoutAutoCommit(){
  boolean omit=SuggestionStripLayoutHelper.shouldOmitTypedWord(SuggestedWords.INPUT_STYLE_PREDICTION,false,true);
  assertFalse(omit);
  assertEquals(1,SuggestionStripLayoutHelper.getPositionInSuggestionStrip(0,false,omit,1,0));
  assertEquals(-1,SuggestionStripLayoutHelper.getPositionInSuggestionStrip(0,false,true,1,0));
 }
}
