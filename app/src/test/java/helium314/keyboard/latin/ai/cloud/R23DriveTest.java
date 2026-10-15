package helium314.keyboard.latin.ai.cloud;
import org.junit.Test;import static org.junit.Assert.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;import java.util.concurrent.atomic.AtomicInteger;
import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;
@RunWith(RobolectricTestRunner.class)
public class R23DriveTest {
 static class Response extends HttpURLConnection {
  final int status;final byte[] payload;final ByteArrayOutputStream sent=new ByteArrayOutputStream();boolean disconnected;
  Response(URL url,int status,String payload){super(url);this.status=status;this.payload=payload.getBytes(StandardCharsets.UTF_8);}
  public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}public void connect(){}
  public int getResponseCode(){return status;}public InputStream getInputStream(){return new ByteArrayInputStream(payload);}
  public OutputStream getOutputStream(){return sent;}
 }
 @Test public void appDataScopeListUsesPrivateSpaceAndNeverFollowsRedirects()throws Exception{
  final Response[] response={null};DriveAppData drive=new DriveAppData("opaque",()->false,url->response[0]=new Response(url,200,"{\"files\":[{\"id\":\"backup1\",\"name\":\"file.bin\"}]}"));
  assertEquals("backup1",drive.list().getJSONObject(0).getString("id"));
  assertTrue(response[0].getURL().getQuery().contains("spaces=appDataFolder"));
  assertFalse(response[0].getInstanceFollowRedirects());assertTrue(response[0].disconnected);
 }
 @Test public void uploadBodyIsAlreadyEncryptedAndCancellationMakesNoRequest()throws Exception{
  byte[] ciphertext=new byte[]{0,1,2,3};final Response[] response={null};AtomicBoolean cancelled=new AtomicBoolean();
  DriveAppData drive=new DriveAppData("opaque",cancelled::get,url->response[0]=new Response(url,200,"{}"));
  drive.upload("heliboard-typing-v1-aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.bin","backup1",ciphertext);
  assertArrayEquals(ciphertext,response[0].sent.toByteArray());assertEquals("PATCH",response[0].getRequestMethod());
  cancelled.set(true);response[0]=null;
  try{drive.download("backup1");fail("Cancelled transfer started");}catch(InterruptedIOException expected){}
  assertNull(response[0]);
 }
 @Test public void networkInterruptionCanResumeAndAuthorizationFailuresKeepTheirStatus()throws Exception{
  AtomicInteger calls=new AtomicInteger();
  DriveAppData drive=new DriveAppData("opaque",()->false,url->{if(calls.getAndIncrement()==0)throw new IOException("offline");return new Response(url,200,"{\"files\":[]}");});
  try{drive.list();fail("Network error hidden");}catch(IOException expected){}
  assertEquals(0,drive.list().length());
  DriveAppData denied=new DriveAppData("opaque",()->false,url->new Response(url,401,"private error body"));
  try{denied.list();fail("Authorization failure hidden");}catch(DriveAppData.NetworkFailure expected){assertEquals(401,expected.status);assertFalse(expected.getMessage().contains("private"));}
 }
 @Test public void durableJobsBatchChangesAndDisableCancelsAllWork(){
  android.content.Context context=androidx.test.core.app.ApplicationProvider.getApplicationContext();
  TypingSync.prefs(context).edit().putBoolean("enabled",true).commit();
  android.app.job.JobScheduler jobs=(android.app.job.JobScheduler)context.getSystemService(android.content.Context.JOB_SCHEDULER_SERVICE);
  jobs.cancelAll();TypingSync.scheduleChanges(context);TypingSync.scheduleChanges(context);
  assertEquals(1,jobs.getAllPendingJobs().size());
  android.app.job.JobInfo scheduled=jobs.getAllPendingJobs().get(0);
  assertTrue(scheduled.isPersisted());assertTrue(scheduled.isRequireBatteryNotLow());assertEquals(600000,scheduled.getMinLatencyMillis());
  TypingSync.activate(context);assertEquals(3,jobs.getAllPendingJobs().size());
  TypingSync.disable(context);assertTrue(jobs.getAllPendingJobs().isEmpty());
 }
}
