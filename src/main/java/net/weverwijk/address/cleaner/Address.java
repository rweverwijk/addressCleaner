package net.weverwijk.address.cleaner;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;

@Data
public class Address {
  private String postcode;
  private String city;
  private String street;
  private String houseNumber;
  private String houseNumberAffix;

  public Address(String postcode, String city, String street, String houseNumber, String houseNumberAffix) {
    this.postcode = postcode;
    this.city = city;
    this.street = street;
    this.houseNumber = houseNumber;
    this.houseNumberAffix = houseNumberAffix;
  }

  public Address(Document fields) {
    this.postcode = fields.get("postcode");
    this.city = fields.get("city");
    this.street = fields.get("street");
//    this.houseNumber = Integer.parseInt(fields.get("houseNumber"));
//    this.houseNumberAffix = fields.get("houseNumberAffix");
  }

  public Address getUpperVersion() {
    return new Address(StringUtils.upperCase(this.postcode), StringUtils.upperCase(this.city),
        StringUtils.upperCase(this.street), StringUtils.upperCase(this.houseNumber), StringUtils.upperCase(this.houseNumberAffix));

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
}
