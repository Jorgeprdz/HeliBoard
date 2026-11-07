// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;
import org.junit.Test;
import static org.junit.Assert.*;
public class TextStickerSuggestionPolicyTest {
 @Test public void privateAndNonSuggestionEditorsNeverGetAutomaticSticker(){
  assertTrue(TextStickerSuggestionPolicy.allowed(1,0,false,false,true,false,false));
  assertFalse(TextStickerSuggestionPolicy.allowed(0x81,0,false,false,true,false,false));
  assertFalse(TextStickerSuggestionPolicy.allowed(1,0,true,false,true,false,false));
  assertFalse(TextStickerSuggestionPolicy.allowed(1,0,false,true,true,false,false));
  assertFalse(TextStickerSuggestionPolicy.allowed(1,0,false,false,false,false,false));
  assertFalse(TextStickerSuggestionPolicy.allowed(1,0x01000000,false,false,true,false,false));
  assertFalse(TextStickerSuggestionPolicy.allowed(1,0,false,false,true,true,false));
  assertFalse(TextStickerSuggestionPolicy.allowed(1,0,false,false,true,false,true));
 }
 @Test public void staleConnectionLogicalEditorCursorOrContextCannotAct(){
  Object connection=new Object(),editor=new Object();
  TextStickerSuggestionPolicy.Target original=new TextStickerSuggestionPolicy.Target(connection,editor,7,"app",10,1,0,4,4,"hola","","hola");
  assertTrue(original.matches(new TextStickerSuggestionPolicy.Target(connection,editor,7,"app",10,1,0,4,4,"hola","","hola")));
  assertFalse(original.matches(new TextStickerSuggestionPolicy.Target(new Object(),editor,7,"app",10,1,0,4,4,"hola","","hola")));
  assertTrue(original.matches(new TextStickerSuggestionPolicy.Target(connection,new Object(),7,"app",10,1,0,4,4,"hola","","hola")));
  assertFalse(original.matches(new TextStickerSuggestionPolicy.Target(connection,editor,8,"app",10,1,0,4,4,"hola","","hola")));
  assertFalse(original.matches(new TextStickerSuggestionPolicy.Target(connection,editor,7,"app",10,1,0,3,3,"hola","","hola")));
  assertFalse(original.matches(new TextStickerSuggestionPolicy.Target(connection,editor,7,"app",10,1,0,4,4,"otro","","hola")));
 }
 @Test public void changedLogicalFieldsAreStaleEvenWithSameConnection(){
  Object connection=new Object(),editor=new Object();
  TextStickerSuggestionPolicy.Target target=new TextStickerSuggestionPolicy.Target(connection,editor,7,"app",10,1,0,4,4,"hola","","hola");
  assertFalse(target.matches(new TextStickerSuggestionPolicy.Target(connection,editor,7,"app",11,1,0,4,4,"hola","","hola")));
  assertFalse(target.matches(new TextStickerSuggestionPolicy.Target(connection,editor,7,"app",10,0x81,0,4,4,"hola","","hola")));
  assertFalse(target.matches(new TextStickerSuggestionPolicy.Target(connection,editor,7,"app",10,1,0x01000000,4,4,"hola","","hola")));
 }
 @Test public void sourceIsBoundedCurrentWordAndRejectsSensitiveContext(){
  assertEquals("hola",TextStickerSuggestionPolicy.source("hola","antes hola",""));
  assertEquals("😀",TextStickerSuggestionPolicy.source("😀","",""));
  assertEquals("😀".repeat(120),TextStickerSuggestionPolicy.source("😀".repeat(120),"",""));
  assertEquals("",TextStickerSuggestionPolicy.source("😀".repeat(121),"",""));
  assertEquals("hola",TextStickerSuggestionPolicy.source("","antes hola",""));
  assertEquals("",TextStickerSuggestionPolicy.source("a".repeat(121),"",""));
  assertEquals("",TextStickerSuggestionPolicy.source("hola","password secreto hola",""));
  assertEquals("",TextStickerSuggestionPolicy.source("hola","hola"," 1234567890123456"));
 }
}
