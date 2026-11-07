// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative post-check, not a claim that a model understands intent perfectly. */
public final class VoiceEditPolicy {
    private VoiceEditPolicy() {}
    private static final Pattern FACT=Pattern.compile("\\d+(?:[.,:]\\d+)*|\\b(?:cero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|zero|one|two|three|four|five|six|seven|eight|nine|ten|lunes|martes|miercoles|jueves|viernes|sabado|domingo|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b");
    private static final String[] ES={"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez"};
    private static final String[] EN={"zero","one","two","three","four","five","six","seven","eight","nine","ten"};
    private static String normalized(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
    private static List<String> facts(String s){List<String> out=new ArrayList<>();Matcher m=FACT.matcher(normalized(s));while(m.find()){String value=m.group();for(int i=0;i<ES.length;i++)if(value.equals(ES[i])||value.equals(EN[i])){value=Integer.toString(i);break;}out.add(value);}return out;}
    private static Set<String> names(String s){Set<String> out=new HashSet<>();Matcher m=Pattern.compile("\\b\\p{Lu}[\\p{L}]{2,}\\b").matcher(s);while(m.find()){String value=normalized(m.group());if(m.start()>0&&!Arrays.asList("hola","este","see","the","nos","llego","son","no").contains(value))out.add(value);}return out;}
    private static Set<String> negatives(String s){Set<String> out=new HashSet<>();Matcher m=Pattern.compile("\\b(?:no|not|never|nunca|jamas|sin|without)\\b").matcher(normalized(s).replaceAll("\\bno\\s*,", ""));while(m.find())out.add(m.group());return out;}
    private static boolean valid(String s){if(s==null||s.trim().isEmpty()||s.length()>4096)return false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))return false;}else if(Character.isLowSurrogate(c)||Character.isISOControl(c)&&c!='\n'&&c!='\t')return false;}return true;}
    public static String dictation(String transcript,String cleaned){
        if(!valid(transcript))return null;
        if(!valid(cleaned)||cleaned.length()>transcript.length()*2+80)return transcript;
        if(!negatives(transcript).equals(negatives(cleaned)))return transcript;
        Set<String> originalNames=names(transcript),newNames=names(cleaned);
        Set<String> originalWords=new HashSet<>(Arrays.asList(normalized(transcript).split("[^\\p{L}]+"))),newWords=new HashSet<>(Arrays.asList(normalized(cleaned).split("[^\\p{L}]+")));
        if(!originalWords.containsAll(newNames)||!newWords.containsAll(originalNames))return transcript;
        List<String> source=facts(transcript),result=facts(cleaned);
        if(!source.equals(result)){
            boolean repair=Pattern.compile("\\bno\\s*,|\\b(?:mejor|quise decir|perdon|rather|sorry|i mean)\\b").matcher(normalized(transcript)).find();
            if(!repair||result.isEmpty()||result.size()>source.size()||!source.subList(source.size()-result.size(),source.size()).equals(result))return transcript;
        }
        return cleaned;
    }
    /** Only call in the user's explicit edit-by-voice mode, never for dictated content. */
    public static boolean acceptEdit(String original,String command,String result){
        if(!valid(original)||!valid(command)||!valid(result)||result.length()>original.length()*3+256)return false;
        List<String> before=facts(original),after=facts(result),spoken=facts(command);
        Set<String> permitted=new HashSet<>(before);permitted.addAll(spoken);
        if(!permitted.containsAll(after))return false;
        boolean replacement=Pattern.compile("\\b(?:cambia|sustituye|reemplaza|corrige|borra|elimina|change|replace|delete|remove)\\b").matcher(normalized(command)).find();
        for(String fact:before)if(!after.contains(fact)&&(!replacement||!spoken.contains(fact)))return false;
        Set<String> allowedNames=new HashSet<>(Arrays.asList(normalized(original+" "+command).split("[^\\p{L}]+")));
        if(!allowedNames.containsAll(names(result)))return false;
        Set<String> resultWords=new HashSet<>(Arrays.asList(normalized(result).split("[^\\p{L}]+"))),commandWords=new HashSet<>(Arrays.asList(normalized(command).split("[^\\p{L}]+")));
        for(String name:names(original))if(!resultWords.contains(name)&&(!replacement||!commandWords.contains(name)))return false;
        if(negatives(original).equals(negatives(result)))return true;
        Set<String> changed=new HashSet<>(negatives(original));changed.addAll(negatives(result));
        return replacement&&negatives(command).containsAll(changed);
    }
}
