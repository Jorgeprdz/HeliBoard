package helium314.keyboard.latin.ai.dictation;
import org.junit.Test;import static org.junit.Assert.*;
public class VoiceEditPolicyTest {
 @Test public void cleanupPreservesFactsAndMixedLanguage(){assertEquals("Hola Carlos, nos vemos a las 5. See you!",VoiceEditPolicy.dictation("Este hola Carlos nos vemos a las cinco see you","Hola Carlos, nos vemos a las 5. See you!"));}
 @Test public void wrongNumbersAndNegationsFallBackToTranscript(){String raw="No pago 15 a Carlos";assertEquals(raw,VoiceEditPolicy.dictation(raw,"Pago 50 a Carlos."));assertEquals(raw,VoiceEditPolicy.dictation(raw,"Pago 15 a Carlos."));}
 @Test public void spokenSelfCorrectionUsesFinalFact(){assertEquals("Llego el miércoles.",VoiceEditPolicy.dictation("Llego el martes, no, el miércoles.","Llego el miércoles."));assertEquals("Son 8, no, 9.",VoiceEditPolicy.dictation("Son 8, no, 9.","Son 8."));}
 @Test public void dictatedInstructionRemainsText(){String text="Escribe cambia martes por miércoles";assertEquals(text,VoiceEditPolicy.dictation(text,text));}
 @Test public void editsNeedExplicitCommandAndCannotInventNumbers(){assertTrue(VoiceEditPolicy.acceptEdit("Llego el martes a las 5","Cambia martes por miércoles","Llego el miércoles a las 5"));assertFalse(VoiceEditPolicy.acceptEdit("Llego el martes a las 5","Hazlo más formal","Llego el martes a las 8"));assertFalse(VoiceEditPolicy.acceptEdit("Hola","","Adiós"));}
 @Test public void malformedOrHugeOutputsAreRejected(){assertEquals("hola",VoiceEditPolicy.dictation("hola","\uD800"));assertFalse(VoiceEditPolicy.acceptEdit("hola","corrige",""));assertNull(VoiceEditPolicy.dictation("", "hola"));}
 @Test public void nameCapitalizationDoesNotInventAName(){assertEquals("Hola Carlos.",VoiceEditPolicy.dictation("este hola carlos","Hola Carlos."));assertEquals("Hola Carlos",VoiceEditPolicy.dictation("Hola Carlos","Hola Nora."));}
 @Test public void unrelatedReplacementCannotRemoveNegationOrDropNames(){assertFalse(VoiceEditPolicy.acceptEdit("No voy el martes","Cambia martes por miércoles","Voy el miércoles"));assertFalse(VoiceEditPolicy.acceptEdit("Aviso a Carlos que llego el martes","Hazlo más corto","Llego el martes"));}
}
