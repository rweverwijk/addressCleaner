package net.weverwijk.address.cleaner;


import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.BooleanFilter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class PostcodeCheck {

  private static final Version version = Version.LUCENE_4_10_2;
  private final Directory index;
  private final PerFieldAnalyzerWrapper analyzer;


  public PostcodeCheck() {
    index = new RAMDirectory();
    HashMap<String, Analyzer> analyzers = new HashMap<>();
    analyzers.put("complete", new DutchAnalyzer());
    analyzers.put("streetAnalyzed", new DutchAnalyzer());
    analyzer = new PerFieldAnalyzerWrapper(new SimpleLowerCaseKeywordAnalyzer(), analyzers);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        try {
          index.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
  }

  public void loadAddresses(String fileName) throws IOException {
    CSVReader csvReader = new CSVReader(new FileReader(fileName), ';', '\"');
    HashMap<String, Integer> header = convertToColumnLookup(csvReader.readNext());

    IndexWriterConfig config = new IndexWriterConfig(version, analyzer)
        .setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    IndexWriter writer = new IndexWriter(index, config);
    String[] nextLine;
    while ((nextLine = csvReader.readNext()) != null) {
      Document doc = new Document();
      String postcode = nextLine[header.get("postcode")];
      String city = nextLine[header.get("city")];
      String municipality = nextLine[header.get("municipality")];
      String street = nextLine[header.get("street")];
      doc.add(new TextField("postcode", postcode, Field.Store.YES));
      doc.add(new TextField("street", street, Field.Store.YES));
      doc.add(new TextField("streetAnalyzed", street, Field.Store.YES));
      doc.add(new TextField("city", city, Field.Store.YES));
      doc.add(new TextField("municipality", municipality, Field.Store.YES));
      doc.add(new TextField("numbertype", nextLine[header.get("numbertype")], Field.Store.YES));
      doc.add(new IntField("minnumber", Integer.parseInt(nextLine[header.get("minnumber")]), Field.Store.YES));
      doc.add(new IntField("maxnumber", Integer.parseInt(nextLine[header.get("maxnumber")]), Field.Store.YES));
      doc.add(new TextField("complete", String.format("%s %s %s %s", postcode, street, city, municipality), Field.Store.YES));
      writer.addDocument(doc);
    }
    writer.commit();
    writer.close();

  }

  public Address getAddress(Address address) throws IOException, ParseException {
    return this.getAddress(address, false);
  }

  public Address getAddress(Address address, boolean debug) throws IOException, ParseException {
    int limit = 20;
    Address result;

    try (IndexReader reader = DirectoryReader.open(index)) {
      BooleanQuery booleanQuery = new BooleanQuery();

      if (address.getPostcode() != null) {
        Query postcode = new LowerCaseTermQuery(new Term("postcode", address.getPostcode()));
        postcode.setBoost(20F);
        booleanQuery.add(postcode, BooleanClause.Occur.SHOULD);
      }
      if (address.getStreet() != null) {
        LowerCaseTermQuery streetTerm = new LowerCaseTermQuery(new Term("street", address.getStreet()));
        streetTerm.setBoost(30F);
        booleanQuery.add(streetTerm, BooleanClause.Occur.SHOULD);
        booleanQuery.add(new FuzzyQuery(new Term("street", address.getStreet())), BooleanClause.Occur.SHOULD);

        QueryParser qp = new QueryParser("streetAnalyzed", new DutchAnalyzer());
        booleanQuery.add(qp.parse(QueryParser.escape(address.getStreet())), BooleanClause.Occur.SHOULD);
      }
      if (address.getCity() != null) {
        booleanQuery.add(new FuzzyQuery(new Term("city", address.getCity())), BooleanClause.Occur.SHOULD);
        LowerCaseTermQuery cityTerm = new LowerCaseTermQuery(new Term("city", address.getCity()));
        cityTerm.setBoost(30F);
        booleanQuery.add(cityTerm, BooleanClause.Occur.SHOULD);
      }
      if (address.getMunicipality() != null) {
        booleanQuery.add(new FuzzyQuery(new Term("municipality", address.getMunicipality())), BooleanClause.Occur.SHOULD);
        LowerCaseTermQuery municipalityTerm = new LowerCaseTermQuery(new Term("municipality", address.getMunicipality()));
        municipalityTerm.setBoost(5F);
        booleanQuery.add(municipalityTerm, BooleanClause.Occur.SHOULD);
      }

      try {
        if (address.getHouseNumber() != null) {
          int houseNumber = Integer.parseInt(address.getHouseNumber());

          BooleanQuery oddEvenQuery = new BooleanQuery();
          oddEvenQuery.add(new LowerCaseTermQuery(new Term("numbertype", "mixed")), BooleanClause.Occur.SHOULD);
          if (houseNumber % 2 == 0) {
            oddEvenQuery.add(new LowerCaseTermQuery(new Term("numbertype", "even")), BooleanClause.Occur.SHOULD);
          } else {
            oddEvenQuery.add(new LowerCaseTermQuery(new Term("numbertype", "odd")), BooleanClause.Occur.SHOULD);

          }
          booleanQuery.add(oddEvenQuery, BooleanClause.Occur.MUST);
          booleanQuery.add(NumericRangeQuery.newIntRange("minnumber", 0, houseNumber, true, true), BooleanClause.Occur.SHOULD);
          booleanQuery.add(NumericRangeQuery.newIntRange("maxnumber", houseNumber, 9999, true, true), BooleanClause.Occur.SHOULD);
        }
      } catch (NumberFormatException e) {
        // nothing to see, walk through...
      }

      if (StringUtils.isNotBlank(address.getDescription())) {
        QueryParser qp = new QueryParser("complete", new DutchAnalyzer());
        booleanQuery.add(qp.parse(QueryParser.escape(address.getDescription())), BooleanClause.Occur.SHOULD);
      }

      result = searchAddress(limit, booleanQuery, null, reader, debug, address);
    }
    if (result != null) {
      addHouseNumber(address, result);
    }
    return result;
  }

  private void addHouseNumber(Address address, Address result) {
    result.setHouseNumber(address.getHouseNumber());
    result.setHouseNumberAffix(address.getHouseNumberAffix());
    result.setDescription(address.getDescription());
    if (StringUtils.isEmpty(result.getHouseNumber())) {
      result.fillHouseNumberFromDescription();
      // if we don't know the houseNumber we cannot know the postcode
      result.setPostcode(null);
    }
  }

  public HashMap<String, Integer> convertToColumnLookup(String[] headers) {
    HashMap<String, Integer> result = new HashMap<String, Integer>();
    for (int i = 0; i < headers.length; i++) {
      result.put(headers[i], i);
    }
    return result;
  }

  private Address searchAddress(final int limit, final Query query,
                                BooleanFilter filterClauses, final IndexReader reader, boolean debug, Address originalAddress) throws IOException {
    Address result = null;
    Float lastScore = null;

    IndexSearcher searcher = new IndexSearcher(reader);
    TopDocs docs = searcher.search(query, filterClauses, limit);
    if (debug) {
      printDebug(query, searcher, docs);
    }
    for (final ScoreDoc scoreDoc : docs.scoreDocs) {
      if (lastScore == null) {
        lastScore = scoreDoc.score;
      } else if ((lastScore / 2) > scoreDoc.score) {
        break;
      }
      Address nextAddress = new Address(reader.document(scoreDoc.doc));


      if (result == null) {
        result = nextAddress;
        continue;
      }
      if (nextAddress.getLevenshteinDistance(originalAddress) < result.getLevenshteinDistance(originalAddress)) {
        result = nextAddress;
      }
    }
    return result;
  }

  private void printDebug(Query query, IndexSearcher searcher, TopDocs docs) throws IOException {

    System.out.println(docs.totalHits + " found for query: " + query);

    for (final ScoreDoc scoreDoc : docs.scoreDocs) {
      System.out.println(searcher.doc(scoreDoc.doc));
      System.out.println(searcher.explain(query, scoreDoc.doc));
    }
  }
}
