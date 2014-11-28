package net.weverwijk.address.cleaner;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class Address {
  private static final Pattern SINGLE_HOUSE_NUMBER_PATTERN = Pattern.compile("([0-9]+)([a-zA-Z]?)");
  private static final Pattern SINGLE_HOUSE_NUMBER_PATTERN_AT_END_OF_LINE = Pattern.compile("([0-9]+)([a-zA-Z]?)$");
  private static final Pattern MULTI_HOUSE_NUMBER_PATTERN_AT_END_OF_LINE = Pattern.compile("([0-9]*[a-zA-Z]?)( en |en| - |-| & |&)([0-9]*[a-zA-Z]?)$");

  private String postcode;
  private String city;
  private String municipality;
  private String street;
  private String houseNumber;
  private String houseNumberAffix;
  private String description;


  public Address(String postcode, String city, String municipality, String street, String houseNumber, String houseNumberAffix, String description) {
    this(postcode, city, municipality, street, houseNumber, houseNumberAffix);
    this.description = description;
  }

  public Address(String postcode, String city, String municipality, String street, String houseNumber, String houseNumberAffix) {
    this.postcode = StringUtils.isNotEmpty(postcode) ? postcode.trim() : null;
    this.city = StringUtils.isNotEmpty(city) ? city.trim() : null;
    this.municipality = StringUtils.isNotEmpty(municipality) ? municipality.trim() : null;
    this.street = StringUtils.isNotEmpty(street) ? street.trim() : null;
    this.houseNumber = StringUtils.isNotEmpty(houseNumber) ? houseNumber.trim() : null;
    this.houseNumberAffix = StringUtils.isNotEmpty(houseNumberAffix) ? houseNumberAffix.trim() : null;

    this.cleanUpHouseNumbers();
  }

  public Address(Document fields) {
    this.postcode = fields.get("postcode");
    this.city = fields.get("city");
    this.street = fields.get("street");
    this.municipality = fields.get("municipality");
  }

  private void cleanUpHouseNumbers() {
    if (StringUtils.isEmpty(this.getHouseNumber()) && StringUtils.isNotEmpty(this.getStreet())) {
      cleanupMultiHouseNumbers();
      cleanupSingleHouseNumber();
    }
  }

  private void cleanupSingleHouseNumber() {
    Matcher m = SINGLE_HOUSE_NUMBER_PATTERN_AT_END_OF_LINE.matcher(street);
    if (m.find()) {
      street = street.replace(m.group(0), "").trim();
      houseNumber = m.group(1);
      houseNumberAffix = m.group(2);
    }
  }

  protected void cleanupMultiHouseNumbers() {
    Matcher m = MULTI_HOUSE_NUMBER_PATTERN_AT_END_OF_LINE.matcher(street);
    if (m.find()) {
      street = street.replace(m.group(0), "").trim();
      houseNumber = m.group(1);
      houseNumberAffix = m.group(3);
    }
  }

  public int getLevenshteinDistance(Address compareTo) {
    if (compareTo != null) {
      int cityDistance = levenshteinDistance(this.city, compareTo.city);
      int municipalityDistance = levenshteinDistance(this.municipality, compareTo.city);
      return (cityDistance < municipalityDistance ? cityDistance : municipalityDistance) +
          levenshteinDistance(this.street, compareTo.street) +
          levenshteinDistance(this.postcode, compareTo.postcode);
    }
    return 0;
  }

  private int levenshteinDistance(String from, String to) {
    if (to != null) {
      return StringUtils.getLevenshteinDistance(from.toUpperCase(), to.toUpperCase());
    }
    return 0;
  }

  public void fillHouseNumberFromDescription() {
    if (StringUtils.isNotBlank(description)) {
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
        Matcher m = SINGLE_HOUSE_NUMBER_PATTERN.matcher(split[houseNumberPosition]);
        if (m.find()) {
          houseNumber = m.group(1);
          houseNumberAffix = m.group(2);
        }
      } catch (ArrayIndexOutOfBoundsException aiobe) {
        // cannot find a houseNumber to bad, but not too bad
      }
    }
  }
}
