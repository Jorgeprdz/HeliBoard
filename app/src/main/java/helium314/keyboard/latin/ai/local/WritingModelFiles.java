// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import android.content.Context;import java.io.*;import java.net.*;import java.security.*;import javax.net.ssl.HttpsURLConnection;
import helium314.keyboard.latin.ai.AiSessionCoordinator;
/** Explicit settings action only. No network API is reachable from inference or key events. */
public final class WritingModelFiles {
 public static final String SHA="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db";
 public static final long SIZE=491400032L;
 public static final String URL="https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf";
 private WritingModelFiles(){}
 public static File file(Context context){return new File(context.getNoBackupFilesDir(),"qwen25-writing-q4km.gguf");}
 public static boolean enabled(Context context){return context.getSharedPreferences("keyboard_ai_private",0).getBoolean("local_model_consent",false);}
 public static boolean available(Context context){return android.os.Build.VERSION.SDK_INT>=24&&java.util.Arrays.asList(android.os.Build.SUPPORTED_ABIS).contains("arm64-v8a")&&file(context).length()==SIZE;}
 public static boolean verify(File file,AiSessionCoordinator.Cancellation cancel)throws Exception{
  if(file.length()!=SIZE)return false;
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){if(cancel.cancelled())return false;digest.update(buffer,0,n);}}
  StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
  return SHA.contentEquals(hex);
 }
 public static void download(Context context,AiSessionCoordinator.Cancellation cancel)throws Exception{
  if(android.os.Build.VERSION.SDK_INT<24)throw new IOException("API 24 required");
  if(!enabled(context))throw new IOException("consent required");
  File target=file(context),partial=new File(target.getPath()+".part");
  HttpsURLConnection connection=(HttpsURLConnection)new URL(URL).openConnection();
  connection.setConnectTimeout(15000);connection.setReadTimeout(15000);
  cancel.onCancel(connection::disconnect);
  try{
   connection.connect();if(connection.getResponseCode()!=200)throw new IOException("download unavailable");
   if(connection.getContentLengthLong()>SIZE)throw new IOException("unexpected size");
   try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(partial)){
    byte[] buffer=new byte[65536];int n;long total=0;
    while((n=in.read(buffer))!=-1){
     if(cancel.cancelled()||!enabled(context))throw new IOException("cancelled");
     total+=n;if(total>SIZE)throw new IOException("unexpected size");out.write(buffer,0,n);
    }out.getFD().sync();
   }
   if(!verify(partial,cancel)||cancel.cancelled()||!enabled(context))throw new IOException("integrity/cancellation");
   if(!partial.renameTo(target))throw new IOException("atomic installation failed");
  }finally{connection.disconnect();if(partial.exists()&&!partial.delete())partial.deleteOnExit();}
 }
}
