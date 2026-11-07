// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import java.util.ArrayList;
import java.util.List;

/** Unicode-safe limits and cluster boundaries shared by creator input and rendering. */
public final class TextStickerText {
    public static final int MAX_CODE_POINTS=120;
    private TextStickerText() {}
    public static String validate(String source) {
        if(source==null)throw new IllegalArgumentException("Escribe texto para el sticker");
        if(source.codePointCount(0,source.length())>MAX_CODE_POINTS)
            throw new IllegalArgumentException("Máximo 120 caracteres Unicode");
        boolean visible=false;
        for(int i=0;i<source.length();){
            char unit=source.charAt(i);
            if(Character.isSurrogate(unit)&&!(Character.isHighSurrogate(unit)&&i+1<source.length()&&Character.isLowSurrogate(source.charAt(i+1))))
                throw new IllegalArgumentException("Texto Unicode incompleto");
            int cp=source.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isISOControl(cp)&&cp!='\n'&&cp!='\t')throw new IllegalArgumentException("Carácter de control no admitido");
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&cp!=0x200d&&cp!=0xfe0f)visible=true;
        }
        if(!visible)throw new IllegalArgumentException("Escribe texto para el sticker");
        return source.trim();
    }
    public static List<String> graphemes(String text) {
        List<String> result=new ArrayList<>();
        int start=0,index=0,regionalCount=0,previous=-1;
        while(index<text.length()){
            int cp=text.codePointAt(index);int type=Character.getType(cp);
            boolean regional=cp>=0x1f1e6&&cp<=0x1f1ff;
            boolean extend=type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK
                || cp==0xfe0e||cp==0xfe0f||(cp>=0x1f3fb&&cp<=0x1f3ff)||(cp>=0xe0020&&cp<=0xe007f);
            boolean join=index==start||extend||cp==0x200d||previous==0x200d||virama(previous)
                ||(regional&&regionalCount%2==1)||(previous=='\r'&&cp=='\n');
            if(!join){result.add(text.substring(start,index));start=index;regionalCount=0;}
            regionalCount=regional?regionalCount+1:0;previous=cp;index+=Character.charCount(cp);
        }
        if(start<text.length())result.add(text.substring(start));return result;
    }
    private static boolean virama(int cp){
        switch(cp){case 0x94d:case 0x9cd:case 0xa4d:case 0xacd:case 0xb4d:case 0xbcd:case 0xc4d:case 0xccd:case 0xd4d:case 0xdca:case 0x1039:case 0x17d2:return true;default:return false;}
    }
    public static String appendWithinLimit(String current,String addition,int maximum){
        StringBuilder result=new StringBuilder(current);int count=current.codePointCount(0,current.length());
        for(String cluster:graphemes(addition)){
            int n=cluster.codePointCount(0,cluster.length());if(count+n>maximum)break;
            result.append(cluster);count+=n;
        }return result.toString();
    }
    public static String deleteLast(String text){List<String> clusters=graphemes(text);return clusters.isEmpty()?"":text.substring(0,text.length()-clusters.get(clusters.size()-1).length());}
}
