// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import helium314.keyboard.latin.ai.local.LocalContextReviewer;
import helium314.keyboard.latin.ai.local.SemanticEditGuard;
import java.util.*;
import java.util.regex.*;

/** Bounded spell/grammar edits; model output is data, never permission to rewrite. */
public final class AutomaticCorrectionPolicy {
    private static final Pattern PROTECTED=Pattern.compile("[\\p{L}\\p{M}]*[\\p{Lu}\\p{N}_][\\p{L}\\p{M}\\p{N}_]*|\\b(?:no|ni|sin|nunca|jamás|not|never|don't|can't|won't)\\b");
    private static final Set<String> CONFUSIONS=new HashSet<>(Arrays.asList(
        "haber→a ver","a ver→haber","hay→ahí","ahí→hay","ay→hay","ay→ahí",
        "porque→por qué","por qué→porque","por que→porque","sino→si no","si no→sino",
        "si→sí","sí→si","tu→tú","tú→tu","el→él","él→el","mas→más","más→mas","aun→aún","aún→aun"));
    private AutomaticCorrectionPolicy() {}
    private static List<String> protectedWords(String text){
        List<String> out=new ArrayList<>();Matcher m=PROTECTED.matcher(text);while(m.find())out.add(m.group());return out;
    }
    /** Keep only context with a known left boundary when the editor prefix was truncated. */
    public static String context(String before,int cursor){
        if(before==null||before.isEmpty()||before.length()>512)return "";
        if(cursor<=before.length())return before;
        for(int i=0;i<before.length()-1;i++){
            if(".!?\n".indexOf(before.charAt(i))>=0&&Character.isWhitespace(before.charAt(i+1))){
                int start=i+1;while(start<before.length()&&Character.isWhitespace(before.charAt(start)))start++;
                if(before.substring(start).trim().length()>=3)return before.substring(start);
            }
        }
        return "";
    }
    private static String unaccented(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","");}
    private static String lexicalProjection(String value){return unaccented(value).replaceAll("[^\\p{L}\\p{N}]+","");}
    private static List<String> preservedWords(String text,LocalContextReviewer.Lexicon lexicon){
        List<String> out=new ArrayList<>();Matcher words=Pattern.compile("[\\p{L}\\p{M}]+").matcher(text);
        while(words.find()){String word=words.group().toLowerCase(Locale.ROOT);if(lexicon.preserved(word))out.add(word);}return out;
    }
    public static LocalContextReviewer.Correction choose(String source,String generated,
            LocalContextReviewer.Lexicon lexicon,Set<String> rejected){
        if(source==null||generated==null||source.isEmpty()||source.length()>512||generated.length()>640
            ||source.equals(generated)||source.matches("(?s).*[@/\\\\`_=<>].*")
            ||!protectedWords(source).equals(protectedWords(generated))
            ||!preservedWords(source,lexicon).equals(preservedWords(generated,lexicon)))return null;
        // Derive punctuation spans from the actual generated string. The explicit-review
        // guard anchors insertions differently; reusing that replacement could move a comma
        // after the original space ("word ,next") instead of before it ("word, next").
        if(source.replaceAll("[\\p{P}\\s]+","").equals(generated.replaceAll("[\\p{P}\\s]+",""))){
            int left=0,right=source.length(),targetRight=generated.length();
            while(left<right&&left<targetRight&&source.charAt(left)==generated.charAt(left))left++;
            while(right>left&&targetRight>left&&source.charAt(right-1)==generated.charAt(targetRight-1)){right--;targetRight--;}
            if(left==right&&left>0){while(left>0&&Character.isLetter(source.charAt(left-1)))left--;}
            if(right>left&&right-left<=48&&targetRight>left&&targetRight-left<=64){
                String from=source.substring(left,right),to=generated.substring(left,targetRight);
                if(!from.trim().matches("(?i)(no|ni|sin|nunca|jamás|not|never|don't|can't|won't)")
                    &&!lexicon.rejected(from,to)&&!vetoed(from,to,rejected))
                    return new LocalContextReviewer.Correction(left,right,from,to,"gemini-automatic-punctuation",0,true);
            }
            return null;
        }
        List<LocalContextReviewer.Correction> changes=SemanticEditGuard.proposals(source,generated,lexicon,rejected,()->false);
        for(LocalContextReviewer.Correction c:changes){
            Matcher words=Pattern.compile("[\\p{L}\\p{M}]+").matcher(c.original);
            boolean invalid=false;while(words.find())invalid|=lexicon.knownInvalid(words.group().toLowerCase(Locale.ROOT));
            // Explicit-choice function-word alternatives are not calibrated automatic edits.
            // Existing valid words can change only by accents or an enumerated confusion.
            if(c.original.trim().matches("(?i)(no|ni|sin|nunca|jamás|not|never|don't|can't|won't)"))continue;
            if(!invalid&&!lexicalProjection(c.original).equals(lexicalProjection(c.replacement))&&!CONFUSIONS.contains(c.original+"→"+c.replacement))continue;
            return new LocalContextReviewer.Correction(c.start,c.end,c.original,c.replacement,"gemini-automatic-minimal-edit",0,true);
        }
        int start=0,end=source.length(),targetEnd=generated.length();
        while(start<end&&start<targetEnd&&source.charAt(start)==generated.charAt(start))start++;
        while(end>start&&targetEnd>start&&source.charAt(end-1)==generated.charAt(targetEnd-1)){end--;targetEnd--;}
        while(start>0&&(letter(source,start-1)||letter(generated,start-1)))start--;
        while(end<source.length()&&letter(source,end))end++;
        while(targetEnd<generated.length()&&letter(generated,targetEnd))targetEnd++;
        if(end<=start||targetEnd<=start||end-start>48||targetEnd-start>64)return null;
        String from=source.substring(start,end),to=generated.substring(start,targetEnd);
        if(!source.substring(0,start).equals(generated.substring(0,start))||!source.substring(end).equals(generated.substring(targetEnd)))return null;
        if(!CONFUSIONS.contains(from+"→"+to))return null;
        if(lexicon.rejected(from,to))return null;
        if(rejected!=null)for(String veto:rejected)if(veto.equalsIgnoreCase(from)||veto.equalsIgnoreCase(to)||veto.equalsIgnoreCase(from+"→"+to))return null;
        return new LocalContextReviewer.Correction(start,end,from,to,"gemini-automatic-context-confusion",0,true);
    }
    private static boolean vetoed(String from,String to,Set<String> rejected){
        if(rejected!=null)for(String value:rejected)if(value.equalsIgnoreCase(from)||value.equalsIgnoreCase(to)||value.equalsIgnoreCase(from+"→"+to))return true;
        return false;
    }
    private static boolean letter(String text,int index){return index>=0&&index<text.length()&&Character.isLetter(text.charAt(index));}
}
