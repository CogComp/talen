package io.github.mayhewsw.classifier;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.util.IterationEvent;

import java.util.*;

/**
 * Created by mayhew2 on 6/15/17.
 */
public class Candidate {
    public static final String punctuation = "!@#$%^&*()_-+=~`:;<>,./?|\\\"\'‹‹››،[]{}";
    public boolean isgood;
    public ArrayList<String> tokens;
    public HashMap<String, Double> contexts;
    public String name;
    public double totalcontexts;

    public Candidate(String name, HashMap<String, Double> ctx) {
        tokens = new ArrayList<>();
        tokens.addAll(Arrays.asList(name.split(" ")));

        this.name = name;

        contexts = ctx;
        totalcontexts = ctx.values().stream().mapToDouble(i -> i).sum();
    }

    @Override
    public String toString() {
        return "Candidate{" +
                "isgood=" + isgood +
                ", tokens=" + StringUtils.join(tokens, " ") +
                '}';
    }

    public boolean haspunc(){
        for(String t : tokens){
            if(punctuation.contains(t)){
                return true;
            }
        }
        return false;
    }

}
