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
 private final Map<String,Long> style=new TreeMap<>();
 private final Set<String> vocabulary=new TreeSet<>();
 private final Context context;private Database database;private String device;
 private volatile boolean ready,loading;
 private volatile RuntimeException storageFailure;
 private long epoch,styleEpoch;
 public static synchronized PersonalTypingStore get(Context context){
  if(instance==null)instance=new PersonalTypingStore(context);else instance.ensureLoaded();return instance;
 }
 private PersonalTypingStore(Context original){
  Context app=original.getApplicationContext();
  context=app;
  ensureLoaded();
 }
 private synchronized void ensureLoaded(){
  if(ready||loading)return;
  if(Build.VERSION.SDK_INT>=24&&!((android.os.UserManager)context.getSystemService(Context.USER_SERVICE)).isUserUnlocked())return;
  loading=true;
  final long initializationEpoch=generation();
  final long initializationStyleEpoch;synchronized(rows){initializationStyleEpoch=styleEpoch;}
  writer.execute(()->{
   try {
    if(Build.VERSION.SDK_INT>=24&&!((android.os.UserManager)context.getSystemService(Context.USER_SERVICE)).isUserUnlocked())return;
    android.content.SharedPreferences prefs=context.getSharedPreferences("personal_typing_identity",0);
    device=prefs.getString("device","");
    if(device.isEmpty()){device=UUID.randomUUID().toString();if(!prefs.edit().putString("device",device).commit())return;}
    if(database==null)database=new Database(context);
    try(Cursor c=database.getReadableDatabase().query("evidence",null,null,null,null,null,"updated DESC",Integer.toString(LIMIT))){
     synchronized(rows){while(c.moveToNext()){
      Row r=new Row(c.getString(c.getColumnIndexOrThrow("error")),c.getString(c.getColumnIndexOrThrow("correction")),
       c.getString(c.getColumnIndexOrThrow("language")),c.getString(c.getColumnIndexOrThrow("device")),
       c.getLong(c.getColumnIndexOrThrow("accepts")),c.getLong(c.getColumnIndexOrThrow("rejects")),c.getLong(c.getColumnIndexOrThrow("updated")));
      if(epoch==initializationEpoch){Row old=rows.get(r.key());rows.put(r.key(),old==null?r:new Row(r.error,r.correction,r.language,r.device,Math.max(old.a,r.a),Math.max(old.r,r.r),Math.max(old.t,r.t)));}
     }}
    }
    Map<String,Long> loadedStyle=new TreeMap<>();Set<String> loadedWords=new TreeSet<>();
    try(Cursor cur=database.getReadableDatabase().query("style",null,null,null,null,null,null)){
     while(cur.moveToNext()){String feature=cur.getString(0);if(PersonalStyleProfile.KEYS.contains(feature))loadedStyle.put(feature,Math.max(0,Math.min(1000000,cur.getLong(1))));}
    }
    try(Cursor cur=database.getReadableDatabase().query("vocabulary",null,null,null,null,null,null,"512")){
     while(cur.moveToNext())loadedWords.add(cur.getString(0)+Character.toString(9)+cur.getString(1));
    }
    synchronized(rows){if(epoch==initializationEpoch){if(styleEpoch==initializationStyleEpoch)style.putAll(loadedStyle);vocabulary.addAll(loadedWords);}}
    storageFailure=null;ready=true;
   }catch(RuntimeException unavailable){storageFailure=unavailable;ready=false;}finally{loading=false;}
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
  return distance(withoutStress(a),withoutStress(b))<=Math.min(4,Math.max(1,(Math.max(a.length(),b.length())+1)/3));
 }
 private static String withoutStress(String value){
  return java.text.Normalizer.normalize(value.replace('ñ',(char)1),java.text.Normalizer.Form.NFD)
    .replaceAll("\\p{M}+","").replace((char)1,'ñ');
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
  ensureLoaded();
  if(!ready||!word(error)||!word(correction))return;
  String a=normalized(error),b=normalized(correction);if(!pair(a,b,language))return;
  final Row next;
  synchronized(rows){
   Row r=rows.get(a+"\t"+b+"\t"+language+"\t"+device);
   next=new Row(a,b,language,device,Math.min(1_000_000,(r==null?0:r.a)+(rejected?0:1)),
     Math.min(1_000_000,(r==null?0:r.r)+(rejected?1:0)),System.currentTimeMillis());
   rows.put(next.key(),next);prune();
  }
  writer.execute(()->{try{Row latest;synchronized(rows){latest=rows.get(next.key());}if(latest!=null)save(latest);helium314.keyboard.latin.ai.cloud.TypingSync.scheduleChanges(context);}catch(RuntimeException unavailable){storageFailure=unavailable;ready=false;}});
 }
 /** A known legitimate word is always protected. Any rejection vetoes auto-replacement. */
 public String automatic(String word,String language,boolean dictionaryValid){
  ensureLoaded();
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
 /** Bounded evidence lookup; no disk IO on the typing thread. */
 public double preference(String error,String correction,String language){
  long accepted=0,rejected=0;
  synchronized(rows){for(Row r:rows.values())if(r.error.equals(error)&&r.correction.equals(correction)&&r.language.equals(language)){accepted+=r.a;rejected+=r.r;}}
  return rejected>0?0:Math.min(.18,accepted*.03);
 }
 public boolean rejected(String error,String correction,String language){
  synchronized(rows){for(Row r:rows.values())if(r.error.equals(error)&&r.correction.equals(correction)&&r.language.equals(language)&&r.r>0)return true;}return false;
 }
 public boolean protectedWord(String value){
  String prefix=normalized(value)+Character.toString(9);synchronized(rows){for(String key:vocabulary)if(key.startsWith(prefix))return true;}return false;
 }
 private static boolean safeVocabulary(String value){
  return value!=null&&value.matches("[\\p{L}\\p{M}]{2,32}")&&!helium314.keyboard.latin.ai.SensitiveInputPolicy.sensitiveText(value)
    &&value.chars().filter(Character::isUpperCase).count()<=1;
 }
 /** Explicit keep-word evidence only. Manual dictionaries remain authoritative as well. */
 public boolean keepWord(String value,String language){
  ensureLoaded();if(!ready||!safeVocabulary(value)||!locale(language))return false;
  String normalized=normalized(value),key=normalized+Character.toString(9)+language;long ticket;
  synchronized(rows){if(vocabulary.size()>=512&&!vocabulary.contains(key))return false;vocabulary.add(key);ticket=epoch;}
  writer.execute(()->{try{synchronized(rows){if(epoch!=ticket||!vocabulary.contains(key))return;}
   ContentValues v=new ContentValues();v.put("word",normalized);v.put("language",language);
   if(database.getWritableDatabase().insertWithOnConflict("vocabulary",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Vocabulary write failed");
  }catch(RuntimeException e){storageFailure=e;}});return true;
 }
 public void forgetWord(String value,String language){
  String normalized=normalized(value);synchronized(rows){vocabulary.remove(normalized+Character.toString(9)+language);}
  writer.execute(()->{try{if(database!=null)database.getWritableDatabase().delete("vocabulary","word=? AND language=?",new String[]{normalized,language});}catch(RuntimeException e){storageFailure=e;}});
 }
 public Map<String,Long> styleSnapshot(){synchronized(rows){return Collections.unmodifiableMap(new TreeMap<>(style));}}
 public List<String> inspect(){
  List<String> result=new ArrayList<>();synchronized(rows){
   for(String word:vocabulary){if(result.size()==40)break;result.add("Conservar: "+word.replace((char)9,' '));}
   for(Row r:rows.values()){if(result.size()==40)break;result.add(r.error+" → "+r.correction+" ["+r.language+"] +"+r.a+" / rechazo "+r.r);}
  }return Collections.unmodifiableList(result);
 }
 /** The caller must supply initial opt-in, field/context policy and one completed sentence. */
 public void observeStyle(String sentence,String language){
  ensureLoaded();if(!ready)return;Map<String,Long> features=PersonalStyleProfile.features(sentence,language);
  if(features.isEmpty())return;long ticket;Map<String,Long> next=new TreeMap<>();
  synchronized(rows){ticket=styleEpoch;for(String key:features.keySet()){
   long value=Math.min(1000000,style.getOrDefault(key,0L)+1);style.put(key,value);next.put(key,value);
  }}
  writer.execute(()->{try{
   synchronized(rows){if(styleEpoch!=ticket)return;}
   SQLiteDatabase db=database.getWritableDatabase();db.beginTransaction();
   try{for(Map.Entry<String,Long> item:next.entrySet()){
    ContentValues v=new ContentValues();v.put("feature",item.getKey());v.put("count",item.getValue());
    if(db.insertWithOnConflict("style",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Style write failed");
   }db.setTransactionSuccessful();}finally{db.endTransaction();}
  }catch(RuntimeException e){storageFailure=e;}});
 }
 public void eraseStyle(){
  synchronized(rows){styleEpoch++;style.clear();}
  writer.execute(()->{try{if(database!=null)database.getWritableDatabase().delete("style",null,null);}catch(RuntimeException e){storageFailure=e;}});
 }
 private void prune(){while(rows.size()>LIMIT){Row oldest=Collections.min(rows.values(),Comparator.comparingLong(r->r.t));rows.remove(oldest.key());}}
 private void save(Row r){
  ContentValues v=new ContentValues();v.put("error",r.error);v.put("correction",r.correction);v.put("language",r.language);v.put("device",r.device);
  v.put("accepts",r.a);v.put("rejects",r.r);v.put("frequency",r.a+r.r);v.put("confidence",r.r>0?0.0:Math.min(.99,r.a/5.0));v.put("updated",r.t);
  SQLiteDatabase db=database.getWritableDatabase();if(db.insertWithOnConflict("evidence",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Evidence write failed");
  db.execSQL("DELETE FROM evidence WHERE rowid NOT IN (SELECT rowid FROM evidence ORDER BY updated DESC LIMIT "+LIMIT+")");
 }
 private <T>T io(Callable<T> task){try{return writer.submit(task).get(30,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException("Learning storage unavailable",e);}}
 /** Background-only API. An authenticated allowlisted, bounded schema; no settings/key dumps. */
 public byte[] exportData(){ensureLoaded();return io(()->{
  if(storageFailure!=null)throw new IllegalStateException("SQLite persistence failed",storageFailure);
  JSONArray all=new JSONArray();synchronized(rows){for(Row r:rows.values())all.put(r.json());}
  return new JSONObject().put("schema",1).put("rows",all).toString().getBytes(StandardCharsets.UTF_8);
 });}
 public long generation(){synchronized(rows){return epoch;}}
 public void validate(byte[] data){parseRows(data);}
 private static List<Row> parseRows(byte[] data){
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
  return validated;
 }
 public void merge(byte[] data){merge(data,generation());}
 public void merge(byte[] data,long expectedGeneration){
  ensureLoaded();
  final List<Row> validated=parseRows(data);
  io(()->{
   if(!ready)throw new IllegalStateException("Learning unavailable");
   synchronized(rows){if(epoch!=expectedGeneration)throw new IllegalStateException("Stale cloud import");}
   SQLiteDatabase db=database.getWritableDatabase();db.beginTransaction();
   Map<String,Row> next;
   synchronized(rows){next=new TreeMap<>(rows);for(Row r:validated){
    Row old=next.get(r.key());next.put(r.key(),old==null?r:new Row(r.error,r.correction,r.language,r.device,Math.max(old.a,r.a),Math.max(old.r,r.r),Math.max(old.t,r.t)));
   }}
   if(next.size()>LIMIT){List<Row> ordered=new ArrayList<>(next.values());ordered.sort(Comparator.comparingLong(r->r.t));for(int i=0;i<ordered.size()-LIMIT;i++)next.remove(ordered.get(i).key());}
   try{for(Row r:next.values())save(r);db.setTransactionSuccessful();}finally{db.endTransaction();}
   synchronized(rows){
    if(epoch!=expectedGeneration)return null;
    for(Row r:rows.values()){Row old=next.get(r.key());next.put(r.key(),old==null?r:new Row(r.error,r.correction,r.language,r.device,Math.max(old.a,r.a),Math.max(old.r,r.r),Math.max(old.t,r.t)));}
    rows.clear();rows.putAll(next);prune();
   }return null;
  });
 }
 public void eraseAsync(){
  synchronized(rows){epoch++;styleEpoch++;rows.clear();style.clear();vocabulary.clear();}
  writer.execute(()->{if(database!=null){
   SQLiteDatabase db=database.getWritableDatabase();db.beginTransaction();
   try{db.delete("evidence",null,null);db.delete("style",null,null);db.delete("vocabulary",null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}
  }});
 }
 public void erase(){ensureLoaded();eraseAsync();io(()->null);}
 private static final class Row {
  final String error,correction,language,device;final long a,r,t;
  Row(String e,String c,String l,String d,long a,long r,long t){error=e;correction=c;language=l;device=d;this.a=a;this.r=r;this.t=t;}
  String key(){return error+"\t"+correction+"\t"+language+"\t"+device;}
  JSONObject json()throws JSONException{return new JSONObject().put("error",error).put("correction",correction).put("language",language).put("device",device).put("a",a).put("r",r).put("t",t);}
 }
 private static final class Database extends SQLiteOpenHelper{
  Database(Context c){super(c,"personal_typing_v1.db",null,2);setWriteAheadLoggingEnabled(true);}
  public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE evidence(error TEXT NOT NULL,correction TEXT NOT NULL,language TEXT NOT NULL,device TEXT NOT NULL,accepts INTEGER NOT NULL,rejects INTEGER NOT NULL,frequency INTEGER NOT NULL,confidence REAL NOT NULL,updated INTEGER NOT NULL,PRIMARY KEY(error,correction,language,device))");createProfile(db);}
  private static void createProfile(SQLiteDatabase db){
   db.execSQL("CREATE TABLE IF NOT EXISTS style(feature TEXT PRIMARY KEY NOT NULL,count INTEGER NOT NULL)");
   db.execSQL("CREATE TABLE IF NOT EXISTS vocabulary(word TEXT NOT NULL,language TEXT NOT NULL,PRIMARY KEY(word,language))");
  }
  public void onUpgrade(SQLiteDatabase db,int old,int next){
   if(old==1&&next==2){createProfile(db);return;}
   throw new SQLiteException("Unsupported learning migration; original database retained");
  }
  public void onDowngrade(SQLiteDatabase db,int old,int next){throw new SQLiteException("Newer learning schema; original database retained");}
 }
}
