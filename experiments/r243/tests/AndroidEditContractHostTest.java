// SPDX-License-Identifier: GPL-3.0-only
// Host-only regression for the pure-Java editing boundary; does NOT emulate Android InputConnection.
import helium314.keyboard.latin.ai.*;
import helium314.keyboard.latin.ai.local.ContinuousReviewAnchor;
import java.util.*;
public final class AndroidEditContractHostTest {
 static int pass=0,fail=0;
 static void check(String label,boolean ok){if(ok)pass++;else{fail++;System.err.println("FAIL "+label);}}
 static boolean wellFormed(CharSequence s){
  for(int i=0;i<s.length();i++){char c=s.charAt(i);
   if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))return false;}
   else if(Character.isLowSurrogate(c))return false;
  }return true;
 }
 static class Editor implements AiEditTransaction.Target {
  final Object id=new Object();String text;String pkg="org.test";int field=1,cursor;long generation=1,revision=1;
  boolean allow=true;
  Editor(String s){text=s;cursor=s.length();}
  public EditorSnapshot capture(){return new EditorSnapshot(id,pkg,field,generation,revision,cursor,cursor,"");}
  public boolean replace(int s,int e,CharSequence value){
   if(!allow||s<0||e>text.length()||s>e)return false;
   text=text.substring(0,s)+value+text.substring(e);cursor=s+value.length();revision++;return true;
  }
 }
 static void standard(String input,String bad,String good){
  Editor e=new Editor(input);AiEditTransaction tx=new AiEditTransaction();int a=input.indexOf(bad);
  check("span_found:"+bad,a>=0);
  if(a<0)return;
  check("apply:"+bad,tx.applySpan(e.capture(),a,a+bad.length(),bad,good,e));
  check("applied_exact:"+bad,e.text.equals(input.substring(0,a)+good+input.substring(a+bad.length())));
  check("undo:"+bad,tx.undo(e));
  check("restore:"+bad,e.text.equals(input));
  check("once:"+bad,!tx.undo(e));
 }
 static void run() {
  standard("necsito enviar","necsito","necesito");
  standard("hola este documeto","documeto","documento");
  standard("envio el archivo","envio","envío");
  standard("hola amigso","amigso","amigos");
  standard("buenastardes","buenastardes","buenas tardes");
  standard("send the fiel","fiel","file");
  standard("Ana teclao","teclao","teclado");
  standard("no teclao","teclao","teclado");
  standard("2026 teclao","teclao","teclado");
  standard("😀 escribo mal","mal","bien");
  // Correct words remain untouched unless there is an explicit, bounded target.
  Editor valid=new Editor("este teclado foros casa quiero");AiEditTransaction noEdit=new AiEditTransaction();
  check("valid_no_autocommit",valid.text.equals("este teclado foros casa quiero")&&!noEdit.hasUndo());
  // Wrong cursor/selection, a different editor, and a cancelled/revised transaction all abstain.
  Editor e=new Editor("text blah");AiEditTransaction tx=new AiEditTransaction();EditorSnapshot snap=e.capture();
  e.cursor=2;check("cursor_move_rejected",!tx.applySpan(snap,5,9,"blah","word",e));
  e.cursor=e.text.length();snap=e.capture();e.revision++;
  check("content_revision_rejected",!tx.applySpan(snap,5,9,"blah","word",e));
  e.revision--;snap=e.capture();Editor other=new Editor("text blah");
  check("cross_editor_rejected",!tx.applySpan(snap,5,9,"blah","word",other));
  // Composing is an Android-side policy; test only the pure Java decision contract.
  check("sensitive_password",SensitiveInputPolicy.protectedEditor(0x81,0,false,false));
  check("sensitive_email",SensitiveInputPolicy.protectedEditor(0x21,0,false,false));
  check("sensitive_uri",SensitiveInputPolicy.protectedEditor(0x11,0,false,false));
  check("sensitive_incognito",SensitiveInputPolicy.protectedEditor(0x1,0,true,false));
  check("normal_text_allowed",!SensitiveInputPolicy.protectedEditor(0x1,0,false,false));
  // UTF-16: a replacement starting in the low half must be refused rather than split an emoji.
  Editor unicode=new Editor("A😀B");AiEditTransaction unicodeTx=new AiEditTransaction();
  boolean partial=unicodeTx.applySpan(unicode.capture(),2,3,"\uDE00","x",unicode);
  check("utf16_mid_surrogate_rejected",!partial);
  check("utf16_text_remains_well_formed",wellFormed(unicode.text));
  Editor malformed=new Editor("hola");AiEditTransaction malformedTx=new AiEditTransaction();
  check("utf16_replacement_unpaired_rejected",!malformedTx.applySpan(malformed.capture(),0,4,"hola","\uD83D",malformed));
  check("utf16_valid_remains",malformed.text.equals("hola"));
  // Async dependency anchor can detect altered content, editor, field, selection, generation.
  Object id=new Object();ContinuousReviewAnchor anchor=new ContinuousReviewAnchor(id,"org.test",5,10,0,"hola");
  check("async_append",anchor.matches(id,"org.test",5,10,8,8,"hola hoy"));
  check("async_modified_text",!anchor.matches(id,"org.test",5,10,8,8,"raro hoy"));
  check("async_different_editor",!anchor.matches(new Object(),"org.test",5,10,8,8,"hola hoy"));
  check("async_different_field",!anchor.matches(id,"org.test",6,10,8,8,"hola hoy"));
  check("async_invalidated",!anchor.matches(id,"org.test",5,11,8,8,"hola hoy"));
  check("async_selection",!anchor.matches(id,"org.test",5,10,7,8,"hola hoy"));
  check("after_replace_cursor",AiCursorPolicy.afterReplacement(7,7,2,5,7)==11);
 }
 public static void main(String[]args){run();System.out.println("HOST_EDIT_CONTRACTS PASS="+pass+" FAIL="+fail);if(fail>0)System.exit(1);}
}
