// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.entities;
import java.util.*;
import java.util.regex.*;
/** Explicit text only. Bounded local recognition, no retained text and no network. */
public final class EntityRecognizer {
 public enum Kind { CLABE, BANK_ACCOUNT, PAYMENT_REFERENCE, TRACKING_NUMBER, PHONE, EMAIL, URL, ADDRESS, DATE, TIME, DURATION, RFC, POSTAL_CODE, AMOUNT, PERCENT, PERSON, BUSINESS }
 public static final class Entity {
  public final Kind kind; public final int start,end; public final String value;
  Entity(Kind k,int s,int e,String v){kind=k;start=s;end=e;value=v;}
 }
 private static final Pattern BANK=Pattern.compile("(?<![\\p{N}])(?:[0-9][ \\t-]?){17}[0-9](?![ \\t-]*[0-9])");
 private static final Pattern PHONE=Pattern.compile("(?<![\\p{N}+])(?:\\+[1-9](?:[ ()-]*[0-9]){7,14}|(?:[0-9][ ()-]*){9}[0-9])(?![ ()-]*[0-9])");
 private static final Pattern EMAIL=Pattern.compile("(?<![\\w.+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}\\b");
 private static final Pattern URL=Pattern.compile("https?://[^\\s<>\\\"']+",Pattern.CASE_INSENSITIVE);
 private static final Pattern ADDRESS=Pattern.compile("\\b(?:Insurgentes(?: Sur| Norte)?|(?:calle|avenida|av\\.?|boulevard|blvd\\.?) [\\p{L}][\\p{L} .'-]{1,60}?) +[0-9]{1,6}(?:[ -][A-Za-z])?\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
 private static final Pattern PAYMENT_REFERENCE=Pattern.compile("(?<![\\p{L}\\p{N}_])(?:referencia(?:\\s+de\\s+pago)?|ref\\.?\\s+de\\s+pago|payment\\s+reference)\\s*[:#]?\\s*([A-Z0-9][A-Z0-9-]{5,31})(?![\\p{L}\\p{N}-])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
 private static final Pattern TRACKING_NUMBER=Pattern.compile("(?<![\\p{L}\\p{N}_])(?:gu[ií]a|n[uú]mero\\s+de\\s+rastreo|tracking(?:\\s+number)?)\\s*[:#]?\\s*([A-Z0-9][A-Z0-9-]{5,31})(?![\\p{L}\\p{N}-])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
 private static final Pattern PERSON=Pattern.compile("(?<![\\p{L}\\p{N}_])(?i:contacto|persona|nombre)\\s*[:#]\\s*([\\p{Lu}][\\p{L}'’.-]{1,39}(?:\\s+[\\p{Lu}][\\p{L}'’.-]{1,39}){1,3})(?=$|[,;\\n])");
 private static final Pattern BUSINESS=Pattern.compile("(?<![\\p{L}\\p{N}_])(?i:empresa|negocio|business)\\s*[:#]\\s*([\\p{L}\\p{N}&.'’ -]{2,80}?)(?=$|[,;\\n])");
 public List<Entity> scan(String text){
  if(text==null||text.isEmpty()||text.length()>4096)return Collections.emptyList();
  List<Entity> result=new ArrayList<>(); List<int[]> protectedNumbers=new ArrayList<>();
  Matcher m=BANK.matcher(text);
  while(m.find()) { protectedNumbers.add(new int[]{m.start(),m.end()}); String n=m.group().replaceAll("[^0-9]",""); if(validClabe(n))result.add(new Entity(Kind.CLABE,m.start(),m.end(),n)); }
  m=Pattern.compile("\\b(?:cuenta|account)\\s*(?:no\\.?|n[uú]mero|number)?\\s*[:#]?\\s*((?:[0-9][ \\t-]?){5,16}[0-9])(?![ \\t-]*[0-9])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text);
  while(m.find()&&result.size()<24){protectedNumbers.add(new int[]{m.start(1),m.end(1)});result.add(new Entity(Kind.BANK_ACCOUNT,m.start(1),m.end(1),m.group(1).replaceAll("[^0-9]","")));}
  addLabeled(result,protectedNumbers,PAYMENT_REFERENCE,text,Kind.PAYMENT_REFERENCE);
  addLabeled(result,protectedNumbers,TRACKING_NUMBER,text,Kind.TRACKING_NUMBER);
  m=PHONE.matcher(text);
  while(m.find()&&result.size()<24){ boolean blocked=false;for(int[] b:protectedNumbers)if(m.start()<b[1]&&m.end()>b[0])blocked=true;
   if(!blocked)result.add(new Entity(Kind.PHONE,m.start(),m.end(),m.group().replaceAll("[^+0-9]",""))); }
  add(result,EMAIL,text,Kind.EMAIL);add(result,URL,text,Kind.URL);add(result,ADDRESS,text,Kind.ADDRESS);
  add(result,Pattern.compile("\\b(?:pasado mañana|mañana|tomorrow|next (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|[0-9]{4}-[0-9]{2}-[0-9]{2}|(?:el )?(?:próximo )?(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo))\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),text,Kind.DATE);
  add(result,Pattern.compile("\\b(?:[01]?[0-9]|2[0-3]):[0-5][0-9](?: ?(?:am|pm|a\\.m\\.|p\\.m\\.))?",Pattern.CASE_INSENSITIVE),text,Kind.TIME);
  add(result,Pattern.compile("\\b(?:durante|por|for) +[0-9]{1,3} *(?:minutos?|minutes?|horas?|hours?)\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),text,Kind.DURATION);
  add(result,Pattern.compile("\\b[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}\\b"),text,Kind.RFC);
  add(result,Pattern.compile("\\b(?:CP|C\\.P\\.) *([0-9]{5})\\b",Pattern.CASE_INSENSITIVE),text,Kind.POSTAL_CODE);
  add(result,Pattern.compile("(?:\\$|MXN|USD) *[0-9]+(?:[.,][0-9]{1,3})*"),text,Kind.AMOUNT);
  add(result,Pattern.compile("\\b[0-9]+(?:[.,][0-9]+)? *%"),text,Kind.PERCENT);
  addLabeledText(result,PERSON,text,Kind.PERSON);addLabeledText(result,BUSINESS,text,Kind.BUSINESS);
  Collections.sort(result,(a,b)->Integer.compare(a.start,b.start));
  return Collections.unmodifiableList(result);
 }
 private void addLabeledText(List<Entity> out,Pattern pattern,String text,Kind kind){
  Matcher m=pattern.matcher(text);while(m.find()&&out.size()<24)out.add(new Entity(kind,m.start(1),m.end(1),m.group(1)));
 }
 private void addLabeled(List<Entity> out,List<int[]> protectedNumbers,Pattern pattern,String text,Kind kind){
  Matcher m=pattern.matcher(text);while(m.find()&&out.size()<24){String value=m.group(1);boolean hasDigit=false;
   for(int i=0;i<value.length();i++)if(Character.isDigit(value.charAt(i))){hasDigit=true;break;}
   if(!hasDigit)continue;protectedNumbers.add(new int[]{m.start(1),m.end(1)});out.add(new Entity(kind,m.start(1),m.end(1),value));}
 }
 private void add(List<Entity> out,Pattern pattern,String text,Kind kind){
  Matcher m=pattern.matcher(text);while(m.find()&&out.size()<24){String v=m.group();int end=m.end();
   if(kind==Kind.URL){while(v.endsWith(".")||v.endsWith(",")||v.endsWith(")")){v=v.substring(0,v.length()-1);end--;}}
   out.add(new Entity(kind,m.start(),end,v));}
 }
 public static boolean validClabe(String value){if(value==null||!value.matches("[0-9]{18}"))return false;int[] w={3,7,1};int sum=0;
  for(int i=0;i<17;i++)sum+=((value.charAt(i)-'0')*w[i%3])%10;return (10-sum%10)%10==value.charAt(17)-'0';}
}
