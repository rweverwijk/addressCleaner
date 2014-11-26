package net.weverwijk.address.cleaner;

import lombok.Data;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;

@Data
public class Address {
  private String postcode;
  private String city;
  private String street;
  private String houseNumber;
  private String houseNumberAffix;
  private String description;

  public Address(String postcode, String city, String street, String houseNumber, String houseNumberAffix, String description) {
    this(postcode, city, street, houseNumber, houseNumberAffix);
    this.description = description;
  }

  public Address(String postcode, String city, String street, String houseNumber, String houseNumberAffix) {
    this.postcode = StringUtils.isNotEmpty(postcode) ? postcode.trim() : null;
    this.city = StringUtils.isNotEmpty(city) ? city.trim() : null;
    this.street = StringUtils.isNotEmpty(street) ? street.trim() : null;
    this.houseNumber = StringUtils.isNotEmpty(houseNumber) ? houseNumber.trim() : null;
    this.houseNumberAffix = StringUtils.isNotEmpty(houseNumberAffix) ? houseNumberAffix.trim() : null;

    this.cleanUpHouseNumbers();
  }

  private void cleanUpHouseNumbers() {
    if (StringUtils.isEmpty(this.getHouseNumber()) && StringUtils.isNotEmpty(this.getStreet())) {
      String[] addressSplit = this.getStreet().split(" ");
      String potentialHousenumber = addressSplit[addressSplit.length - 1];
      try {
        Integer.parseInt(potentialHousenumber);
        this.setStreet(StringUtils.join(ArrayUtils.remove(addressSplit, addressSplit.length - 1), " "));
        this.setHouseNumber(potentialHousenumber);
      } catch (NumberFormatException nfe) {
        // nothing to see walk through
      }
    }
  }

  public Address(Document fields) {
    this.postcode = fields.get("postcode");
    this.city = fields.get("city");
    this.street = fields.get("street");
//    this.houseNumber = Integer.parseInt(fields.get("houseNumber"));
//    this.houseNumberAffix = fields.get("houseNumberAffix");
  }

  public int getLevenshteinDistance(Address compareTo) {
    if (compareTo != null) {
      return levenshteinDistance(this.city, compareTo.city) +
          levenshteinDistance(this.street, compareTo.street) +
          levenshteinDistance(this.postcode, compareTo.postcode);
    }
    return 0;
  }

  private int levenshteinDistance(String from, CharSequence to) {
    if (to != null) {
      return StringUtils.getLevenshteinDistance(from, to);
    }
    return 0;
  }

  public void fillHouseNumberFromDescription() {
    String[] split = description.split(" ");
    int houseNumberPosition = 1;
    int bestLevenshteinDistance = 99;
    for (int i = 0; i < split.length; i++) {
        int currentDistance = levenshteinDistance(split[i], getStreet());
        if (currentDistance <= bestLevenshteinDistance) {
          bestLevenshteinDistance = currentDistance;
          houseNumberPosition = i + 1;
      }
    }
    try {
      Integer.parseInt(split[houseNumberPosition]);
      setHouseNumber(split[houseNumberPosition]);
    } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
      // nothing to see...
    }
  }
}
