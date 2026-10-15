// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.entities;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class CalendarResolverTest {
 private final TimeZone zone=TimeZone.getTimeZone("America/Mexico_City");
 private long now(){Calendar c=new GregorianCalendar(zone);c.clear();c.set(2026,Calendar.OCTOBER,15,10,0);return c.getTimeInMillis();}
 private Long resolve(String date,String time){try{return (Long)Class.forName("helium314.keyboard.latin.ai.entities.CalendarResolver").getMethod("resolve",String.class,String.class,long.class,TimeZone.class).invoke(null,date,time,now(),zone);}catch(Exception e){throw new AssertionError("Relative calendar resolver missing",e);}}
 private void expect(Long value,int day,int hour,int minute){assertNotNull(value);Calendar c=new GregorianCalendar(zone);c.setTimeInMillis(value);assertEquals(day,c.get(Calendar.DAY_OF_MONTH));assertEquals(hour,c.get(Calendar.HOUR_OF_DAY));assertEquals(minute,c.get(Calendar.MINUTE));}
 @Test public void relativeTomorrowUsesLocalTimezone(){expect(resolve("mañana","16:30"),16,16,30);}
 @Test public void nextThursdayAdvancesAWeekWhenTodayIsThursday(){expect(resolve("próximo jueves","4:30 pm"),22,16,30);}
 @Test public void ambiguousMorningOrEveningDoesNotInventTime(){assertNull(resolve("mañana","4:30"));}
 @Test public void invalidDatesAreRejected(){assertNull(resolve("2026-02-30","16:30"));}
 @Test public void ambiguousNumericDateIsNotGuessed(){assertNull(resolve("03/04/2026","16:30"));}
 @Test public void explicitIsoAndMidnightAreSupported(){Long value=resolve("2026-10-20","12:00 am");expect(value,20,0,0);}
 @Test public void missingDateDoesNotInventToday(){assertNull(resolve("","16:30"));}
}
