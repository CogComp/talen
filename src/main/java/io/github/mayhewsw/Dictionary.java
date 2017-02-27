package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.io.LineIO;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

/**
 * Created by mayhew2 on 1/27/17.
 */
public class Dictionary extends HashMap<String, List<String>> {

    private static Logger logger = LoggerFactory.getLogger(Dictionary.class);
    public String dictpath;
    public String dictname;
    private List<Pair<String, String>> newpairs;

    /**
     * Get the all pairs of elements from two lists. In python this is an import. :(
     * @param alist
     * @param blist
     * @return a list of pairs.
     */
    private static List<Pair<String,String>> product(String[] alist, String[] blist){

        List<Pair<String, String>> prod = new ArrayList<>();

        for(String a : alist){
            for(String b : blist){
                prod.add(new Pair<>(a,b));
            }
        }
        return prod;
    }

    public boolean isEmpty(){
        return this.keySet().isEmpty();
    }

    public String getName(){
        return this.dictname;
    }

    /**
     * Because I got tired of writing this so many times.
     * @param m
     * @param p
     */
    private static void addOrIncrement(HashMap<Pair<String, String>, Integer> m, Pair<String, String> p){
        // In python, this is a defaultdict.
        if(!m.containsKey(p)){
            m.put(p, 0);
        }
        m.put(p, m.get(p)+1);
    }

    /**
     * Add a definition to this dictionary.
     * @param key
     * @param def
     */
    public void add(String key, String def){
        if(!this.containsKey(key)){
            this.put(key, new ArrayList<>());
        }
        this.get(key).add(def);

        this.newpairs.add(new Pair<>(key, def));

    }

    /**
     * This saves the user-generated pairs to file.
     */
    public void save() throws IOException {
        if(this.dictpath != null) {
            List<String> outlines = newpairs.stream().map(p -> p.getFirst() + "\t" + p.getSecond()).collect(toList());
            LineIO.write(this.dictpath + ".user", outlines);
        }
    }

    /**
     * Just load an empty dictionary.
     */
    public Dictionary(){}

    public Dictionary(String dictname, String dictpath) {
        // TODO: also read the user generated pairs.

        this.newpairs = new ArrayList<>();

        this.dictpath = dictpath;
        this.dictname = dictname;

        ArrayList<String> dictlines = null;

        try {
            dictlines = LineIO.readGZip(dictpath);
        } catch (IOException e) {
            // an empty dictionary is a graceful failure.
            logger.info("Dictionary file not found: "+dictpath+". Dictionary is empty.");
            return;
        }

        // I want a dictionary that maps from foreign->english.

        HashMap<Pair<String, String>, Integer> pairs = new HashMap<>();

        logger.info("Loading dictionary...");
        for(String line : dictlines){
            String[] sline = line.split("\t");
            String f = sline[0];
            String e = sline[5];

            Pair<String, String> ef = new Pair<>(e,f);
            Pair<String, String> eflower = new Pair<>(e.toLowerCase(),f.toLowerCase());

            addOrIncrement(pairs, ef);
            addOrIncrement(pairs, eflower);

            for(Pair<String, String> p : product(e.split(" "), f.split(" "))){
                addOrIncrement(pairs, p);
                Pair<String, String> plower = new Pair<>(p.getFirst().toLowerCase(), p.getSecond().toLowerCase());
                addOrIncrement(pairs, plower);
            }

            // actually add to dictionary.
            if(!this.containsKey(f)){
                this.put(f, new ArrayList<>());
            }
            // don't include duplicates.
            if(!this.get(f).contains(e)) {
                this.get(f).add(e);
            }

            // and the lowercase version. this is tedious.
            if(!this.containsKey(f.toLowerCase())){
                this.put(f.toLowerCase(), new ArrayList<>());
            }
            // don't include duplicates
            if(!this.get(f.toLowerCase()).contains(e.toLowerCase())) {
                this.get(f.toLowerCase()).add(e.toLowerCase());
            }

        }

        // this is now f2e.
        for(String k : this.keySet()){
            // scores gathers

            Comparator<Pair<String, Integer>> comparator =
                    (p1, p2) -> p1.getSecond().compareTo(p2.getSecond());

            List<String> sortedpairs = this.get(k).stream()
                    .map(w -> new Pair<>(w, k))
                    .map(p -> new Pair<>(p.getFirst(), pairs.get(p)))
                    .sorted(comparator.reversed())
                    .map(p -> p.getFirst())
                    .collect(toList());

            this.put(k, sortedpairs);

        }

        logger.info("Done loading dictionary.");
    }


    public static void main(String[] args) throws IOException {
        Dictionary d = new Dictionary("whatevs", "/shared/experiments/mayhew2/lexicons/spa-eng.masterlex.txt.gz");
    }


}
