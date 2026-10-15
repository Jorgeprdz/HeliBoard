// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.content.Intent;
import android.provider.CalendarContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36)
public class AiSharedTextActivityTest {
 private AiSharedTextActivity launch(Intent intent){
  return Robolectric.buildActivity(AiSharedTextActivity.class,intent).create().start().resume().get();
 }
 private static List<View> descendants(View root){
  List<View> values=new ArrayList<>();values.add(root);
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)values.addAll(descendants(((ViewGroup)root).getChildAt(i)));
  return values;
 }
 private static Button button(AiSharedTextActivity activity,String prefix){
  View root=activity.findViewById(android.R.id.content);
  for(View view:descendants(root))if(view instanceof Button&&((Button)view).getText().toString().startsWith(prefix))return (Button)view;
  throw new AssertionError("Missing button: "+prefix);
 }
 @Test public void rejectsAnythingExceptExplicitPlainText(){
  AiSharedTextActivity activity=launch(new Intent(Intent.ACTION_VIEW).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"hola"));
  assertTrue(activity.isFinishing());
 }
 @Test public void readonlyProcessTextCannotReturnAReplacement(){
  Intent intent=new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
      .putExtra(Intent.EXTRA_PROCESS_TEXT,"hola").putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true);
  AiSharedTextActivity activity=launch(intent);
  assertEquals(View.GONE,button(activity,"Aceptar resultado").getVisibility());
 }
 @Test public void calendarActionPrefillsOnlyResolvedDateTimeAndLocation(){
  Intent intent=new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
      .putExtra(Intent.EXTRA_PROCESS_TEXT,"Nos vemos mañana a las 16:30 en Insurgentes Sur 1200 por 90 minutos")
      .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true);
  AiSharedTextActivity activity=launch(intent);
  button(activity,"Abrir acción: mañana").performClick();
  Intent opened=shadowOf(activity).getNextStartedActivity();
  assertNotNull(opened);assertEquals(Intent.ACTION_INSERT,opened.getAction());
  assertEquals(CalendarContract.Events.CONTENT_URI,opened.getData());
  assertEquals("Insurgentes Sur 1200",opened.getStringExtra(CalendarContract.Events.EVENT_LOCATION));
  assertTrue(opened.hasExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME));
  assertEquals(90*60*1000L,opened.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME,0)-opened.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,0));
 }
 @Test public void addressOffersEditableMapDirections(){
  Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Insurgentes Sur 1200");
  AiSharedTextActivity activity=launch(intent);button(activity,"Obtener indicaciones").performClick();
  Intent opened=shadowOf(activity).getNextStartedActivity();assertNotNull(opened);
  assertEquals(Intent.ACTION_VIEW,opened.getAction());assertTrue(opened.getDataString().startsWith("google.navigation:q="));
 }
}
