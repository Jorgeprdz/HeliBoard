// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.cloud;
import android.content.*;import android.os.Build;import android.app.job.*;import java.util.*;import java.util.concurrent.*;
import com.google.android.gms.auth.api.identity.*;import com.google.android.gms.common.api.Scope;import com.google.android.gms.tasks.Tasks;
import helium314.keyboard.latin.ai.local.PersonalTypingStore;import org.json.*;
/** Durable work, batched changes and periodic reconciliation. Never runs on a key-event thread. */
public final class TypingSync extends JobService {
 private static final Object SYNC=new Object();
 private static final int CHANGES=2301,PERIODIC=2302,INITIAL=2303;
 private final java.util.concurrent.ConcurrentHashMap<Integer,java.util.concurrent.atomic.AtomicBoolean> jobs=new java.util.concurrent.ConcurrentHashMap<>();
 private final ExecutorService background=Executors.newSingleThreadExecutor();
 private enum Result { SUCCESS,RETRY,FAILURE; static Result success(){return SUCCESS;}static Result retry(){return RETRY;}static Result failure(){return FAILURE;} }
 @Override public boolean onStartJob(JobParameters parameters){
  java.util.concurrent.atomic.AtomicBoolean cancellation=new java.util.concurrent.atomic.AtomicBoolean();
  java.util.concurrent.atomic.AtomicBoolean old=jobs.put(parameters.getJobId(),cancellation);if(old!=null)old.set(true);
  background.execute(()->{
   Result result=doWork(()->cancellation.get()||Thread.currentThread().isInterrupted());
   jobs.remove(parameters.getJobId(),cancellation);
   if(!cancellation.get())jobFinished(parameters,result==Result.RETRY);
  });return true;
 }
 @Override public boolean onStopJob(JobParameters parameters){
  java.util.concurrent.atomic.AtomicBoolean cancellation=jobs.remove(parameters.getJobId());
  if(cancellation!=null)cancellation.set(true);
  return prefs(this).getBoolean("enabled",false);
 }
 @Override public void onDestroy(){
  for(java.util.concurrent.atomic.AtomicBoolean cancellation:jobs.values())cancellation.set(true);
  jobs.clear();background.shutdownNow();super.onDestroy();
 }
 private int getRunAttemptCount(){int count=prefs(this).getInt("attempt",0);prefs(this).edit().putInt("attempt",count+1).apply();return count;}
 
 static Context storage(Context c){Context app=c.getApplicationContext();return app;}
 static android.content.SharedPreferences prefs(Context c){return storage(c).getSharedPreferences("typing_cloud",0);}
 static AuthorizationRequest request(Context c){
  String account=prefs(c).getString("account","");
  if(account.isEmpty())throw new IllegalStateException("Choose Google account first");
  return AuthorizationRequest.builder().setAccount(new android.accounts.Account(account,"com.google"))
   .setRequestedScopes(Collections.singletonList(new Scope(DriveAppData.SCOPE))).build();
 }
 private static JobScheduler scheduler(Context c){return (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);}
 @androidx.annotation.RequiresApi(26)
 private static JobInfo.Builder job(Context c,int id){
  return new JobInfo.Builder(id,new ComponentName(c,TypingSync.class))
   .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setRequiresBatteryNotLow(true)
   .setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL);
 }
 public static void scheduleChanges(Context c){
  if(Build.VERSION.SDK_INT<26||!prefs(c).getBoolean("enabled",false))return;
  JobScheduler scheduler=scheduler(c);
  if(scheduler.getPendingJob(CHANGES)==null)scheduler.schedule(job(c,CHANGES).setMinimumLatency(600000).build());
 }
 static void activate(Context c){
  if(Build.VERSION.SDK_INT<26)return;
  JobScheduler scheduler=scheduler(c);
  scheduler.schedule(job(c,PERIODIC).setPeriodic(TimeUnit.HOURS.toMillis(12)).build());
  scheduler.schedule(job(c,INITIAL).build());
 }
 public static void stopSync(Context c){disable(c);}
 static void disable(Context c){
  prefs(c).edit().putBoolean("enabled",false).putString("status","Desactivado").apply();
  JobScheduler scheduler=scheduler(c);for(int id:new int[]{CHANGES,PERIODIC,INITIAL})scheduler.cancel(id);
 }
 private void status(String value){prefs(getApplicationContext()).edit().putString("status",value).apply();}
 private Result doWork(java.util.function.BooleanSupplier cancelled){
  if(Build.VERSION.SDK_INT<26||!prefs(getApplicationContext()).getBoolean("enabled",false))return Result.success();
  synchronized(SYNC){
   Context c=storage(getApplicationContext());char[] phrase=null;
   try{
    if(cancelled.getAsBoolean())return Result.success();
    PersonalTypingStore learning=PersonalTypingStore.get(c);long generation=learning.generation();
    AuthorizationResult authorization=Tasks.await(Identity.getAuthorizationClient(c).authorize(request(c)),45,TimeUnit.SECONDS);
    if(authorization.hasResolution()||authorization.getAccessToken()==null){status("Se necesita autorización de Google");return Result.failure();}
    phrase=RecoveryVault.read(c);
    DriveAppData drive=new DriveAppData(authorization.getAccessToken(),()->cancelled.getAsBoolean()||!prefs(c).getBoolean("enabled",false));
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
    for(byte[] data:authenticated)BackupPayload.validate(data,learning);
    for(byte[] data:authenticated){
     if(cancelled.getAsBoolean()||!prefs(c).getBoolean("enabled",false))return Result.success();
     BackupPayload.merge(c,learning,data,generation);Arrays.fill(data,(byte)0);
    }
    if(cancelled.getAsBoolean()||!prefs(c).getBoolean("enabled",false))return Result.success();
    byte[] snapshot=BackupPayload.export(c,learning);
    try{drive.upload(name,existing,BackupCipher.encrypt(snapshot,phrase));}finally{Arrays.fill(snapshot,(byte)0);}
    prefs(c).edit().putInt("attempt",0).putBoolean("restore_visual",false).apply();status("Sincronizado · "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date()));return Result.success();
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
