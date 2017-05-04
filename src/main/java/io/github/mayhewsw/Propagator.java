package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.codehaus.groovy.runtime.callsite.ConstructorSite;
import org.mapdb.Atomic;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by mayhew2 on 3/13/17.
 */
public class Propagator {

    /**
     * This will save the rules out to file.
     * @param fname
     * @param rules
     * @throws IOException
     */
    public static void saveRules(String fname, HashMap<String, HashMap<String, Integer>> rules) throws IOException {
        ArrayList<String> rulelines = new ArrayList<>();

        ArrayList<String> sortedkeys = new ArrayList<>(rules.keySet());
        Collections.sort(sortedkeys);

        for(String surface : sortedkeys){
            HashMap<String, Integer> labelcounts = rules.get(surface);
            String outs = surface + "\t";
            for(String label : labelcounts.keySet()){
                outs += label + ":" + labelcounts.get(label) + "\t";
            }
            rulelines.add(outs.trim());
        }

        LineIO.write(fname, rulelines);
    }

    /**
     * This induces a set of rules over a corpus.
     * @param infolder
     * @return
     */
    public static HashMap<String, HashMap<String, Integer>> getRules(String infolder){
        CoNLLNerReader cnr = new CoNLLNerReader(infolder);

        // maps from: surface text to {label:count, label:count}
        HashMap<String, HashMap<String, Integer>> rules = new HashMap<>();

        while(cnr.hasNext()){
            TextAnnotation ta = cnr.next();

            View ner = ta.getView(ViewNames.NER_CONLL);


            for(Constituent c : ner.getConstituents()){
                String surface = c.getSurfaceForm();
                String label = c.getLabel();

                HashMap<String, Integer> counts = rules.getOrDefault(surface, new HashMap<>());

                // increment label count.
                int labelcount = counts.getOrDefault(label, 0);
                counts.put(label, labelcount + 1);

                rules.put(surface, counts);
            }

        }
        System.out.println(rules);
        return rules;
    }

    /**
     * This takes a set of rules and applies them to a corpus.
     * @param outfolder
     * @param rules
     * @throws IOException
     */
    public static void applyRules(String infolder, String outfolder, HashMap<String, HashMap<String, Integer>> rules) throws IOException {
        List<TextAnnotation> outtas = new ArrayList<>();

        int added = 0;

        // we want to count the consistency of this corpus
        // so this maps rule to (already applied, needs application).
        HashMap<String, Pair<AtomicInteger, AtomicInteger>> rulecounts = new HashMap<>();
        for(String surface : rules.keySet()){
            rulecounts.put(surface, new Pair<>(new AtomicInteger(0), new AtomicInteger(0)));
        }

        CoNLLNerReader cnr = new CoNLLNerReader(infolder);
        while(cnr.hasNext()){
            TextAnnotation ta = cnr.next();
            View ner = ta.getView(ViewNames.NER_CONLL);

            int i = 1;
            for(String surface : rules.keySet()){



                List<IntPair> spans = ta.getSpansMatching(surface);

                String maxlabel = Collections.max(rules.get(surface).entrySet(), (entry1, entry2) -> entry1.getValue() - entry2.getValue()).getKey();

                // skip rules that show up less than 3 times.
//                if(rules.get(surface).get(maxlabel) < 3) continue;

                for(IntPair span : spans){

                    List<Constituent> others = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());
                    if(others.size() > 1){
                        System.err.println("Should only be one constituent on this string...");
                        for(Constituent other : others){
                            ner.removeConstituent(other);
                            others = new ArrayList<>();
                        }
                    }

                    boolean add = false;

                    // two cases
                    if(others.size() == 0){
                        // apply label, increment unlabeld counter
                        rulecounts.get(surface).getSecond().incrementAndGet();
                        add = true;
                    }else{
                        Constituent other = others.get(0);
                        if(other.getLabel().equals(maxlabel) && other.getSpan().equals(span)){
                            // if exact match to rule, increment labeled counter
                            rulecounts.get(surface).getFirst().incrementAndGet();
                        }else {
                            // remove constituent, apply label, increment unlabeled counter.
                            // if other is wholly contained in span, then remove it and add current.
                            // if current is wholly contained in other, then do nothing.
                            int a = span.getFirst();
                            int b = span.getSecond();
                            int c = other.getStartSpan();
                            int d = other.getEndSpan();

                            if (a <= c && b >= d) {
                                // remove other, add current.
                                ner.removeConstituent(other);
                                add = true;
                                rulecounts.get(surface).getSecond().incrementAndGet();
                            }
                        }

                    }

                    if(add) {
                        Constituent newc = new Constituent(maxlabel, ViewNames.NER_CONLL, ta, span.getFirst(), span.getSecond());
                        ner.addConstituent(newc);
                        added++;
                        System.out.println("Adding: " + surface + " -> " + maxlabel);
                    }
                }
            }
            outtas.add(ta);
        }
        System.out.println(rulecounts);

        int totalspans = 0;
        int unlabeledspans = 0;
        for(String surface : rulecounts.keySet()){
            Pair<AtomicInteger, AtomicInteger> p = rulecounts.get(surface);
            totalspans += p.getFirst().get() + p.getSecond().get();
            unlabeledspans += p.getSecond().get();
        }

        System.out.println("There are " + rules.keySet().size() + " rules.");
        System.out.println("out of " + totalspans + " applicable spans, " + unlabeledspans + " were unlabeled.");


        System.out.println("Added this many: " + added);
        System.out.println("Writing to: " + outfolder);
        CoNLLNerReader.TaToConll(outtas, outfolder);
    }

    public static void main(String[] args) throws IOException {

        // rules are learned from this folder
        String annotator = "bridgel2";
        String lang = "ug";

        String infolder = "/shared/corpora/ner/human/"+lang+"/conll-anno-" + annotator;

        HashMap<String, HashMap<String, Integer>> rules = getRules(infolder);
        saveRules("/shared/corpora/ner/human/"+lang+"/rules-" + annotator, rules);

        // rules are applied to this folder.
        //String outfolder = "/shared/corpora/ner/human/"+lang+"/conll-anno-"+annotator+"-prop";
        String testfolder = "/shared/corpora/ner/lorelei/ug/";
        applyRules(testfolder + "All-stem-clear", testfolder + "All-stem-"+annotator+"-rules", rules);
    }

}
