package net.weverwijk.address.cleaner;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;

public class LowerCaseTermQuery extends TermQuery {
  public LowerCaseTermQuery(Term t) {
    super(new Term(t.field(),t.text().toLowerCase()));
  }
}
