package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.lbjava.classify.TestDiscrete;
import edu.illinois.cs.cogcomp.lbjava.parse.Parser;
import io.github.mayhewsw.classifier.CandParser;
import io.github.mayhewsw.classifier.Candidate;
import io.github.mayhewsw.classifier.Trainer;
import io.github.mayhewsw.classifier.lbjava.GoodCandidate;
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
    public static final String punctuation = "!@#$%^&*()_-+=~`:;<>,./?|\\\"\'‹‹››،[]{}";

    LinkedHashMap<String, Fraction> contexts;
    HashSet<String> notcontexts;

    HashSet<String> names;
    HashSet<String> notnames;
    ArrayList<Candidate> candlist;

    int numpos = 0;
    int numneg = 0;

    HashMap<String, HashMap<String, Double>> contextmap;

    SentenceCache sc;

    Trainer trainer;

    double pmithreshold = 0;
    Pair<Double, Double> abovepmi = new Pair<>(0.,1.);
    Pair<Double, Double> belowpmi = new Pair<>(0.,1.);

    // how many we select to reorder by classifier score
    int limit = 500;

    // how many we show to the user
    int displaylimit = 10;

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
        File prev = new File(fname + ".found");
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


    /**
    * This assumes a list of contexts, and gathers names from contexts, and has the user tag them.
    */
    public boolean getnames() throws IOException {

        // a set of contexts which should be removed.
        HashSet<String> removethese = new HashSet<>();

        HashMap<String, Double> candidatecounts = new HashMap<>();

        for(String context : contexts.keySet()){
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
        }

        // sorted is a hashmap, sorted by frequency counts.
        // this is limited to the top candidates, as defined by field called limit
        LinkedHashMap<String, Double> sorted = candidatecounts.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
            .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));


        boolean needmorecontexts = true;

        if(sorted.size() > 0) {
            Iterator<Map.Entry<String, Double>> iter = sorted.entrySet().iterator();
            Map.Entry<String, Double> entry;

            // get top k of sorted, and resort by score.
            LinkedHashMap<Candidate, Double> topkcands = new LinkedHashMap<>();

            Random r = new Random();

            // this will populate the topkcands map.
            while(iter.hasNext()){
                entry = iter.next();
                Candidate c = new Candidate(entry.getKey(), getcontexts(entry.getKey()));

                double score = 0.0;
                if(trainer.trained && candlist.size() > 20 && numpos > 0 && numneg > 0) {
                    score = trainer.cc.scores(c).get("true");
                }

                // keep all positive scored cands, and reject 70% of the negatives.
                if(score < 0 && r.nextDouble() < 0.1){
                    // this path rejects candidate.
                    continue;
                }else {
                    //namecands.add(c);
                    topkcands.put(c, score);
                }
            }

            // this solves two problems: turn topkcands to a list, and limit to displaylimit size.
            List<Map.Entry<Candidate, Double>> topklist = topkcands.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .limit(displaylimit)
                .collect(Collectors.toList());


            System.out.println("Which of these are names? (input comma-sep ints, as in 1,2,5,8). Empty for none.");
            //System.out.println("Context score: " + contexts.get(context).doubleValue());
            System.out.println("[#] freq. score  pmi");
            int j = 0;
            //for(int k = 0; k < topkcands.size(); k++){
            for(Map.Entry<Candidate, Double> e: topklist){;
                Candidate c = e.getKey();
                String[] skey = c.name.split(" ");
                double pmi = 0;
                if(skey.length == 2){
                    pmi = pmi(skey[0], skey[1]);
                }
                double score = e.getValue();
                double freq = sorted.get(c.name);
                
                System.out.println(String.format("[%d] %5.1f %5.1f %5.1f  %s", j++, freq, score, pmi, c.name));
            }

            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().trim();

            if(input.equals("q") || input.equals("quit") || input.equals("exit")){

                trainer.cc.save();

                System.exit(1);
            }else{
                // empty list means all candidates are wrong.
                HashSet<String> selections = new HashSet<>(Arrays.asList(input.split(",")));

                for (int k = 0; k < topklist.size(); k++) {
                    Map.Entry<Candidate, Double> e = topklist.get(k);
                    Candidate cand = e.getKey();

                    if (selections.contains(k + "")) {
                        names.add(cand.name);
                        cand.isgood = true;
                        numpos++;

                        // if you find any good names in this list, then keep hammering until you find none!
                        needmorecontexts = false;
                    } else {
                        notnames.add(cand.name);
                        cand.isgood = false;
                        numneg++;
                    }
                    candlist.add(cand);
                }

                // update context weight
                //Fraction f = new Fraction(selections.size(), limit);

                //contexts.merge(context.trim(), f,
                //        (oldValue, one) -> new Fraction(oldValue.getNumerator() + one.getNumerator(), oldValue.getDenominator() + one.getDenominator()));
            }
        }

        LineIO.write(path + "entities-"+lang+"." + type + ".found", names);
        //LineIO.write(path + "entities-"+lang+".NOT" + type + ".found", notnames);
        LineIO.write(path + "contexts-" + lang + "." + type + ".found",
                contexts.entrySet()
                .stream()
                .map((entry) -> entry.getKey() + "\t" + entry.getValue())
                .collect(Collectors.toList()));


        trainer.cc.forget();
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

        if(trainer.trained) {
            TestDiscrete tester = TestDiscrete.testDiscrete(trainer.cc, new GoodCandidate(), new CandParser(candlist));
            tester.printPerformance(System.out);
            //logger.debug("Precision: {}, Recall: {}, F1: {}", prf[0], prf[1], prf[2]);
            logger.debug("Num pos: {}, Num neg: {}", numpos, numneg);

            int total = correct + incorrect;
            logger.debug(correct + "/" + total + " are correct. This is: " + (100 * correct / (float) total) + "%");
        }

        return needmorecontexts;
    }

    /**
     * Given a term, return the set of contexts it appears in.
     * @param entity
     */
    public HashMap<String, Double> getcontexts(String entity) throws IOException {

        if(contextmap.containsKey(entity)){
            return contextmap.get(entity);
        }

        //logger.debug("Getting contexts for " + entity);
        HashMap<String, Double> featcounts = new HashMap<>();
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
                    featcounts.merge(both, 1., (oldValue, one) -> oldValue + one);
                }

                if (k == 0) {
                    // use only before
                    featcounts.merge(before, 1., (oldValue, one) -> oldValue + one);
                } else if (k == sentcontexts.length - 1) {
                    // use only after
                    featcounts.merge(after, 1., (oldValue, one) -> oldValue + one);
                } else {
                    // use both before and after.
                    featcounts.merge(before, 1., (oldValue, one) -> oldValue + one);
                    featcounts.merge(after, 1., (oldValue, one) -> oldValue + one);
                }
            }
        }
        //logger.debug("Done getting contexts...");

        contextmap.put(entity, featcounts);

        return featcounts;
    }



    public boolean getcontexts() throws IOException {
        // start with some seed entities.
        HashMap<String, Double> featcounts = new HashMap<>();

        for(String entity : names) {
            HashMap<String, Double> entityfeatcounts = getcontexts(entity);

            for(String feat : entityfeatcounts.keySet()){
                featcounts.merge(feat, entityfeatcounts.get(feat), (oldValue, one) -> oldValue + one);
            }
        }

        LinkedHashMap<String, Double> sorted = featcounts.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        //logger.debug("Context candidates: " + sorted);

        Iterator<Map.Entry<String, Double>> iter = sorted.entrySet().iterator();

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

        boolean needmorecontexts = true;
        while(true) {
            if(needmorecontexts) {
                bt.getcontexts();
            }
            // this has a system.exit in it. :( I know.
            needmorecontexts = bt.getnames();
        }

    }
}
