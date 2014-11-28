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
      int cityDistance = levenshteinDistance(this.city.toUpperCase(), compareTo.city.toUpperCase());
      int municipalityDistance = levenshteinDistance(this.municipality.toUpperCase(), compareTo.city.toUpperCase());
      return (cityDistance < municipalityDistance ? cityDistance : municipalityDistance) +
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
      Matcher m = SINGLE_HOUSE_NUMBER_PATTERN.matcher(split[houseNumberPosition]);
      if (m.find()) {
        houseNumber = m.group(1);
        houseNumberAffix = m.group(2);
      }

    }
  }
}
