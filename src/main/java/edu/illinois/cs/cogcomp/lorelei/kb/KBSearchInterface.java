package edu.illinois.cs.cogcomp.lorelei.kb;

import edu.illinois.cs.cogcomp.lorelei.edl.KBEntity;

import java.util.Map;
import java.util.List;

/**
 * Any class that supports database search for TALEN should implement this interface
 * @author prasanna
 **/
public abstract class KBSearchInterface {
    /**
     * @param query
     * @param ngram
     * @param type (GPE, PER, LOC, etc)
     * @param numcands
     *
     * @return map of integer entity id to the score based on the relevance of search
     * **/
    public abstract Map<Integer,Double> retrieve(String query, List<Integer> ngram, String type, int numcands);

    /**
     * @param id
     * @return KBEntity class representation of the corresponding entity id
     * **/
    public abstract KBEntity get(int id);
}
