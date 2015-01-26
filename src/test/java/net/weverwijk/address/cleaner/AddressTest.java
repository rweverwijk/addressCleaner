package net.weverwijk.address.cleaner;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class AddressTest {

  @Test
  public void testFillHouseNumberFromDescription() {
    Address address = new Address(null, null, null, "Dorpstraat", null, null, "Dorpstraat 28 te Amsterdam");
    address.fillHouseNumberFromDescription();
    assertEquals("28", address.getHouseNumber());
  }

  @Test
  public void testFillHouseNumberWithAffixFromDescription() {
    Address address = new Address(null, null, null, "Milhezerweg", null, null, "Milhezerweg 55b te Deurne");
    address.fillHouseNumberFromDescription();
    assertEquals("55", address.getHouseNumber());
    assertEquals("b", address.getHouseNumberAffix());
  }

  @Test
  public void testCleanupMultipleAddresses() {
    ArrayList<String[]> testValues = new ArrayList<String[]>();
    testValues.add(new String[]{"Eikenlaan 31 en 33", "Eikenlaan", "31", "33"});
    testValues.add(new String[]{"Eikenlaan 31 & 33", "Eikenlaan", "31", "33"});
    testValues.add(new String[]{"Eikenlaan 31&33", "Eikenlaan", "31", "33"});
    testValues.add(new String[]{"Eikenlaan 31-33", "Eikenlaan", "31", "33"});
    testValues.add(new String[]{"Eikenlaan 31 - 33", "Eikenlaan", "31", "33"});
    testValues.add(new String[]{"1ste Eikenlaan 31 - 33", "1ste Eikenlaan", "31", "33"});
    for (String[] testValue : testValues) {
      Address address = new Address(null, null, null, testValue[0], null, null);
      assertEquals(testValue[1], address.getStreet());
      assertEquals(testValue[2], address.getHouseNumber());
      assertEquals(testValue[3], address.getHouseNumberAffix());
    }
  }

  @Test
  public void testCleanupSingleAddresses() {
    ArrayList<String[]> testValues = new ArrayList<String[]>();
    testValues.add(new String[]{"Eikenlaan 31", "Eikenlaan", "31", ""});
    testValues.add(new String[]{"Eikenlaan 31a", "Eikenlaan", "31", "a"});
    testValues.add(new String[]{"1ste Eikenlaan 31a", "1ste Eikenlaan", "31", "a"});
    for (String[] testValue : testValues) {
      Address address = new Address(null, null, null, testValue[0], null, null);
      assertEquals(testValue[1], address.getStreet());
      assertEquals(testValue[2], address.getHouseNumber());
      assertEquals(testValue[3], address.getHouseNumberAffix());
    }
  }

  @Test
  public void testCleanupEikenlaan31aEn33b() {
    Address address = new Address(null, null, null, "Eikenlaan 31a en 33b", null, null);
    assertEquals("31a", address.getHouseNumber());
    assertEquals("33b", address.getHouseNumberAffix());
    assertEquals("Eikenlaan", address.getStreet());
  }

  @Test
  public void testCleanupSpecialCharactersFromAffix() {
    ArrayList<String[]> testValues = new ArrayList<String[]>();
    testValues.add(new String[]{"Eikenlaan", "31", "-32", "Eikenlaan", "31", "32"});
    testValues.add(new String[]{"Eikenlaan", "31", "/32", "Eikenlaan", "31", "32"});
    testValues.add(new String[]{"Eikenlaan", "31", "+32", "Eikenlaan", "31", "32"});
    for (String[] testValue : testValues) {
      Address address = new Address(null, null, null, testValue[0], testValue[1], testValue[2]);
      assertEquals(testValue[3], address.getStreet());
      assertEquals(testValue[4], address.getHouseNumber());
      assertEquals(testValue[5], address.getHouseNumberAffix());
    }
  }

}