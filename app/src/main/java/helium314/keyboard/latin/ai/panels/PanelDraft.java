// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.panels;
import java.util.Objects;
/** Source is isolated from the host editor; only a complete current result is exposed for use. */
public final class PanelDraft {
 private final Object editor; private long revision; private boolean closed;
 private String source="", result="";
 public PanelDraft(Object editor){this.editor=editor;}
 public String source(){return source;}
 public long ticket(){return revision;}
 public void setSource(String text){if(closed)return;source=text==null?"":text;invalidate();}
 public void append(String text){if(text!=null&&source.length()+text.length()<=4096)setSource(source+text);}
 public void delete(){if(!source.isEmpty())setSource(source.substring(0,source.offsetByCodePoints(source.length(),-1)));}
 public void invalidate(){revision++;result="";}
 public boolean current(Object now){return !closed&&editor!=null&&Objects.equals(editor,now);}
 public boolean offer(long ticket,Object now,String text,boolean complete){
  if(ticket!=revision||!current(now)||!complete||text==null||text.trim().isEmpty()||text.length()>16384)return false;
  result=text;return true;
 }
 public String result(Object now){return current(now)?result:"";}
 public void close(){closed=true;source="";invalidate();}
}
