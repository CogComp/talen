package edu.illinois.cs.cogcomp.lorelei.kb;

import edu.illinois.cs.cogcomp.lorelei.edl.KBEntity;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

public class KBSearch extends KBSearchInterface {
    private static GeonamesLoader gl = new GeonamesLoader("9");
    private static MapDBMatcher db;

    static {
        try {
            db = new MapDBMatcher("9", Arrays.asList(0,1));
            db.load_alldbs();
        } catch (IOException e) {
            e.printStackTrace();
            db = null;
        }
    }

    @Override
    public Map<Integer, Double> retrieve(String query, List<Integer> ngram, String type, int numcands) {
        LinkedHashMap<Integer, Double> results =  db.retrieve(query, ngram, type, db.maplist, 20);
        return new HashMap<>(results);
    }

    @Override
    public KBEntity get(int id) {
        return gl.get(id);
    }
}
