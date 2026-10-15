// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.entities;
import java.text.Normalizer;import java.util.*;import java.util.regex.*;
/** Resolve only supported, unambiguous date/time pairs. The caller must present editable review. */
public final class CalendarResolver {
 private CalendarResolver(){}
 private static String normalize(String text){return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).trim();}
 public static Long resolve(String date,String time,long now,TimeZone zone){
  if(zone==null)return null;String d=normalize(date),t=normalize(time);
  Matcher clock=Pattern.compile("^(\\d{1,2}):([0-5]\\d)\\s*(am|pm|a\\.m\\.|p\\.m\\.)?$").matcher(t);if(!clock.matches())return null;
  int hour=Integer.parseInt(clock.group(1)),minute=Integer.parseInt(clock.group(2));String suffix=clock.group(3);
  if(suffix!=null){if(hour<1||hour>12)return null;hour=hour%12+(suffix.startsWith("p")?12:0);}
  else if(hour>23||(hour>=1&&hour<=12&&!clock.group(1).startsWith("0")))return null;
  Calendar c=new GregorianCalendar(zone);c.setTimeInMillis(now);c.setLenient(false);
  Matcher iso=Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$").matcher(d);
  if(iso.matches()){
   int year=Integer.parseInt(iso.group(1));if(year<1970||year>2199)return null;
   c.clear();c.set(year,Integer.parseInt(iso.group(2))-1,Integer.parseInt(iso.group(3)));
  }else if(d.equals("manana")||d.equals("tomorrow"))c.add(Calendar.DAY_OF_MONTH,1);
  else if(d.equals("pasado manana")||d.equals("day after tomorrow"))c.add(Calendar.DAY_OF_MONTH,2);
  else {
   String word=d.replaceFirst("^(?:el )?(?:proximo |next )?","");
   List<String> es=Arrays.asList("domingo","lunes","martes","miercoles","jueves","viernes","sabado");
   List<String> en=Arrays.asList("sunday","monday","tuesday","wednesday","thursday","friday","saturday");
   int index=es.indexOf(word);if(index<0)index=en.indexOf(word);if(index<0)return null;
   int delta=(index+1-c.get(Calendar.DAY_OF_WEEK)+7)%7;c.add(Calendar.DAY_OF_MONTH,delta==0?7:delta);
  }
  c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
  try{return c.getTimeInMillis();}catch(IllegalArgumentException invalid){return null;}
 }
 public static Long durationMillis(String value){String normalized=normalize(value);Matcher matcher=Pattern.compile("^(?:durante|por|for) +(\\d{1,3}) *(minutos?|minutes?|horas?|hours?)$").matcher(normalized);
  if(!matcher.matches())return null;long amount=Long.parseLong(matcher.group(1));boolean hours=matcher.group(2).startsWith("hora")||matcher.group(2).startsWith("hour");
  if(amount<1||(hours&&amount>24)||(!hours&&amount>1440))return null;return amount*(hours?60L:1L)*60L*1000L;
 }
}
