package helium314.keyboard.latin.ai;
import org.junit.Test;import static org.junit.Assert.*;
public class R23BackupTest {
 private byte[] call(String method,byte[] data,char[] pass) throws Exception {
  return (byte[])Class.forName("helium314.keyboard.latin.ai.cloud.BackupCipher")
   .getMethod(method,byte[].class,char[].class).invoke(null,data,pass);
 }
 @Test public void recoveryAcrossInstancesAndFreshNonce() throws Exception {
  byte[] data="private learned pairs".getBytes("UTF-8");char[] pass="six long random recovery words together".toCharArray();
  byte[] a=call("encrypt",data,pass),b=call("encrypt",data,pass);
  assertFalse(java.util.Arrays.equals(a,b));assertArrayEquals(data,call("decrypt",a,pass));
 }
 @Test public void tamperingWrongPasswordAndUnknownFormatFailClosed() throws Exception {
  char[] pass="six long random recovery words together".toCharArray();
  byte[] encrypted=call("encrypt",new byte[]{1,2,3},pass);
  try{call("decrypt",encrypted,"wrong recovery password".toCharArray());fail("Wrong password accepted");}catch(java.lang.reflect.InvocationTargetException expected){}
  encrypted[encrypted.length-1]^=1;
  try{call("decrypt",encrypted,pass);fail("Tampering accepted");}catch(java.lang.reflect.InvocationTargetException expected){}
  try{call("decrypt",new byte[80],pass);fail("Unknown version accepted");}catch(java.lang.reflect.InvocationTargetException expected){}
 }
}
