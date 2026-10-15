// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.cloud;
import android.content.*;import android.os.Build;import androidx.work.*;import java.util.*;import java.util.concurrent.*;
import com.google.android.gms.auth.api.identity.*;import com.google.android.gms.common.api.Scope;import com.google.android.gms.tasks.Tasks;
import helium314.keyboard.latin.ai.local.PersonalTypingStore;import org.json.*;
/** Durable work, batched changes and periodic reconciliation. Never runs on a key-event thread. */
public final class TypingSync extends Worker {
 private static final Object SYNC=new Object();
 public TypingSync(Context c,WorkerParameters p){super(c,p);}
 static Context storage(Context c){Context app=c.getApplicationContext();return Build.VERSION.SDK_INT>=24?app.createCredentialProtectedStorageContext():app;}
 static android.content.SharedPreferences prefs(Context c){return storage(c).getSharedPreferences("typing_cloud",0);}
 static AuthorizationRequest request(Context c){
  String account=prefs(c).getString("account","");
  if(account.isEmpty())throw new IllegalStateException("Choose Google account first");
  return AuthorizationRequest.builder().setAccount(new android.accounts.Account(account,"com.google"))
   .setRequestedScopes(Collections.singletonList(new Scope(DriveAppData.SCOPE))).build();
 }
 private static Constraints constraints(){return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build();}
 public static void scheduleChanges(Context c){
  if(Build.VERSION.SDK_INT<26||!prefs(c).getBoolean("enabled",false))return;
  WorkManager.getInstance(c).enqueueUniqueWork("typing-cloud-changes",ExistingWorkPolicy.KEEP,
   new OneTimeWorkRequest.Builder(TypingSync.class).setInitialDelay(10,TimeUnit.MINUTES).setConstraints(constraints())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build());
 }
 static void activate(Context c){
  WorkManager wm=WorkManager.getInstance(c);
  wm.enqueueUniquePeriodicWork("typing-cloud-periodic",ExistingPeriodicWorkPolicy.KEEP,
   new PeriodicWorkRequest.Builder(TypingSync.class,12,TimeUnit.HOURS).setConstraints(constraints()).build());
  wm.enqueueUniqueWork("typing-cloud-initial",ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(TypingSync.class).setConstraints(constraints()).build());
 }
 static void disable(Context c){
  prefs(c).edit().putBoolean("enabled",false).putString("status","Desactivado").apply();
  WorkManager wm=WorkManager.getInstance(c);for(String name:new String[]{"typing-cloud-changes","typing-cloud-periodic","typing-cloud-initial"})wm.cancelUniqueWork(name);
 }
 private void status(String value){prefs(getApplicationContext()).edit().putString("status",value).apply();}
 @Override public Result doWork(){
  if(Build.VERSION.SDK_INT<26||!prefs(getApplicationContext()).getBoolean("enabled",false))return Result.success();
  synchronized(SYNC){
   Context c=storage(getApplicationContext());char[] phrase=null;
   try{
    AuthorizationResult authorization=Tasks.await(Identity.getAuthorizationClient(c).authorize(request(c)),45,TimeUnit.SECONDS);
    if(authorization.hasResolution()||authorization.getAccessToken()==null){status("Se necesita autorización de Google");return Result.failure();}
    phrase=RecoveryVault.read(c);
    DriveAppData drive=new DriveAppData(authorization.getAccessToken(),()->isStopped()||!prefs(c).getBoolean("enabled",false));
    JSONArray files=drive.list();List<byte[]> authenticated=new ArrayList<>();
    String device=prefs(c).getString("backup_device","");
    if(device.isEmpty()){device=UUID.randomUUID().toString();if(!prefs(c).edit().putString("backup_device",device).commit())throw new java.io.IOException("Local identity unavailable");}
    String name="heliboard-typing-v1-"+device+".bin",existing=null;
    for(int i=0;i<files.length();i++){
     JSONObject file=files.getJSONObject(i);String id=file.getString("id");
     authenticated.add(BackupCipher.decrypt(drive.download(id),phrase));
     if(name.equals(file.getString("name")))existing=id;
    }
    // Authenticate every envelope before importing anything. Schema validation is atomic per file.
    PersonalTypingStore learning=PersonalTypingStore.get(c);
    for(byte[] data:authenticated){learning.merge(data);Arrays.fill(data,(byte)0);}
    if(isStopped()||!prefs(c).getBoolean("enabled",false))return Result.success();
    byte[] snapshot=learning.exportData();
    try{drive.upload(name,existing,BackupCipher.encrypt(snapshot,phrase));}finally{Arrays.fill(snapshot,(byte)0);}
    status("Sincronizado · "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date()));return Result.success();
   }catch(java.security.GeneralSecurityException|IllegalArgumentException integrity){
    status("Respaldo bloqueado: frase incorrecta, formato incompatible o datos alterados");return Result.failure();
   }catch(DriveAppData.NetworkFailure http){
    status("Drive no disponible: HTTP "+http.status);
    if(http.status==401||http.status==403){status("Reautoriza Google Drive en configuración");return Result.failure();}
    return getRunAttemptCount()<5?Result.retry():Result.failure();
   }catch(Exception unavailable){
    status("Sincronización pendiente; revisa conexión, OAuth y desbloqueo");
    return getRunAttemptCount()<5?Result.retry():Result.failure();
   }finally{if(phrase!=null)Arrays.fill(phrase,'\0');}
  }
 }
}
