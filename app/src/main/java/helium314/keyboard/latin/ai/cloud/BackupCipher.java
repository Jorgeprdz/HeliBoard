// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.cloud;
import java.nio.ByteBuffer;import java.security.*;import java.util.Arrays;
import javax.crypto.*;import javax.crypto.spec.*;
/** Versioned authenticated envelope. PBKDF2-SHA256 600k: OWASP floor, no extra native library. */
public final class BackupCipher {
 private static final int HEADER=37,MAX=2_000_000,ITERATIONS=600_000;
 private static final SecureRandom RANDOM=new SecureRandom();
 private BackupCipher(){}
 public static byte[] encrypt(byte[] plain,char[] phrase)throws GeneralSecurityException{
  if(plain==null||plain.length>MAX||phrase==null||phrase.length<20)throw new GeneralSecurityException("Invalid backup input");
  byte[] salt=new byte[16],nonce=new byte[12];RANDOM.nextBytes(salt);RANDOM.nextBytes(nonce);
  byte[] header=ByteBuffer.allocate(HEADER).putInt(0x48323342).put((byte)1).putInt(ITERATIONS).put(salt).put(nonce).array();
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");byte[] key=derive(phrase,salt);
  try{cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(header);
   byte[] encrypted=cipher.doFinal(plain);return ByteBuffer.allocate(HEADER+encrypted.length).put(header).put(encrypted).array();
  }finally{Arrays.fill(key,(byte)0);}
 }
 public static byte[] decrypt(byte[] packed,char[] phrase)throws GeneralSecurityException{
  if(packed==null||packed.length<HEADER+16||packed.length>MAX+HEADER+16||phrase==null)throw new GeneralSecurityException("Invalid envelope");
  ByteBuffer in=ByteBuffer.wrap(packed);
  if(in.getInt()!=0x48323342||in.get()!=1||in.getInt()!=ITERATIONS)throw new GeneralSecurityException("Unsupported backup format");
  byte[] salt=new byte[16],nonce=new byte[12];in.get(salt);in.get(nonce);
  byte[] key=derive(phrase,salt);
  try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
   cipher.updateAAD(Arrays.copyOf(packed,HEADER));return cipher.doFinal(packed,HEADER,packed.length-HEADER);
  }finally{Arrays.fill(key,(byte)0);}
 }
 private static byte[] derive(char[] phrase,byte[] salt)throws GeneralSecurityException{
  PBEKeySpec spec=new PBEKeySpec(phrase,salt,ITERATIONS,256);
  try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
 }
}
