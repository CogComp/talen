package io.github.mayhewsw.classifier;

import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * Created by mayhew2 on 6/15/17.
 */
public class Candidate {

    public boolean isgood;
    public ArrayList<String> tokens;
    public ArrayList<String> contexts;
    public String name;

    public Candidate(String name, HashMap<String, Double> ctx) {
        tokens = new ArrayList<>();
        tokens.addAll(Arrays.asList(name.split(" ")));

        this.name = name;

        contexts = new ArrayList<>();
        contexts.addAll(ctx.keySet());
    }

    @Override
    public String toString() {
        return "Candidate{" +
                "isgood=" + isgood +
                ", tokens=" + StringUtils.join(tokens, " ") +
                '}';
    }
}
