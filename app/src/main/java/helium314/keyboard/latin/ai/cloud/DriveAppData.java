// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.cloud;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import org.json.*;import java.util.*;
/** No source, credentials or plaintext learning in requests. Strict HTTPS, bounded responses. */
final class DriveAppData {
 static final String SCOPE="https://www.googleapis.com/auth/drive.appdata";
 interface Connections{HttpURLConnection open(URL url)throws IOException;}
 private final Connections connections;
 private final String token;private final java.util.function.BooleanSupplier cancelled;
 DriveAppData(String token,java.util.function.BooleanSupplier cancelled){this(token,cancelled,url->(HttpURLConnection)url.openConnection());}
 DriveAppData(String token,java.util.function.BooleanSupplier cancelled,Connections connections){this.token=token;this.cancelled=cancelled;this.connections=connections;}
 static final class NetworkFailure extends IOException {final int status;NetworkFailure(int s){super("Drive HTTP "+s);status=s;}}
 JSONArray list()throws Exception{
  String query=URLEncoder.encode("trashed = false and name contains 'heliboard-typing-v1-'","UTF-8");
  JSONObject root=new JSONObject(new String(request("GET","https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=100&fields=nextPageToken,files(id,name,size)&q="+query,null,null),StandardCharsets.UTF_8));
  if(root.has("nextPageToken")||root.getJSONArray("files").length()>64)throw new IOException("Too many backup files; no data discarded");
  return root.getJSONArray("files");
 }
 byte[] download(String id)throws Exception{return request("GET","https://www.googleapis.com/drive/v3/files/"+id(id)+"?alt=media",null,null);}
 void upload(String name,String existing,byte[] encrypted)throws Exception{
  if(!name.matches("heliboard-typing-v1-[a-f0-9-]{36}\\.bin"))throw new IOException("Invalid backup identity");
  if(existing!=null){request("PATCH","https://www.googleapis.com/upload/drive/v3/files/"+id(existing)+"?uploadType=media",encrypted,"application/octet-stream");return;}
  String boundary="heliboard"+UUID.randomUUID().toString().replace("-","");
  JSONObject metadata=new JSONObject().put("name",name).put("parents",new JSONArray().put("appDataFolder"));
  ByteArrayOutputStream body=new ByteArrayOutputStream();
  body.write(("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+metadata+"\r\n--"+boundary+"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
  body.write(encrypted);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
  request("POST","https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",body.toByteArray(),"multipart/related; boundary="+boundary);
 }
 private static String id(String id)throws IOException{if(id==null||!id.matches("[A-Za-z0-9_-]{1,200}"))throw new IOException("Invalid Drive id");return id;}
 private byte[] request(String method,String url,byte[] body,String type)throws Exception{
  if(cancelled.getAsBoolean())throw new InterruptedIOException("Sync stopped");
  HttpURLConnection c=connections.open(new URL(url));c.setInstanceFollowRedirects(false);
  c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);
  try{
   if(body!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);c.setRequestProperty("Content-Type",type);try(OutputStream out=c.getOutputStream()){out.write(body);}}
   int status=c.getResponseCode();if(status<200||status>=300)throw new NetworkFailure(status);
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(cancelled.getAsBoolean())throw new InterruptedIOException("Sync stopped");if(out.size()+n>2_000_100)throw new IOException("Backup too large");out.write(block,0,n);}return out.toByteArray();
   }
  }finally{c.disconnect();}
 }
}
