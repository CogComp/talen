package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.IntPair;

/**
 *
 * A suggestion object holds a span, a label, and a reason for this suggestion.
 *
 * Created by stephen on 2/27/17.
 */
public class Suggestion {

    public String label;
    public String reason;
    public IntPair span;

    public Suggestion(IntPair span, String label, String reason){
        this.span = span;
        this.label = label;
        this.reason = reason;
    }

    public int getStartSpan(){
        return this.span.getFirst();
    }

    public int getEndSpan(){
        return this.span.getSecond();
    }
}
