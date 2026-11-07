package helium314.keyboard.latin.ai;
import org.junit.Test;import static org.junit.Assert.*;
public class AiConnectionStatusTest {
 @Test public void rejectsBadPasteBeforeCredentialReplacement(){assertTrue(AiConnectionStatus.validKey("AIza_valid-example123"));assertFalse(AiConnectionStatus.validKey("\"AIza_valid\""));assertFalse(AiConnectionStatus.validKey("AIza valid"));assertFalse(AiConnectionStatus.validKey("AIza\nvalid"));assertFalse(AiConnectionStatus.validKey(""));}
 @Test public void knownErrorsAreActionableWithoutSecrets(){assertTrue(AiConnectionStatus.explain("RATE_LIMITED").contains("cuota"));assertTrue(AiConnectionStatus.explain("INVALID_REQUEST").contains("formato"));assertTrue(AiConnectionStatus.explain("FORBIDDEN").contains("permisos"));assertTrue(AiConnectionStatus.explain("OFFLINE").contains("conexión"));}
 @Test public void unknownProviderMessageIsNotEchoed(){assertFalse(AiConnectionStatus.explain("secret-token").contains("secret-token"));}
}
