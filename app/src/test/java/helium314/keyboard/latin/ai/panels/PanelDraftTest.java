package helium314.keyboard.latin.ai.panels;
import org.junit.Test;
import static org.junit.Assert.*;
public class PanelDraftTest {
 @Test public void editedSourceInvalidatesTranslationAndCannotCommitSource() {
  PanelDraft d=new PanelDraft("editor"); d.setSource("hola"); long request=d.ticket();
  assertTrue(d.offer(request,"editor","hello",true)); assertEquals("hello",d.result("editor"));
  d.append("!"); assertEquals("",d.result("editor")); assertFalse(d.offer(request,"editor","old",true));
 }
 @Test public void changedEditorAndClosedPanelDiscardResults() {
  PanelDraft d=new PanelDraft("a"); d.setSource("hola"); long request=d.ticket();
  assertFalse(d.offer(request,"b","hello",true)); assertEquals("",d.result("b"));
  d.close(); assertFalse(d.offer(request,"a","hello",true));
 }
 @Test public void incompleteResultsAreNeverApplicableAndDeletionUsesCodePoints() {
  PanelDraft d=new PanelDraft(1); d.setSource("a😀"); d.delete(); assertEquals("a",d.source());
  assertFalse(d.offer(d.ticket(),1,"cut",false)); assertEquals("",d.result(1));
 }
}
