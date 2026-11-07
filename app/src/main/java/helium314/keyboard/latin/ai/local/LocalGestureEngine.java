// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.DictionaryFacilitator;
import helium314.keyboard.latin.SuggestedWords;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.dictionary.Dictionary;
import helium314.keyboard.latin.utils.JniUtils;
import java.util.*;
import java.util.concurrent.*;

/** Worker-only preparation/decoding for the original Java glide engine. Native engine wins. */
public final class LocalGestureEngine {
    private static final ExecutorService PREPARER=Executors.newSingleThreadExecutor();
    private static volatile Cache cache;
    private static Object pending;
    private static final Object LOAD_LOCK=new Object();
    private static final class Cache {
        final Object dictionary;
        final List<LocalGestureDecoder.Word> words;
        Keyboard keyboard;
        LocalGestureDecoder decoder;
        Cache(Object dictionary,List<LocalGestureDecoder.Word> words){this.dictionary=dictionary;this.words=words;}
    }
    private LocalGestureEngine(){}
    /** Actual Java engine capability; dictionary availability remains a separate native IME gate. */
    public static boolean isSupported(){return true;}
    public static boolean isReady(DictionaryFacilitator facilitator){
        Cache current=cache;
        return current!=null&&!current.words.isEmpty()&&current.dictionary==facilitator.getGestureDictionaryIdentity();
    }
    public static void prepare(DictionaryFacilitator facilitator){
        if(JniUtils.sHaveGestureLib||!facilitator.hasAtLeastOneInitializedMainDictionary())return;
        Object identity=facilitator.getGestureDictionaryIdentity();
        synchronized(LOAD_LOCK){
            if((cache!=null&&cache.dictionary==identity)||pending==identity)return;
            pending=identity;
        }
        PREPARER.execute(()->{
            try{load(facilitator,identity);}catch(RuntimeException|LinkageError unavailable){/* Readiness stays false; no downloads or fabricated words. */}
            finally{synchronized(LOAD_LOCK){if(pending==identity)pending=null;}}
        });
    }
    private static Cache load(DictionaryFacilitator facilitator,Object identity){
        // Serializes dictionary reads/template publication, never used by typing/UI code.
        synchronized(LocalGestureEngine.class){
            Cache current=cache;if(current!=null&&current.dictionary==identity)return current;
            if(facilitator.getGestureDictionaryIdentity()!=identity)return null;
            List<LocalGestureDecoder.Word> words=facilitator.getGestureWords(LocalGestureDecoder.MAX_WORDS);
            if(facilitator.getGestureDictionaryIdentity()!=identity)return null;
            Cache loaded=new Cache(identity,Collections.unmodifiableList(new ArrayList<>(words)));
            cache=loaded;return loaded;
        }
    }
    /** Called exclusively by InputLogicHandler's worker. No IO/network or editor writes. */
    public static SuggestedWords suggest(DictionaryFacilitator facilitator,Keyboard keyboard,
            int[] x,int[] y,int size,int sequenceNumber,boolean tail,boolean allCaps,boolean firstCapital){
        int style=tail?SuggestedWords.INPUT_STYLE_TAIL_BATCH:SuggestedWords.INPUT_STYLE_UPDATE_BATCH;
        ArrayList<SuggestedWordInfo> candidates=new ArrayList<>();
        if(keyboard!=null&&!JniUtils.sHaveGestureLib){
            Object identity=facilitator.getGestureDictionaryIdentity();
            Cache current=load(facilitator,identity);
            if(current!=null&&!current.words.isEmpty()){
                LocalGestureDecoder decoder;
                synchronized(current){
                    if(current.keyboard!=keyboard||current.decoder==null){
                        List<LocalGestureDecoder.KeyPoint> keys=new ArrayList<>();
                        for(Key key:keyboard.getSortedKeys())if(Character.isLetter(key.getCode()))keys.add(new LocalGestureDecoder.KeyPoint(
                                key.getCode(),key.getX()+key.getWidth()/2f,key.getY()+key.getHeight()/2f,key.getWidth()));
                        current.decoder=new LocalGestureDecoder(keys,current.words);current.keyboard=keyboard;
                    }
                    decoder=current.decoder;
                }
                Locale locale=facilitator.getCurrentLocale();
                for(LocalGestureDecoder.Match match:decoder.decode(x,y,size,8)){
                    String word=match.word.toLowerCase(locale);
                    if(allCaps)word=word.toUpperCase(locale);
                    else if(firstCapital&&!word.isEmpty()){
                        int first=word.offsetByCodePoints(0,1);word=word.substring(0,first).toUpperCase(locale)+word.substring(first);
                    }
                    candidates.add(new SuggestedWordInfo(word,"",Math.max(1,(int)(100000-match.cost*10000)),
                            SuggestedWordInfo.KIND_CORRECTION,Dictionary.DICTIONARY_HARDCODED,
                            SuggestedWordInfo.NOT_AN_INDEX,SuggestedWordInfo.NOT_A_CONFIDENCE));
                }
                if(facilitator.getGestureDictionaryIdentity()!=identity)candidates.clear();
            }
        }
        return new SuggestedWords(candidates,candidates,null,false,false,false,style,sequenceNumber);
    }
}
