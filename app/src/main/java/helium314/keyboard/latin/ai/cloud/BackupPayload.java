// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.cloud;
import android.content.*;import android.database.Cursor;import android.provider.UserDictionary.Words;
import helium314.keyboard.latin.ai.local.PersonalTypingStore;import helium314.keyboard.latin.ai.SensitiveInputPolicy;
import org.json.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Explicit schema, never a SharedPreferences/database dump. Only single-word personal entries. */
public final class BackupPayload {
 private static final Set<String> VISUAL=new HashSet<>(Arrays.asList(
  "theme_style","theme_colors","theme_colors_night","theme_key_borders","theme_auto_day_night",
  "font_scale","hint_font_scale","emoji_font_scale","popup_on","show_popup_hints"));
 private static boolean allowedKey(String key){return VISUAL.contains(key)||key.matches("keyboard_height_scale_(true|false)_(true|false)");}
 private static boolean validValue(String key,Object value){
  if(key.equals("theme_style"))return value instanceof String&&Arrays.asList("Material","Holo","Rounded").contains(value);
  if(key.equals("theme_colors")||key.equals("theme_colors_night"))return value instanceof String&&Arrays.asList(
    "light","holo_white","dark","darker","black","dynamic","dynamic_neutral","dynamic_monochrome",
    "dynamic_tonal_spot","dynamic_vibrant","dynamic_rainbow","dynamic_expressive","dynamic_fidelity",
    "dynamic_content","dynamic_fruit_salad","dynamic_cmf","blue_gray","brown","chocolate","cloudy",
    "forest","indigo","ocean","pink","sand","violette").contains(value);
  if(key.endsWith("scale")||key.startsWith("keyboard_height_scale"))
   return value instanceof Number&&Double.isFinite(((Number)value).doubleValue())
    &&((Number)value).doubleValue()>=.2&&((Number)value).doubleValue()<=3;
  return value instanceof Boolean;
 }
 private static android.content.SharedPreferences visual(Context c){return helium314.keyboard.latin.utils.DeviceProtectedUtils.getSharedPreferences(c);}
 private static boolean safeRejection(String value){
  return value!=null&&value.length()<=100&&value.matches("[\\p{Ll}\\p{M}]{2,48}→[\\p{Ll}\\p{M}]+(?: [\\p{Ll}\\p{M}]+)?")
   &&!SensitiveInputPolicy.sensitiveText(value);
 }
 private static boolean safeWord(String word){return word!=null&&word.length()>=2&&word.length()<=48
  &&word.matches("[\\p{L}\\p{M}]+")&&!SensitiveInputPolicy.sensitiveText(word);}
 private static boolean language(String value){return Arrays.asList("","es","es_MX","en","en_US","es-MX","en-US").contains(value);}
 public static byte[] export(Context c,PersonalTypingStore learning)throws Exception{
  JSONObject preferences=new JSONObject();
  for(Map.Entry<String,?> e:visual(c).getAll().entrySet())if(allowedKey(e.getKey())){
   Object value=e.getValue();if(validValue(e.getKey(),value))preferences.put(e.getKey(),value);
  }
  JSONArray words=new JSONArray();boolean available=false;
  TreeMap<String,JSONObject> unique=new TreeMap<>();
  try(Cursor cursor=c.getContentResolver().query(Words.CONTENT_URI,
    new String[]{Words.WORD,Words.LOCALE,Words.FREQUENCY,Words.SHORTCUT},null,null,null)){
   if(cursor!=null){available=true;while(cursor.moveToNext()){
    String word=cursor.getString(0),locale=cursor.isNull(1)?"":cursor.getString(1),shortcut=cursor.getString(3);
    if(!safeWord(word)||!language(locale)||shortcut!=null&&!shortcut.isEmpty())continue;
    if(unique.size()>=1024&&!unique.containsKey(word+"\t"+locale))throw new IllegalStateException("Personal dictionary backup limit reached");
    unique.put(word+"\t"+locale,new JSONObject().put("word",word).put("locale",locale).put("frequency",Math.max(0,Math.min(255,cursor.getInt(2)))));
   }}
  }catch(SecurityException|IllegalArgumentException unavailable){available=false;}
  for(JSONObject entry:unique.values())words.put(entry);
  JSONArray rejections=new JSONArray();
  for(String rejection:helium314.keyboard.latin.ai.AiSettingsActivity.rejectedCorrections(c))
   if(safeRejection(rejection))rejections.put(rejection);
  JSONObject result=new JSONObject().put("schema",2).put("rejections",rejections).put("learning",new JSONObject(new String(learning.exportData(),StandardCharsets.UTF_8)))
   .put("dictionary",words).put("dictionaryAvailable",available).put("visual",preferences).put("updated",System.currentTimeMillis());
  return result.toString().getBytes(StandardCharsets.UTF_8);
 }
 private static JSONObject decode(byte[] bytes)throws Exception{
  if(bytes==null||bytes.length>2_000_000)throw new IllegalArgumentException("Backup payload too large");
  JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));int schema=root.getInt("schema");
  if(schema==1)return root; // Original learning-only snapshots remain readable.
  if(schema!=2)throw new IllegalArgumentException("Unsupported cloud schema");
  JSONArray rejections=root.optJSONArray("rejections");
  if(rejections!=null){
   if(rejections.length()>128)throw new IllegalArgumentException("Rejection limit");
   for(int i=0;i<rejections.length();i++)if(!safeRejection(rejections.getString(i)))throw new IllegalArgumentException("Unsafe rejection");
  }
  JSONArray dictionary=root.getJSONArray("dictionary");if(dictionary.length()>1024)throw new IllegalArgumentException("Dictionary limit");
  for(int i=0;i<dictionary.length();i++){JSONObject word=dictionary.getJSONObject(i);
   if(!safeWord(word.getString("word"))||!language(word.getString("locale"))||word.getInt("frequency")<0||word.getInt("frequency")>255)
    throw new IllegalArgumentException("Unsafe dictionary entry");}
  JSONObject preferences=root.getJSONObject("visual");
  Iterator<String> keys=preferences.keys();while(keys.hasNext()){
   String key=keys.next();Object value=preferences.get(key);
   if(!allowedKey(key)||!validValue(key,value))
    throw new IllegalArgumentException("Unsupported visual preference");
  }
  if(root.getLong("updated")<0)throw new IllegalArgumentException("Invalid preference timestamp");
  return root;
 }
 private static byte[] learning(JSONObject root)throws Exception{return (root.getInt("schema")==1?root:root.getJSONObject("learning")).toString().getBytes(StandardCharsets.UTF_8);}
 public static void validate(byte[] bytes,PersonalTypingStore store)throws Exception{JSONObject root=decode(bytes);store.validate(learning(root));}
 public static void merge(Context c,PersonalTypingStore store,byte[] bytes,long generation)throws Exception{
  JSONObject root=decode(bytes);store.merge(learning(root),generation);if(root.getInt("schema")==1)return;
  JSONArray rejections=root.optJSONArray("rejections");
  if(rejections!=null)for(int i=0;i<rejections.length();i++){
   if(store.generation()!=generation||!TypingSync.prefs(c).getBoolean("enabled",false))return;
   helium314.keyboard.latin.ai.AiSettingsActivity.rejectCorrection(c,rejections.getString(i));
  }
  JSONArray dictionary=root.getJSONArray("dictionary");
  for(int i=0;i<dictionary.length();i++){
   if(store.generation()!=generation||!TypingSync.prefs(c).getBoolean("enabled",false))return;
   JSONObject word=dictionary.getJSONObject(i);String value=word.getString("word"),locale=word.getString("locale");
   String query=Words.WORD+"=? AND "+(locale.isEmpty()?"("+Words.LOCALE+" IS NULL OR "+Words.LOCALE+"='')":Words.LOCALE+"=?");
   String[] args=locale.isEmpty()?new String[]{value}:new String[]{value,locale};
   boolean exists=false;
   try(Cursor cursor=c.getContentResolver().query(Words.CONTENT_URI,new String[]{Words.WORD},query,args,null)){exists=cursor!=null&&cursor.moveToFirst();}
   if(!exists)Words.addWord(c,value,word.getInt("frequency"),null,locale.isEmpty()?null:Locale.forLanguageTag(locale.replace('_','-')));
  }
  // Restore compatible visuals only on the explicit initial restore, newest remote snapshot wins.
  if(TypingSync.prefs(c).getBoolean("restore_visual",false)&&root.getLong("updated")>TypingSync.prefs(c).getLong("visual_updated",0)
      &&store.generation()==generation&&TypingSync.prefs(c).getBoolean("enabled",false)){
   SharedPreferences.Editor edit=visual(c).edit();JSONObject preferences=root.getJSONObject("visual");Iterator<String> keys=preferences.keys();
   while(keys.hasNext()){String key=keys.next();Object value=preferences.get(key);
    if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
    else if(value instanceof Number)edit.putFloat(key,((Number)value).floatValue());
    else edit.putString(key,(String)value);
   }edit.commit();TypingSync.prefs(c).edit().putLong("visual_updated",root.getLong("updated")).commit();
  }
 }
}
