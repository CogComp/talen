package edu.illinois.cs.cogcomp.lorelei.kb;

import edu.illinois.cs.cogcomp.lorelei.edl.KBEntity;

import java.util.Map;
import java.util.List;

public abstract class KBSearchInterface {
    public abstract Map<Integer,Double> retrieve(String query, List<Integer> ngram, String type, int numcands);
    public abstract KBEntity get(int id);
}
