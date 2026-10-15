// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.Build;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Explicit word-pair evidence only. No editor text, key events, audio or phrases.
 * UI lookups use memory; SQLite and backup serialization run on a single writer.
 * A component is a monotonically increasing per-device counter. Merge is max, not sum.
 */
public final class PersonalTypingStore {
 private static PersonalTypingStore instance;
 private static final int LIMIT=4096, MAX_BYTES=2_000_000;
 private final ExecutorService writer=Executors.newSingleThreadExecutor();
 private final Map<String,Row> rows=new TreeMap<>();
 private final Context context;private Database database;private String device;
 private volatile boolean ready;
 public static synchronized PersonalTypingStore get(Context context){
  if(instance==null)instance=new PersonalTypingStore(context);return instance;
 }
 private PersonalTypingStore(Context original){
  Context app=original.getApplicationContext();
  context=Build.VERSION.SDK_INT>=24?app.createCredentialProtectedStorageContext():app;
  writer.execute(()->{
   try {
    android.content.SharedPreferences prefs=context.getSharedPreferences("personal_typing_identity",0);
    device=prefs.getString("device","");
    if(device.isEmpty()){device=UUID.randomUUID().toString();if(!prefs.edit().putString("device",device).commit())return;}
    database=new Database(context);
    try(Cursor c=database.getReadableDatabase().query("evidence",null,null,null,null,null,"updated DESC",Integer.toString(LIMIT))){
     synchronized(rows){while(c.moveToNext()){
      Row r=new Row(c.getString(c.getColumnIndexOrThrow("error")),c.getString(c.getColumnIndexOrThrow("correction")),
       c.getString(c.getColumnIndexOrThrow("language")),c.getString(c.getColumnIndexOrThrow("device")),
       c.getLong(c.getColumnIndexOrThrow("accepts")),c.getLong(c.getColumnIndexOrThrow("rejects")),c.getLong(c.getColumnIndexOrThrow("updated")));
      rows.put(r.key(),r);
     }}
    }ready=true;
   }catch(RuntimeException unavailable){ready=false;}
  });
 }
 private static String normalized(String value){return value==null?"":java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
 private static boolean word(String value){
  return value!=null&&value.length()>=2&&value.length()<=48&&value.matches("[\\p{Ll}\\p{M}]+")
    &&!Arrays.asList("no","not","never","nunca","jamás","sin","ni","nor","neither").contains(value);
 }
 private static boolean locale(String value){return "es-MX".equals(value)||"en-US".equals(value);}
 private static boolean pair(String a,String b,String lang){
  if(!locale(lang)||!word(a)||!word(b)||a.equals(b))return false;
  // General Damerau-Levenshtein admission, including omission/repetition/transposition/accents.
  return distance(a,b)<=Math.min(4,Math.max(1,(Math.max(a.length(),b.length())+1)/3));
 }
 private static int distance(String a,String b){
  int[][] d=new int[a.length()+1][b.length()+1];
  for(int i=0;i<=a.length();i++)d[i][0]=i;for(int j=0;j<=b.length();j++)d[0][j]=j;
  for(int i=1;i<=a.length();i++)for(int j=1;j<=b.length();j++){
   d[i][j]=Math.min(Math.min(d[i-1][j]+1,d[i][j-1]+1),d[i-1][j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
   if(i>1&&j>1&&a.charAt(i-1)==b.charAt(j-2)&&a.charAt(i-2)==b.charAt(j-1))d[i][j]=Math.min(d[i][j],d[i-2][j-2]+1);
  }return d[a.length()][b.length()];
 }
 public void accept(String error,String correction,String language){observe(error,correction,language,false);}
 public void reject(String error,String correction,String language){observe(error,correction,language,true);}
 private void observe(String error,String correction,String language,boolean rejected){
  if(!ready||!word(error)||!word(correction))return;
  String a=normalized(error),b=normalized(correction);if(!pair(a,b,language))return;
  final Row next;
  synchronized(rows){
   Row r=rows.get(a+"\t"+b+"\t"+language+"\t"+device);
   next=new Row(a,b,language,device,Math.min(1_000_000,(r==null?0:r.a)+(rejected?0:1)),
     Math.min(1_000_000,(r==null?0:r.r)+(rejected?1:0)),System.currentTimeMillis());
   rows.put(next.key(),next);prune();
  }
  writer.execute(()->{try{save(next);helium314.keyboard.latin.ai.cloud.TypingSync.scheduleChanges(context);}catch(RuntimeException unavailable){ready=false;}});
 }
 /** A known legitimate word is always protected. Any rejection vetoes auto-replacement. */
 public String automatic(String word,String language,boolean dictionaryValid){
  if(dictionaryValid||!ready||!word(word)||!locale(language))return null;
  Map<String,long[]> totals=new HashMap<>();
  synchronized(rows){for(Row r:rows.values())if(r.error.equals(word)&&r.language.equals(language)){
   long[] counts=totals.computeIfAbsent(r.correction,k->new long[2]);counts[0]+=r.a;counts[1]+=r.r;
  }}
  String best=null;long first=0,second=0;boolean rejected=false;
  for(Map.Entry<String,long[]> e:totals.entrySet()){
   long score=e.getValue()[0];if(score>first){second=first;first=score;best=e.getKey();rejected=e.getValue()[1]>0;}else second=Math.max(second,score);
  }
  return best!=null&&!rejected&&first>=5&&first-second>=4&&second*4<first?best:null;
 }
 private void prune(){while(rows.size()>LIMIT){Row oldest=Collections.min(rows.values(),Comparator.comparingLong(r->r.t));rows.remove(oldest.key());}}
 private void save(Row r){
  ContentValues v=new ContentValues();v.put("error",r.error);v.put("correction",r.correction);v.put("language",r.language);v.put("device",r.device);
  v.put("accepts",r.a);v.put("rejects",r.r);v.put("frequency",r.a+r.r);v.put("confidence",r.r>0?0.0:Math.min(.99,r.a/5.0));v.put("updated",r.t);
  SQLiteDatabase db=database.getWritableDatabase();db.insertWithOnConflict("evidence",null,v,SQLiteDatabase.CONFLICT_REPLACE);
  db.execSQL("DELETE FROM evidence WHERE rowid NOT IN (SELECT rowid FROM evidence ORDER BY updated DESC LIMIT "+LIMIT+")");
 }
 private <T>T io(Callable<T> task){try{return writer.submit(task).get(30,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException("Learning storage unavailable",e);}}
 /** Background-only API. An authenticated allowlisted, bounded schema; no settings/key dumps. */
 public byte[] exportData(){return io(()->{
  JSONArray all=new JSONArray();synchronized(rows){for(Row r:rows.values())all.put(r.json());}
  return new JSONObject().put("schema",1).put("rows",all).toString().getBytes(StandardCharsets.UTF_8);
 });}
 public void merge(byte[] data){
  if(data==null||data.length>MAX_BYTES)throw new IllegalArgumentException("Backup too large");
  final List<Row> validated=new ArrayList<>();
  try{
   JSONObject root=new JSONObject(new String(data,StandardCharsets.UTF_8));if(root.getInt("schema")!=1)throw new IllegalArgumentException("Unsupported learning schema");
   JSONArray items=root.getJSONArray("rows");if(items.length()>LIMIT)throw new IllegalArgumentException("Too many records");
   Set<String> keys=new HashSet<>();
   for(int i=0;i<items.length();i++){
    JSONObject o=items.getJSONObject(i);Row r=new Row(o.getString("error"),o.getString("correction"),o.getString("language"),o.getString("device"),o.getLong("a"),o.getLong("r"),o.getLong("t"));
    if(!pair(r.error,r.correction,r.language)||!r.device.matches("[a-f0-9-]{36}")||r.a<0||r.r<0||r.a>1_000_000||r.r>1_000_000||r.t<0||!keys.add(r.key()))
     throw new IllegalArgumentException("Invalid learning record");
    validated.add(r);
   }
  }catch(JSONException e){throw new IllegalArgumentException("Invalid learning schema",e);}
  io(()->{
   if(!ready)throw new IllegalStateException("Learning unavailable");
   SQLiteDatabase db=database.getWritableDatabase();db.beginTransaction();
   Map<String,Row> next;
   synchronized(rows){next=new TreeMap<>(rows);for(Row r:validated){
    Row old=next.get(r.key());next.put(r.key(),old==null?r:new Row(r.error,r.correction,r.language,r.device,Math.max(old.a,r.a),Math.max(old.r,r.r),Math.max(old.t,r.t)));
   }}
   if(next.size()>LIMIT){List<Row> ordered=new ArrayList<>(next.values());ordered.sort(Comparator.comparingLong(r->r.t));for(int i=0;i<ordered.size()-LIMIT;i++)next.remove(ordered.get(i).key());}
   try{for(Row r:next.values())save(r);db.setTransactionSuccessful();}finally{db.endTransaction();}
   synchronized(rows){rows.clear();rows.putAll(next);}return null;
  });
 }
 public void erase(){io(()->{if(database==null)throw new IllegalStateException("Learning unavailable");database.getWritableDatabase().delete("evidence",null,null);synchronized(rows){rows.clear();}return null;});}
 private static final class Row {
  final String error,correction,language,device;final long a,r,t;
  Row(String e,String c,String l,String d,long a,long r,long t){error=e;correction=c;language=l;device=d;this.a=a;this.r=r;this.t=t;}
  String key(){return error+"\t"+correction+"\t"+language+"\t"+device;}
  JSONObject json()throws JSONException{return new JSONObject().put("error",error).put("correction",correction).put("language",language).put("device",device).put("a",a).put("r",r).put("t",t);}
 }
 private static final class Database extends SQLiteOpenHelper{
  Database(Context c){super(c,"personal_typing_v1.db",null,1);setWriteAheadLoggingEnabled(true);}
  public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE evidence(error TEXT NOT NULL,correction TEXT NOT NULL,language TEXT NOT NULL,device TEXT NOT NULL,accepts INTEGER NOT NULL,rejects INTEGER NOT NULL,frequency INTEGER NOT NULL,confidence REAL NOT NULL,updated INTEGER NOT NULL,PRIMARY KEY(error,correction,language,device))");}
  public void onUpgrade(SQLiteDatabase db,int old,int next){throw new SQLiteException("Unsupported learning migration; original database retained");}
  public void onDowngrade(SQLiteDatabase db,int old,int next){throw new SQLiteException("Newer learning schema; original database retained");}
 }
}
