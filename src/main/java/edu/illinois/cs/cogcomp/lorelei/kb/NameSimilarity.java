package edu.illinois.cs.cogcomp.lorelei.kb;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by mayhew2 on 7/25/17.
 */
public class NameSimilarity extends Similarity {

    @Override
    public long computeNorm(FieldInvertState fieldInvertState) {
        return 0;
    }

    /**
     * Assume we will do nothing with this!
     * @param cs
     * @param ts
     * @return
     */
    @Override
    public SimWeight computeWeight(CollectionStatistics cs, TermStatistics... ts) {
        TermStatistics tt = ts[0];
        String termstring = tt.term().utf8ToString();

        NameSimWeight nsw = new NameSimWeight(termstring);

        return nsw;
    }

    @Override
    public SimScorer simScorer(SimWeight simWeight, LeafReaderContext lrc) throws IOException {

        NameSimWeight nsw = (NameSimWeight) simWeight;


        LeafReader lr = lrc.reader();



        SimScorer ss = new SimScorer() {
            @Override
            public float score(int doc, float freq) {

                float jaccard = freq;
                try {
                    Document d = lr.document(doc);
                    String n = d.get("asciiname");
                    List<String> docngrams = KBStringMatcher.getngrams(n, 2);
                    jaccard = KBStringMatcher.jaccard(new HashSet<>(docngrams), new HashSet<>(nsw.candngrams));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return jaccard;
            }

            @Override
            public float computeSlopFactor(int distance) {
                return 0;
            }

            @Override
            public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
                return 0;
            }
        };

        return ss;
    }

    private class NameSimWeight extends SimWeight{

        private String term;
        public ArrayList<String> candngrams;

        public NameSimWeight(String term){
            this.term = term;
            this.candngrams = KBStringMatcher.getngrams(term,2);
        }

        @Override
        public float getValueForNormalization() {
            return 0;
        }

        @Override
        public void normalize(float v, float v1) {

        }
    }
}
