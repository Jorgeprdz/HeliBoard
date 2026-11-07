// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import java.text.Normalizer;
import java.util.*;

/** Original geometric gesture decoder. Dictionary words and keyboard geometry only; no network. */
public final class LocalGestureDecoder {
    public static final int MAX_WORDS=15000;
    private static final int SAMPLES=24, SHORTLIST=48;
    public static final class KeyPoint {
        public final int code;
        public final float x,y,width;
        public KeyPoint(int code,float x,float y,float width){this.code=code;this.x=x;this.y=y;this.width=width;}
    }
    public static final class Word {
        public final String text;
        public final int frequency;
        public Word(String text,int frequency){this.text=text;this.frequency=frequency;}
    }
    public static final class Match {
        public final String word;
        public final double cost;
        Match(String word,double cost){this.word=word;this.cost=cost;}
    }
    private static final class Template {
        final Word word;
        final float[] x,y;
        final double length;
        Template(Word word,float[] x,float[] y,double length){this.word=word;this.x=x;this.y=y;this.length=length;}
    }
    private static final class Ranked {
        final Template template;
        final double cost;
        Ranked(Template template,double cost){this.template=template;this.cost=cost;}
    }
    private final List<Template> templates=new ArrayList<>();
    private final double scale;
    public LocalGestureDecoder(List<KeyPoint> keyboard,List<Word> vocabulary){
        Map<Integer,KeyPoint> keys=new HashMap<>();double widths=0;int count=0;
        for(KeyPoint key:keyboard)if(Character.isLetter(key.code)&&key.width>0){keys.put(Character.toLowerCase(key.code),key);widths+=key.width;count++;}
        scale=count==0?1:widths/count;
        Set<String> seen=new HashSet<>();
        List<Word> ordered=new ArrayList<>(vocabulary);ordered.sort(Comparator.comparingInt((Word word)->word.frequency).reversed());
        for(Word word:ordered){
            if(templates.size()>=MAX_WORDS)break;
            if(word.text==null||!word.text.matches("[\\p{L}\\p{M}]{2,32}")||!seen.add(word.text.toLowerCase(Locale.ROOT)))continue;
            String normalized=Normalizer.normalize(word.text,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
            List<KeyPoint> points=new ArrayList<>();boolean valid=true;
            for(int offset=0;offset<normalized.length();){
                int code=normalized.codePointAt(offset);offset+=Character.charCount(code);
                KeyPoint key=keys.get(code);
                if(key==null){String base=Normalizer.normalize(new String(Character.toChars(code)),Normalizer.Form.NFD).replaceAll("\\p{M}","");if(base.codePointCount(0,base.length())==1)key=keys.get(base.codePointAt(0));}
                if(key==null){valid=false;break;}
                if(points.isEmpty()||points.get(points.size()-1)!=key)points.add(key);
            }
            if(!valid||points.size()<2)continue;
            float[] x=new float[points.size()],y=new float[points.size()];
            for(int i=0;i<x.length;i++){x[i]=points.get(i).x;y[i]=points.get(i).y;}
            double length=length(x,y);if(length<scale*.4)continue;
            float[][] sampled=resample(x,y,length);templates.add(new Template(word,sampled[0],sampled[1],length));
        }
    }
    public boolean isReady(){return !templates.isEmpty();}
    /** Call on a worker with copied trace arrays. Work and returned alternatives are bounded. */
    public List<Match> decode(int[] x,int[] y,int size,int limit){
        if(size<2||size>8192||x==null||y==null||size>x.length||size>y.length||limit<=0)return Collections.emptyList();
        // Bound raw sampling independently of motion-event frequency.
        int inputSize=Math.min(size,512);float[] px=new float[inputSize],py=new float[inputSize];
        for(int i=0;i<inputSize;i++){int index=(int)((long)i*(size-1)/(inputSize-1));px[i]=x[index];py[i]=y[index];}
        double traceLength=length(px,py);if(traceLength<scale*.55)return Collections.emptyList();
        float[][] trace=resample(px,py,traceLength);
        PriorityQueue<Ranked> shortlist=new PriorityQueue<>(Comparator.comparingDouble((Ranked ranked)->ranked.cost).reversed());
        for(Template template:templates){
            double start=distance(trace[0][0],trace[1][0],template.x[0],template.y[0])/scale;
            double end=distance(trace[0][SAMPLES-1],trace[1][SAMPLES-1],template.x[SAMPLES-1],template.y[SAMPLES-1])/scale;
            double ratio=traceLength/template.length;
            if(start>1.65||end>1.65||ratio<.35||ratio>2.8)continue;
            double coarse=0;
            for(int i=0;i<SAMPLES;i+=3)coarse+=distance(trace[0][i],trace[1][i],template.x[i],template.y[i])/scale;
            coarse=coarse/8+(start+end)*.35;
            Ranked ranked=new Ranked(template,coarse);
            if(shortlist.size()<SHORTLIST)shortlist.add(ranked);
            else if(coarse<shortlist.peek().cost){shortlist.poll();shortlist.add(ranked);}
        }
        List<Match> result=new ArrayList<>();
        for(Ranked ranked:shortlist){
            Template template=ranked.template;
            double cost=dtw(trace[0],trace[1],template.x,template.y)/scale;
            cost+=(distance(trace[0][0],trace[1][0],template.x[0],template.y[0])
                    +distance(trace[0][SAMPLES-1],trace[1][SAMPLES-1],template.x[SAMPLES-1],template.y[SAMPLES-1]))/scale*.20;
            // Geometry dominates; frequency only resolves similar shapes/repeated-letter ambiguity.
            cost-=Math.log1p(Math.max(0,Math.min(255,template.word.frequency)))*.025;
            if(cost<=1.25)result.add(new Match(template.word.text,cost));
        }
        result.sort(Comparator.comparingDouble((Match match)->match.cost).thenComparing(match->match.word));
        if(result.size()>1&&result.get(0).cost>.65&&result.get(1).cost-result.get(0).cost<.08)return Collections.emptyList();
        if(result.size()>Math.min(limit,8))result=new ArrayList<>(result.subList(0,Math.min(limit,8)));
        return Collections.unmodifiableList(result);
    }
    private static double distance(float x,float y,float xx,float yy){return Math.hypot(x-xx,y-yy);}
    private static double length(float[] x,float[] y){double length=0;for(int i=1;i<x.length;i++)length+=distance(x[i-1],y[i-1],x[i],y[i]);return length;}
    private static float[][] resample(float[] x,float[] y,double length){
        float[][] result=new float[2][SAMPLES];int segment=1;double before=0;
        for(int i=0;i<SAMPLES;i++){
            double target=length*i/(SAMPLES-1);
            while(segment<x.length-1&&before+distance(x[segment-1],y[segment-1],x[segment],y[segment])<target){before+=distance(x[segment-1],y[segment-1],x[segment],y[segment]);segment++;}
            double span=distance(x[segment-1],y[segment-1],x[segment],y[segment]);
            double t=span==0?0:Math.max(0,Math.min(1,(target-before)/span));
            result[0][i]=(float)(x[segment-1]+(x[segment]-x[segment-1])*t);result[1][i]=(float)(y[segment-1]+(y[segment]-y[segment-1])*t);
        }
        return result;
    }
    private static double dtw(float[] x,float[] y,float[] tx,float[] ty){
        double[] previous=new double[SAMPLES+1],current=new double[SAMPLES+1];Arrays.fill(previous,Double.POSITIVE_INFINITY);previous[0]=0;
        for(int i=1;i<=SAMPLES;i++){
            Arrays.fill(current,Double.POSITIVE_INFINITY);
            for(int j=Math.max(1,i-6);j<=Math.min(SAMPLES,i+6);j++)current[j]=distance(x[i-1],y[i-1],tx[j-1],ty[j-1])+Math.min(previous[j-1],Math.min(previous[j],current[j-1]));
            double[] swap=previous;previous=current;current=swap;
        }
        return previous[SAMPLES]/SAMPLES;
    }
}
