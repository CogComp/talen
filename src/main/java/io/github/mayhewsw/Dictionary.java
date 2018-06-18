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
     * Syntactic sugar. Just calls add(key, def, isnew) with isnew set to true
     * @param key
     * @param def
     */
    public void add(String key, String def){
        add(key, def, true);
    }

    /**
     * Add a definition to this dictionary.
     * @param key
     * @param def
     * @param isnew defines whether or not it should be added to the user dictionary list
     */
    public void add(String key, String def, boolean isnew){
        if(!this.containsKey(key)){
            this.put(key, new ArrayList<>());
        }
        // always add to the front of the list.
        this.get(key).add(0, def);

        if(isnew) {
            this.newpairs.add(new Pair<>(key, def));
        }

    }

    /**
     * This saves the user-generated pairs to file.
     */
    public void save(String dataname, String username) throws IOException {
        List<String> outlines = newpairs.stream().map(p -> p.getFirst() + "\t" + p.getSecond()).collect(toList());
        LineIO.write(getUserDictPath(dataname, username), outlines);
    }

    /**
     * This defines a standard for the user dictionary path
     * @param dataname
     * @param username
     * @return
     */
    public String getUserDictPath(String dataname, String username){
        String userdictpath = String.format("dicts/%s-%s.txt", dictname, username);
        return userdictpath;
    }

    /**
     * This creates a default name based on the dataname and username.
     */
    public Dictionary(String dataname, String username){
        this(dataname, null, username);
    }

    public Dictionary(String dictname, String dictpath, String username) {


        this.newpairs = new ArrayList<>();

        this.dictpath = dictpath;
        this.dictname = dictname;

        ArrayList<String> dictlines = null;

        if(dictpath != null) {
            try {
                dictlines = LineIO.read(dictpath);

                // I want a dictionary that maps from foreign->english.

                // This keeps track of pair counts, so we can sort according to popularity.
                HashMap<Pair<String, String>, Integer> pairs = new HashMap<>();

                logger.info("Loading dictionary...");
                for (String line : dictlines) {
                    String[] sline = line.split("\t");
                    if(sline.length < 2) continue;

                    String f = sline[0];
                    String e = sline[1];

                    Pair<String, String> ef = new Pair<>(e, f);
                    Pair<String, String> eflower = new Pair<>(e.toLowerCase(), f.toLowerCase());

                    addOrIncrement(pairs, ef);
                    addOrIncrement(pairs, eflower);

                    for (Pair<String, String> p : product(e.split(" "), f.split(" "))) {
                        addOrIncrement(pairs, p);
                        Pair<String, String> plower = new Pair<>(p.getFirst().toLowerCase(), p.getSecond().toLowerCase());
                        addOrIncrement(pairs, plower);
                    }

                    // actually add to dictionary.
                    this.add(f, e, false);
                }

                // this is now f2e.
                for (String k : this.keySet()) {
                    // scores gathers

                    Comparator<Pair<String, Integer>> comparator = Comparator.comparing(Pair::getSecond);

                    List<String> sortedpairs = this.get(k).stream()
                            .map(w -> new Pair<>(w, k))
                            .map(p -> new Pair<>(p.getFirst(), pairs.get(p)))
                            .sorted(comparator.reversed())
                            .map(p -> p.getFirst())
                            .collect(toList());

                    this.put(k, sortedpairs);
                }
            } catch (IOException e) {
                // an empty dictionary is a graceful failure.
                logger.info("Dictionary file not found: " + dictpath + ". Dictionary is empty.");
            }
        }

        // Also read the user generated pairs.
        ArrayList<String> userlines = new ArrayList<>();
        String userdictpath = this.getUserDictPath(dictname, username);
        try {
            userlines = LineIO.read(userdictpath);
        } catch (IOException e) {
            // an empty dictionary is a graceful failure.
            logger.info("User dictionary file not found: " + dictpath + "." + username +". User dictionary is empty.");
        }
        for(String line : userlines) {
            String[] sline = line.split("\t");
            String f = sline[0];
            String e = sline[1];

            this.add(f, e, true);
        }


        logger.info("Done loading dictionary.");
    }


    public static void main(String[] args) throws IOException {
        //Dictionary d = new Dictionary("whatevs", "/shared/experiments/mayhew2/lexicons/spa-eng.masterlex.txt.gz");
        Dictionary d = new Dictionary("whatevs", "/home/mayhew/IdeaProjects/ner-annotation/bendict.txt", "testuser");
    }


}
