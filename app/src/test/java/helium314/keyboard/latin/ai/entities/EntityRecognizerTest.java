// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.entities;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class EntityRecognizerTest {
 private List<EntityRecognizer.Entity> scan(String s) { return new EntityRecognizer().scan(s); }
 private EntityRecognizer.Entity find(String s,EntityRecognizer.Kind k) { for(EntityRecognizer.Entity e:scan(s))if(e.kind==k)return e; return null; }
 @Test public void preciseAddress() { assertEquals("Insurgentes Sur 1200",find("Nos vemos en Insurgentes Sur 1200.",EntityRecognizer.Kind.ADDRESS).value); }
 @Test public void businessNameIsNotAddress() { assertNull(find("Trabajo en Insurgentes",EntityRecognizer.Kind.ADDRESS)); }
 @Test public void clabeSeparatedAndValidated() { assertEquals("032180000118359719",find("CLABE 032 180 00011835971 9",EntityRecognizer.Kind.CLABE).value); }
 @Test public void invalidBankNumberDoesNotBecomePhone() { assertNull(find("032180000118359718",EntityRecognizer.Kind.PHONE)); assertNull(find("032180000118359718",EntityRecognizer.Kind.CLABE)); }
 @Test public void dialDoesNotConsumePostalCode() { assertNull(find("CP 03100",EntityRecognizer.Kind.PHONE)); assertEquals("+525512345678",find("Llama al +52 55 1234 5678",EntityRecognizer.Kind.PHONE).value); }
 @Test public void urlsDoNotEnableDangerousSchemes() { assertNull(find("javascript:alert(1)",EntityRecognizer.Kind.URL)); assertEquals("https://example.com/x",find("Ver https://example.com/x.",EntityRecognizer.Kind.URL).value); }
 @Test public void preservesEntitySpans() { String s="Envía a juan@example.com, por favor"; EntityRecognizer.Entity e=find(s,EntityRecognizer.Kind.EMAIL); assertEquals(e.value,s.substring(e.start,e.end)); }
 @Test public void bankAccountDoesNotBecomeADialAction(){assertNull(find("Cuenta: 0123456789",EntityRecognizer.Kind.PHONE));assertTrue(scan("Cuenta: 0123456789").stream().anyMatch(e->e.kind.name().equals("BANK_ACCOUNT")&&e.value.equals("0123456789")));}
 @Test public void internationalPhoneRetainsCountryCode(){assertEquals("+442079460958",find("Llama a +44 20 7946 0958",EntityRecognizer.Kind.PHONE).value);}
 @Test public void isoDateAndEnglishRelativeDateAreRecognized(){assertNotNull(find("Meet tomorrow at 16:30",EntityRecognizer.Kind.DATE));assertNotNull(find("Fecha 2026-10-20",EntityRecognizer.Kind.DATE));}
 @Test public void boundsInputAndDoesNotRetainIt() { assertTrue(scan(new String(new char[4097]).replace('\0','a')).isEmpty()); assertTrue(scan("").isEmpty()); }
 @Test public void multiplePhonesStaySeparate() { assertEquals(2,scan("5512345678 y 5587654321").stream().filter(e->e.kind==EntityRecognizer.Kind.PHONE).count()); }
 @Test public void paymentReferenceRequiresAnExplicitLabel() {
  EntityRecognizer.Entity e=find("Referencia de pago: AB-12004567",EntityRecognizer.Kind.PAYMENT_REFERENCE);
  assertNotNull(e);assertEquals("AB-12004567",e.value);
  assertNull(find("Código AB-12004567",EntityRecognizer.Kind.PAYMENT_REFERENCE));
 }
 @Test public void numericPaymentReferenceIsNeverOfferedAsPhone() {
  assertEquals("123456789012",find("Referencia: 123456789012",EntityRecognizer.Kind.PAYMENT_REFERENCE).value);
  assertNull(find("Referencia: 123456789012",EntityRecognizer.Kind.PHONE));
 }
 @Test public void trackingNumberRequiresAnExplicitLabel() {
  EntityRecognizer.Entity e=find("Guía: 1Z999AA10123456784",EntityRecognizer.Kind.TRACKING_NUMBER);
  assertNotNull(e);assertEquals("1Z999AA10123456784",e.value);
  assertNull(find("Pedido 1Z999AA10123456784",EntityRecognizer.Kind.TRACKING_NUMBER));
 }
 @Test public void fullRelativeDateAndDurationAreRecognized(){
  assertEquals("el próximo jueves",find("Nos vemos el próximo jueves por 90 minutos",EntityRecognizer.Kind.DATE).value);
  assertEquals("por 90 minutos",find("Nos vemos el próximo jueves por 90 minutos",EntityRecognizer.Kind.DURATION).value);
 }
 @Test public void peopleAndBusinessesRequireExplicitLabels(){
  assertEquals("Carlos Pérez",find("Contacto: Carlos Pérez",EntityRecognizer.Kind.PERSON).value);
  assertEquals("Acme Seguros",find("Empresa: Acme Seguros",EntityRecognizer.Kind.BUSINESS).value);
  assertNull(find("Reunión con Carlos Pérez",EntityRecognizer.Kind.PERSON));
  assertNull(find("Acme Seguros confirmó",EntityRecognizer.Kind.BUSINESS));
 }
}
