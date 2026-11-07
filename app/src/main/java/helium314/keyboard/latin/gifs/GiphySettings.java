// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM ciphertext in noBackup storage, unlocked-user gated. No plaintext fallback. */
public final class GiphySettings {
    private static final String ALIAS="heliboard_giphy_v1";
    private static File file(Context context){
        File directory=context.getNoBackupFilesDir();
        if(Build.VERSION.SDK_INT>=24){
            android.os.UserManager users=(android.os.UserManager)context.getSystemService(Context.USER_SERVICE);
            if(users==null||!users.isUserUnlocked())throw new IllegalStateException("User locked");

        }
        return new File(directory,"giphy-key.enc");
    }
    public static String readKey(Context context){
        if(Build.VERSION.SDK_INT<23)return "";
        try{
            File file=file(context);if(!file.isFile()||file.length()>4096)return "";
            byte[] data=new byte[(int)file.length()];
            try(java.io.FileInputStream in=new java.io.FileInputStream(file)){new java.io.DataInputStream(in).readFully(data);}
            if(data.length<29)return "";
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            SecretKey key=(SecretKey)store.getKey(ALIAS,null);if(key==null)return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,java.util.Arrays.copyOf(data,12)));
            return new String(cipher.doFinal(data,12,data.length-12),StandardCharsets.UTF_8);
        }catch(Exception ignored){return "";}
    }
    public static boolean saveKey(Context context,String value){
        if(Build.VERSION.SDK_INT<23||!value.matches("[a-zA-Z0-9_-]{10,256}"))return false;
        try{
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            if(!store.containsAlias(ALIAS)){
                KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
                generator.generateKey();
            }
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,(SecretKey)store.getKey(ALIAS,null));
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            android.util.AtomicFile out=new android.util.AtomicFile(file(context));
            java.io.FileOutputStream stream=null;
            try{stream=out.startWrite();stream.write(cipher.getIV());stream.write(encrypted);out.finishWrite(stream);}
            catch(Exception e){if(stream!=null)out.failWrite(stream);return false;}
            return true;
        }catch(Exception ignored){return false;}
    }
    public static void forget(Context context){
        try{new android.util.AtomicFile(file(context)).delete();}catch(Exception ignored){}
        try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}catch(Exception ignored){}
    }
}
