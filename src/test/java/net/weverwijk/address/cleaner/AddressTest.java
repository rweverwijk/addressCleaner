package net.weverwijk.address.cleaner;

import org.junit.Test;

import static org.junit.Assert.*;

public class AddressTest {

  @Test
  public void testFillHouseNumberFromDescription() {
    Address address = new Address(null, null, "Dorpstraat", null, null, "Dorpstraat 28 te Amsterdam");
    address.fillHouseNumberFromDescription();
    assertEquals("28", address.getHouseNumber());
  }

}