package net.weverwijk.address.cleaner;


import au.com.bytecode.opencsv.CSVReader;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeFilter;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class PostcodeCheck {

  private static final Version version = Version.LUCENE_48;
  private final Directory index;
  private final KeywordAnalyzer keywordAnalyzer;


  public PostcodeCheck() {
    index = new RAMDirectory();
    keywordAnalyzer = new KeywordAnalyzer();

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

  public void loadAddresses() throws IOException {
    CSVReader csvReader = new CSVReader(new FileReader("/Users/rvanweverwijk/Downloads/postcode_NL_head.csv"), ';', '\"');
    HashMap<String, Integer> header = convertToColumnLookup(csvReader.readNext());

    IndexWriterConfig config = new IndexWriterConfig(version, keywordAnalyzer)
        .setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    IndexWriter writer = new IndexWriter(index, config);
    String[] nextLine;
    while ((nextLine = csvReader.readNext()) != null) {
      Document doc = new Document();
      doc.add(new TextField("postcode", nextLine[header.get("postcode")], Field.Store.YES));
      doc.add(new TextField("street", nextLine[header.get("street")], Field.Store.YES));
      doc.add(new TextField("city", nextLine[header.get("city")], Field.Store.YES));
      doc.add(new TextField("numbertype", nextLine[header.get("numbertype")], Field.Store.YES));
      doc.add(new IntField("minnumber", Integer.parseInt(nextLine[header.get("minnumber")]), Field.Store.YES));
      doc.add(new IntField("maxnumber", Integer.parseInt(nextLine[header.get("maxnumber")]), Field.Store.YES));
      writer.addDocument(doc);
    }
    writer.commit();
    writer.close();

  }

  public Address getAddress(Address address) throws IOException {
    return this.getAddress(address, false);
  }

  public Address getAddress(Address address, boolean debug) throws IOException {
    int limit = 20;
    Address result;

    try (IndexReader reader = DirectoryReader.open(index)) {
      BooleanQuery booleanQuery = new BooleanQuery();

      if (address.getPostcode() != null) {
        Query postcode = new TermQuery(new Term("postcode", address.getPostcode()));
        postcode.setBoost(20F);
        booleanQuery.add(postcode, BooleanClause.Occur.SHOULD);
      }
      if (address.getStreet() != null) {
        TermQuery streetTerm = new TermQuery(new Term("street", address.getStreet()));
        streetTerm.setBoost(30F);
        booleanQuery.add(streetTerm, BooleanClause.Occur.SHOULD);
        booleanQuery.add(new FuzzyQuery(new Term("street", address.getStreet())), BooleanClause.Occur.SHOULD);
      }
      if (address.getCity() != null) {
        booleanQuery.add(new FuzzyQuery(new Term("city", address.getCity())), BooleanClause.Occur.SHOULD);
        TermQuery cityTerm = new TermQuery(new Term("city", address.getCity()));
        cityTerm.setBoost(30F);
        booleanQuery.add(cityTerm, BooleanClause.Occur.SHOULD);
      }

      BooleanFilter filterClauses = null;
      try {
        if (address.getHouseNumber() != null) {
//          filterClauses = new BooleanFilter();
          int houseNumber = Integer.parseInt(address.getHouseNumber());

          BooleanQuery oddEvenQuery = new BooleanQuery();
          oddEvenQuery.add(new TermQuery(new Term("numbertype", "mixed")), BooleanClause.Occur.SHOULD);
          if (houseNumber % 2 == 0) {
            oddEvenQuery.add(new TermQuery(new Term("numbertype", "even")), BooleanClause.Occur.SHOULD);
          } else {
            oddEvenQuery.add(new TermQuery(new Term("numbertype", "odd")), BooleanClause.Occur.SHOULD);

          }
          booleanQuery.add(oddEvenQuery, BooleanClause.Occur.MUST);
          booleanQuery.add(NumericRangeQuery.newIntRange("minnumber", 0, houseNumber, true, true), BooleanClause.Occur.SHOULD);
          booleanQuery.add(NumericRangeQuery.newIntRange("maxnumber", houseNumber, 9999, true, true), BooleanClause.Occur.SHOULD);
        }
      } catch (NumberFormatException e) {
        // nothing to see, walk through...
      }

      result = searchAddress(limit, booleanQuery, filterClauses, reader, debug, address);

    }
    if (result != null) {
      if (address.getHouseNumber() != null) {
        result.setHouseNumber(address.getHouseNumber());
      } else {
        String[] addressSplit = address.getStreet().split(" ");
        result.setHouseNumber(addressSplit[addressSplit.length - 1]);
      }
      result.setHouseNumberAffix(address.getHouseNumberAffix());
    }
    return result;
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
//    resetIDF(searcher);
    TopDocs docs = searcher.search(query, filterClauses, limit);
    if (debug) {
      printDebug(query, searcher, docs);
    }
    for (final ScoreDoc scoreDoc : docs.scoreDocs) {
      if (lastScore == null ) {
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

  private void resetIDF(IndexSearcher searcher) {
    searcher.setSimilarity(new DefaultSimilarity() {
      @Override
      public float tf(float freq) {
        return 1.0f;
      }

      @Override
      public float idf(long docFreq, long numDocs) {
        return 1.0f;
      }
    });
  }

  private void printDebug(Query query, IndexSearcher searcher, TopDocs docs) throws IOException {

    System.out.println(docs.totalHits + " found for query: " + query);

    for (final ScoreDoc scoreDoc : docs.scoreDocs) {
      System.out.println(searcher.doc(scoreDoc.doc));
      System.out.println(searcher.explain(query, scoreDoc.doc));
    }
  }
}
