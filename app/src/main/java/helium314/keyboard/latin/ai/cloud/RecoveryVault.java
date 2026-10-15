// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.cloud;
import android.content.Context;import android.security.keystore.*;import java.security.KeyStore;
import java.io.*;import java.nio.*;import java.nio.charset.StandardCharsets;import javax.crypto.*;import javax.crypto.spec.*;
/** Device-local wrapping only. Portable recovery always uses the user's phrase, never this key. */
final class RecoveryVault {
 private static final String ALIAS="heliboard_typing_cloud_v1";
 static void save(Context c,char[] phrase)throws Exception{
  if(phrase.length<20)throw new IllegalArgumentException("Use a long recovery phrase");
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
   g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,store.getKey(ALIAS,null));
  ByteBuffer encoded=StandardCharsets.UTF_8.encode(CharBuffer.wrap(phrase));byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);
  try{
   byte[] encrypted=cipher.doFinal(bytes);
   android.util.AtomicFile file=new android.util.AtomicFile(new File(c.getNoBackupFilesDir(),"typing-recovery"));
   FileOutputStream out=file.startWrite();
   try{out.write(cipher.getIV());out.write(encrypted);file.finishWrite(out);}catch(IOException e){file.failWrite(out);throw e;}
  }finally{java.util.Arrays.fill(bytes,(byte)0);if(encoded.hasArray())java.util.Arrays.fill(encoded.array(),(byte)0);}
 }
 static char[] read(Context c)throws Exception{
  android.util.AtomicFile file=new android.util.AtomicFile(new File(c.getNoBackupFilesDir(),"typing-recovery"));
  byte[] data=file.readFully();if(data.length<28||data.length>2048)throw new IOException("Recovery vault unavailable");
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,store.getKey(ALIAS,null),new GCMParameterSpec(128,java.util.Arrays.copyOf(data,12)));
  byte[] plain=cipher.doFinal(data,12,data.length-12);
  try{CharBuffer decoded=StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain));char[] result=new char[decoded.remaining()];decoded.get(result);if(decoded.hasArray())java.util.Arrays.fill(decoded.array(),'\0');return result;}
  finally{java.util.Arrays.fill(plain,(byte)0);}
 }
 static void erase(Context c){new android.util.AtomicFile(new File(c.getNoBackupFilesDir(),"typing-recovery")).delete();}
}
