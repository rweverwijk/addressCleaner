package net.weverwijk.address.cleaner;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
@Ignore
public class LargeDescriptionTest {

  private static PostcodeCheck postcodeCheck;

  @BeforeClass
  public static void beforeOnce() throws IOException {
    postcodeCheck = new PostcodeCheck();
    postcodeCheck.loadAddresses("/Users/rvanweverwijk/Downloads/postcode_NL_head.csv");
  }


  @Test
  public void testSimpleAddess() throws IOException, ParseException {
    CSVReader csvReader = new CSVReader(new InputStreamReader(PostcodeCheck.class.getClassLoader().getResourceAsStream("descriptions.csv")), '|', '\"');
    int countWrong = 0;
    int countRight = 0;
    String[] nextLine;
    while ((nextLine = csvReader.readNext()) != null) {
      Address address = new Address(null, null, null, null, null, nextLine[0]);
      Address foundAddress = postcodeCheck.getAddress(address);
      if (foundAddress != null) {
        System.out.println(String.format("%s\n%s -> %s -> %s\n", address.getDescription(), foundAddress.getCity(), foundAddress.getStreet(), foundAddress.getHouseNumber() ));
      } else {
        System.out.println(String.format("%s\n%s\n", address.getDescription(), "Not Found."));
      }
    }
    System.out.println("wrong: " + countWrong + " right: " + countRight);
  }
}
