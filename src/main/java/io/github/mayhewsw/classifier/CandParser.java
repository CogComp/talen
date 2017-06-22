package io.github.mayhewsw.classifier;

import edu.illinois.cs.cogcomp.lbjava.parse.Parser;

import java.util.List;

/**
 * Created by mayhew2 on 6/20/17.
 */
public class CandParser implements Parser {

    private final List<Candidate> cands;
    private int i;


    public CandParser(List<Candidate> cands){
        this.cands = cands;
        this.i = 0;
    }

    @Override
    public Object next() {
        if(i >= cands.size()) return null;
        return cands.get(this.i++);
    }

    @Override
    public void reset() {
        this.i = 0;
    }

    @Override
    public void close() {

    }
}
