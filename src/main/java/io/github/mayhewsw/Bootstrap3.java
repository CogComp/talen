package io.github.mayhewsw;

import io.github.mayhewsw.classifier.Candidate;
import io.github.mayhewsw.classifier.Trainer;
import io.github.mayhewsw.utils.SentenceCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * This is a copy of BootstrapTest, in order to avoid massive conflicts next time I merge. :(
 *
 * Created by stephen on 6/21/17.
 */


@SuppressWarnings("ALL")
public class Bootstrap3 {

    private static Logger logger = LoggerFactory.getLogger(Bootstrap3.class);
    private static final String punctuation = "!@#$%^&*()_-+=~`:;<>,./?|\\\"\'‹‹،";

    /**
     * Internal datastructure to store patterns that are not contexts.
     */
    HashSet<String> notcontexts;

    /**
     * Internal datastructure to store phrases that are not names.
     */
    HashSet<String> notnames;

    private ArrayList<Candidate> candlist;

    private HashMap<String, HashMap<String, Double>> contextmap;

    private SentenceCache sc;

    private Trainer trainer;

    int limit = 10;

    /**
     * This code effectively bootstraps lists of names and contexts, with human input.
     *
     * The essential parts of this are: given a list of names, gather a bunch of contexts for them.
     * @throws IOException
     */
//    public Bootstrap3() throws IOException {
//        String filedir = "/shared/corpora/ner/eval/column/mono-all-uly";
//        String indexdir = "/shared/corpora/ner/eval/column/mono-all-uly-indexsent4";

        //String filedir = "/shared/corpora/corporaWeb/lorelei/data/LDC2016E90_LORELEI_Somali_Representative_Language_Pack_Monolingual_Text_V1.1/data/monolingual_text/zipped/conll/";
        //String indexdir = "/shared/corpora/corporaWeb/lorelei/data/LDC2016E90_LORELEI_Somali_Representative_Language_Pack_Monolingual_Text_V1.1/data/monolingual_text/zipped/conll-indexsent";

//        sc = new SentenceCache(filedir, indexdir);
//
//        contexts = new LinkedHashMap<>();
//        notcontexts = new HashSet<>();
//
//        String fname = path + "entities-"+lang+"." + type;
//        File prev = new File(fname + ".foundGHOIHJLKWEJRLWKEJRLWKEJRLWEKJR");
//        if(prev.exists()){
//            names = new HashSet<>(LineIO.read(prev.getAbsolutePath()));
//        }else{
//            names = new HashSet<>(LineIO.read(fname));
//        }
//
//        trainer = new Trainer();
//        notnames = new HashSet<>(); //LineIO.read(path + "entities-"+lang+".NOT" + type));
//
//        candlist = new ArrayList<>();
//
//        contextmap = new HashMap<>();

//    }

    public Bootstrap3(SentenceCache sc){
        this.sc = sc;

        trainer = new Trainer();
        candlist = new ArrayList<>();
        contextmap = new HashMap<>();

        notnames = new HashSet<>();
        notcontexts = new HashSet<>();
    }


    /**
     * This assumes a list of contexts. Otherwise, nothing happens.
     *
     * Returns a hashmap of names, ranked by their frequencies.
     *
     * @return
     * @throws IOException
     */
    public LinkedHashMap<String, Double> getnames(Set<String> names, HashSet<String> contexts) throws IOException {

        HashMap<String, Double> candidatecounts = new HashMap<>();

        for(String context : contexts) {
            String term = context.split("@")[1];

            boolean exact = true;
            HashSet<String> allids;
            if (context.startsWith("both@")) {
                String[] terms = term.split("_");
                HashSet<String> all1 = sc.getAllResults(terms[0], exact);
                HashSet<String> all2 = sc.getAllResults(terms[1], exact);
                all1.retainAll(all2);
                allids = all1;
            } else {
                allids = sc.getAllResults(term, exact);
            }

            for (String sentid : allids) {

                String ssent = sc.sentid2text.get(sentid);

                // gather context now.
                String[] sentcontexts = ssent.split(Pattern.quote(term));

                // boolean value for convenience.
                boolean before = context.startsWith("before@");

                if (context.startsWith("both@")) {
                    String[] terms = term.split("_");
                    String first = terms[0];
                    String last = terms[1];

                    int i = ssent.indexOf(first) + first.length();
                    int j = ssent.indexOf(last, i);
                    if (i < 0 || j < 0) {
                        continue;
                    }

                    String cand = ssent.substring(i, j).trim();

                    if (cand.split(" ").length > 10) continue;

                    if (!names.contains(cand) && !notnames.contains(cand)) {
                        candidatecounts.merge(cand, 1., (oldValue, one) -> oldValue + one);
                    }

                } else {
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

        LinkedHashMap<String, Double> sorted = candidatecounts.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        return sorted;
    }

    public boolean manualclassifier(LinkedHashMap<String, Double> sorted, Set<String> names) throws IOException {
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

        System.out.println("Which of these are names? (input comma-sep ints, as in 1,2,5,8). Empty for none.");
        System.out.println("[#] freq. score ");
        for(int k = 0; k < namecands.length; k++){
            Candidate c = namecands[k];

            double score = 0.0;
            if(trainer.trained) {
                score = trainer.cc.scores(c).get("true");
            }

            System.out.println(String.format("[%d] %5.1f %5.1f  %s", k, sorted.get(c.name), score, c.name));
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
        }

        return true;
    }


    public void trainclassifier(){
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

    }


    /**
     * Given a term, return the set of contexts it appears in.
     * @param entity
     */
    public HashMap<String, Double> getcontexts(String entity) throws IOException {

        if(contextmap.containsKey(entity)){
            return contextmap.get(entity);
        }

        logger.debug("Getting contexts for " + entity);
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

                    if(after.split("@").length > 1) {
                        String both = "both@" + prevbeforeterm + "_" + after.split("@")[1];
                        featcounts.merge(both, 1., (oldValue, one) -> oldValue + one);
                    }
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
        logger.debug("Done getting contexts...");

        contextmap.put(entity, featcounts);

        return featcounts;
    }

//    public LinkedHashMap<String, Double> getcontexts(Set<String> names) throws IOException {
//        Map<String, String> mapnames = names.stream().collect(Collectors.toMap(x -> x, x -> "PER"));
//        return getcontexts(new HashMap<>(mapnames));
//    }

    public LinkedHashMap<String, Double> getcontexts(Set<String> names) throws IOException {
        // start with some seed entities.
        HashMap<String, Double> featcounts = new HashMap<>();

        for(String entity : names) {
            HashMap<String, Double> entityfeatcounts = getcontexts(entity);

            for(String feat : entityfeatcounts.keySet()){
                if(feat.split("@")[1].length() > 1) {
                    featcounts.merge(feat, entityfeatcounts.get(feat), (oldValue, one) -> oldValue + one);
                }
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

        return sorted;
    }

    public void topcontext(LinkedHashMap<String, Double> sorted, HashSet<String> contexts){
        Iterator<Map.Entry<String, Double>> iter = sorted.entrySet().iterator();

        String ctx = iter.next().getKey();
        while(contexts.contains(ctx) || ctx.split("@")[1].length() < 5 || notcontexts.contains(ctx)){
            ctx = iter.next().getKey();
        }
        logger.debug("Going to add: " + ctx);
        contexts.add(ctx);
    }


    public static void main(String[] args) throws IOException {

        String filedir = "data/train-short/";
        String indexdir = "data/train-short-indexsent4/";

        SentenceCache sc = new SentenceCache(filedir,indexdir);

        Bootstrap3 bt = new Bootstrap3(sc);

        HashSet<String> names = new HashSet<>();
        names.add("Pete Sampras");
        names.add("Ronald Reagan");

        HashSet<String> contexts = new HashSet<>();

        while(true) {
            LinkedHashMap<String, Double> sortedcontexts = bt.getcontexts(names);
            bt.topcontext(sortedcontexts, contexts);

            LinkedHashMap<String, Double> sortednames = bt.getnames(names, contexts);
            bt.manualclassifier(sortednames, names);
        }

    }
}

