// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import java.io.*;
import java.util.*;

/** Immutable public dictionary evidence. Read only on a worker, never editor text or user data. */
public final class SpanishAccentIndex {
    private final Map<String,String> words;
    private SpanishAccentIndex(Map<String,String> words){this.words=Collections.unmodifiableMap(words);}
    public static SpanishAccentIndex empty(){return new SpanishAccentIndex(Collections.emptyMap());}
    public String get(String word){return words.get(word);}
    /** Preserve both ordinary words and titlecase personal names before overriding validity. */
    public String get(String word,java.util.function.Predicate<String> personal) {
        String candidate=words.get(word);
        if(candidate==null)return null;
        String title=Character.toUpperCase(word.charAt(0))+word.substring(1);
        return personal.test(word)||personal.test(title)?null:candidate;
    }
    public int size(){return words.size();}
    public static SpanishAccentIndex read(Reader source) throws IOException {
        Map<String,String> words=new HashMap<>();
        BufferedReader reader=new BufferedReader(source);
        int chars=0;
        for(String line;(line=reader.readLine())!=null;){
            chars+=line.length()+1;
            if(chars>512000||words.size()>=12000)throw new IOException("Accent index exceeds bounds");
            String[] fields=line.split("\t",-1);
            if(fields.length!=2||!fields[0].matches("[a-zñü]{4,32}")
                ||!fields[1].matches("[a-zñüáéíóú]{4,32}"))throw new IOException("Invalid accent entry");
            StringBuilder bare=new StringBuilder();int accents=0;
            for(char c:fields[1].toCharArray()){
                int at="áéíóú".indexOf(c);
                if(at>=0){bare.append("aeiou".charAt(at));accents++;}else bare.append(c);
            }
            if(accents!=1||!fields[0].contentEquals(bare)||words.put(fields[0],fields[1])!=null)
                throw new IOException("Ambiguous accent entry");
        }
        return new SpanishAccentIndex(words);
    }
}
