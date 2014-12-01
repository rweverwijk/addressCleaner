package net.weverwijk.address.cleaner;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;

import java.io.Reader;

public class LowerCaseKeywordAnalyzer extends Analyzer {
  @Override
  protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
    KeywordTokenizer keywordTokenizer = new KeywordTokenizer(reader);
    return new TokenStreamComponents(keywordTokenizer, new LowerCaseFilter(keywordTokenizer));
  }
}
