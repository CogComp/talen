package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import io.github.mayhewsw.classifier.Candidate;
import io.github.mayhewsw.classifier.Trainer;
import io.github.mayhewsw.utils.SentenceCache;
import org.apache.commons.math3.fraction.Fraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by mayhew2 on 6/9/17.
 */
public class BootstrapTest {
    private static Logger logger = LoggerFactory.getLogger(BootstrapTest.class);
    private static final String punctuation = "!@#$%^&*()_-+=~`:;<>,./?|\\\"\'‹‹،";

    LinkedHashMap<String, Fraction> contexts;
    HashSet<String> notcontexts;

    HashSet<String> names;
    HashSet<String> notnames;
    ArrayList<Candidate> candlist;

    HashMap<String, HashMap<String, Integer>> contextmap;

    SentenceCache sc;

    Trainer trainer;

    double pmithreshold = 0;
    Pair<Double, Double> abovepmi = new Pair<>(0.,1.);
    Pair<Double, Double> belowpmi = new Pair<>(0.,1.);

    int limit = 10;

    String type = "PER";
    String lang = "ug";
    String path = "/tmp/";

    public BootstrapTest() throws IOException {
        String filedir = "/shared/corpora/ner/eval/column/mono-all-uly";
        String indexdir = "/shared/corpora/ner/eval/column/mono-all-uly-indexsent4";

        //String filedir = "/shared/corpora/corporaWeb/lorelei/data/LDC2016E90_LORELEI_Somali_Representative_Language_Pack_Monolingual_Text_V1.1/data/monolingual_text/zipped/conll/";
        //String indexdir = "/shared/corpora/corporaWeb/lorelei/data/LDC2016E90_LORELEI_Somali_Representative_Language_Pack_Monolingual_Text_V1.1/data/monolingual_text/zipped/conll-indexsent";

        sc = new SentenceCache(filedir, indexdir);

        contexts = new LinkedHashMap<>();
        notcontexts = new HashSet<>();

        String fname = path + "entities-"+lang+"." + type;
        File prev = new File(fname + ".foundGHOIHJLKWEJRLWKEJRLWKEJRLWEKJR");
        if(prev.exists()){
            names = new HashSet<>(LineIO.read(prev.getAbsolutePath()));
        }else{
            names = new HashSet<>(LineIO.read(fname));
        }

        trainer = new Trainer();
        notnames = new HashSet<>(); //LineIO.read(path + "entities-"+lang+".NOT" + type));

        candlist = new ArrayList<>();

        contextmap = new HashMap<>();

    }


    public boolean getnames() throws IOException {

        // a set of contexts which should be removed.
        HashSet<String> removethese = new HashSet<>();

        for(String context : contexts.keySet()){

            HashMap<String, Double> candidatecounts = new HashMap<>();

            String term = context.split("@")[1];

            boolean exact = true;
            HashSet<String> allids;
            if(context.startsWith("both@")){
                String[] terms = term.split("_");
                HashSet<String> all1 = sc.getAllResults(terms[0], exact);
                HashSet<String> all2 = sc.getAllResults(terms[1], exact);
                all1.retainAll(all2);
                allids = all1;
            }else{
                allids = sc.getAllResults(term, exact);
            }

            for (String sentid : allids) {

                String ssent = sc.sentid2text.get(sentid);

                // gather context now.
                String[] sentcontexts = ssent.split(Pattern.quote(term));

                // boolean value for convenience.
                boolean before = context.startsWith("before@");

                if(context.startsWith("both@")){
                    String[] terms = term.split("_");
                    String first = terms[0];
                    String last = terms[1];

                    int i = ssent.indexOf(first) + first.length();
                    int j = ssent.indexOf(last, i);
                    if(i < 0 || j < 0){
                        continue;
                    }

                    String cand = ssent.substring(i, j).trim();

                    if(cand.split(" ").length > 10) continue;

                    if(!names.contains(cand) && !notnames.contains(cand)) {
                        candidatecounts.merge(cand, 1., (oldValue, one) -> oldValue + one);
                    }

                }else {
                    int conind = 0;
                    for (String con : sentcontexts) {
                        String[] scon = con.split(" ");

                        if (before && conind != 0) {
                            // then look at first few tokens.
                            // skip conind==0
                            // i==1 is always empty, I think!
                            String cand = "";
                            for (int i = 1; i < 4; i++) {
                                if (i >= scon.length) break;
                                cand = cand + " " + scon[i];
                                cand = cand.trim();
                                if (names.contains(cand) || notnames.contains(cand)) continue;

                                candidatecounts.merge(cand, 1., (oldValue, one) -> oldValue + one);
                            }
                        }

                        if (!before && conind != sentcontexts.length - 1) {
                            // then look at last few tokens.
                            // skip conind == last
                            String cand = "";
                            for (int i = 0; i < 4; i++) {
                                if (scon.length - 1 - i < 0) break;
                                cand = scon[scon.length - 1 - i] + " " + cand;
                                cand = cand.trim();
                                if (names.contains(cand) || notnames.contains(cand)) continue;

                                candidatecounts.merge(cand, 1., (oldValue, one) -> oldValue + one);

                            }
                        }
                        conind++;
                    }
                }
            }


            // without this... the classifier is useless.
//            if(trainer.trained) {
//                for (String c : candidatecounts.keySet()) {
//                    if(candidatecounts.get(c) > 1) {
//                        Candidate cand = new Candidate(c, getcontexts(c));
//                        candidatecounts.put(c, trainer.cc.scores(cand).get("true"));
//                    }
//                }
//            }

            LinkedHashMap<String, Double> sorted = candidatecounts.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));

            if(sorted.size() > 0) {
                Iterator<Map.Entry<String, Double>> iter = sorted.entrySet().iterator();
                Map.Entry<String, Double> entry;

                Candidate[] namecands = new Candidate[Math.min(sorted.size(), limit)];

                int i = 0;
                // this builds the namedcands structure
                while(iter.hasNext() && i < limit){
                    entry = iter.next();
                    Candidate c = new Candidate(entry.getKey(), getcontexts(entry.getKey()));
                    namecands[i] = c;
                    i++;
                }

                System.out.println("Which of these are names? (input comma-sep ints, as in 1,2,5,8). Empty for none. Context: " + context);
                System.out.println("Context score: " + contexts.get(context).doubleValue());
                System.out.println("[#] freq. score  pmi");
                for(int k = 0; k < namecands.length; k++){
                    Candidate c = namecands[k];
                    String[] skey = c.name.split(" ");
                    double pmi = 0;
                    if(skey.length == 2){
                        pmi = pmi(skey[0], skey[1]);
                    }
                    double score = 0.0;
                    if(trainer.trained) {
                        score = trainer.cc.scores(c).get("true");
                    }

                    System.out.println(String.format("[%d] %5.1f %5.1f %5.1f  %s", k, sorted.get(c.name), score, pmi, c.name));
                }


                Scanner scanner = new Scanner(System.in);
                String input = scanner.nextLine().trim();

                if(input.equals("q") || input.equals("quit") || input.equals("exit")){
                    return false;
                }else{
                    // empty list means all candidates are wrong.
                    HashSet<String> selections = new HashSet<>(Arrays.asList(input.split(",")));

                    for (int k = 0; k < limit; k++) {
                        if(k >= namecands.length) break;
                        Candidate cand = namecands[k];

                        if (selections.contains(k + "")) {
                            names.add(cand.name);
                            cand.isgood = true;
                        } else {
                            notnames.add(cand.name);
                            cand.isgood = false;
                        }
                        candlist.add(cand);
                    }

                    // update context weight
                    Fraction f = new Fraction(selections.size(), limit);

                    contexts.merge(context.trim(), f,
                            (oldValue, one) -> new Fraction(oldValue.getNumerator() + one.getNumerator(), oldValue.getDenominator() + one.getDenominator()));
                }
            }
        }

        LineIO.write(path + "entities-"+lang+"." + type + ".found", names);
        //LineIO.write(path + "entities-"+lang+".NOT" + type + ".found", notnames);

        trainer.trainClassifier(candlist);

        int correct = 0;
        int incorrect = 0;
        for(Candidate c : candlist){
            if((c.isgood + "").equals(trainer.cc.discreteValue(c))){
                correct++;
            }else{
                incorrect++;
            }

        }
        int total = correct + incorrect;
        logger.debug(correct + "/" + total + " are correct. This is: " + (100*correct / (float) total) + "%");

        return true;
    }

    /**
     * Given a term, return the set of contexts it appears in.
     * @param entity
     */
    public HashMap<String, Integer> getcontexts(String entity) throws IOException {

        if(contextmap.containsKey(entity)){
            return contextmap.get(entity);
        }

        logger.debug("Getting contexts for " + entity);
        HashMap<String, Integer> featcounts = new HashMap<>();
        if(entity.length() <= 1){
            return featcounts;
        }

        HashSet<String> allids = sc.getAllResults(entity);
        for (String sentid : allids) {
            String sent = sc.sentid2text.get(sentid);
            // gather context now.
            String[] sentcontexts = sent.split(Pattern.quote(entity));

            for (int k = 0; k < sentcontexts.length; k++) {
                String con = sentcontexts[k];

                // if con is empty, ignore
                if (con.length() == 0) {
                    continue;
                }

                String[] scon = con.split(" ");

                // scon[0] is either empty, or morphology.
                String after;
                if (scon.length > 1) {
                    after = "after@" + scon[1];
                } else {
                    after = "after@<end>";
                }

                if(scon.length == 0) continue;
                String before = "before@" + scon[scon.length - 1];

                if(k > 0){

                    String[] prev = sentcontexts[k-1].split(" ");
                    if(prev.length == 0){
                        continue;
                    }
                    String prevbeforeterm = prev[prev.length - 1];

                    if(prevbeforeterm.length() == 0) continue;

                    String both = "both@" + prevbeforeterm + "_" + after.split("@")[1];
                    featcounts.merge(both, 1, (oldValue, one) -> oldValue + one);
                }

                if (k == 0) {
                    // use only before
                    featcounts.merge(before, 1, (oldValue, one) -> oldValue + one);
                } else if (k == sentcontexts.length - 1) {
                    // use only after
                    featcounts.merge(after, 1, (oldValue, one) -> oldValue + one);
                } else {
                    // use both before and after.
                    featcounts.merge(before, 1, (oldValue, one) -> oldValue + one);
                    featcounts.merge(after, 1, (oldValue, one) -> oldValue + one);
                }
            }
        }
        logger.debug("Done getting contexts...");

        contextmap.put(entity, featcounts);

        return featcounts;
    }



    public boolean getcontexts() throws IOException {
        // start with some seed entities.
        HashMap<String, Integer> featcounts = new HashMap<>();

        for(String entity : names) {
            HashMap<String, Integer> entityfeatcounts = getcontexts(entity);

            for(String feat : entityfeatcounts.keySet()){
                featcounts.merge(feat, entityfeatcounts.get(feat), (oldValue, one) -> oldValue + one);
            }
        }

        LinkedHashMap<String, Integer> sorted = featcounts.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        //logger.debug("Context candidates: " + sorted);

        Iterator<Map.Entry<String, Integer>> iter = sorted.entrySet().iterator();

        String ctx = iter.next().getKey();
        while(contexts.containsKey(ctx) || ctx.split("@")[1].length() < 5 || notcontexts.contains(ctx)){
            ctx = iter.next().getKey();
        }
        logger.debug("Going to add: " + ctx);
        contexts.put(ctx, new Fraction(limit/2, limit));

        // sort contexts here.
        contexts = contexts.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));


        return true;
    }

    public double pmi(String a, String b) throws IOException {
        return Math.log(sc.count(a + " " + b) / (double)(sc.count(a)*sc.count(b)));
    }

    public static void main(String[] args) throws IOException {
        BootstrapTest bt = new BootstrapTest();

        while(true) {
            if(!bt.getcontexts()) break;
            if(!bt.getnames()) break;
        }

    }
}
