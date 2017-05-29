package io.github.mayhewsw;


import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Sentence;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import io.github.mayhewsw.utils.SentenceCache;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mayhew2 on 5/10/17.
 */
@SuppressWarnings("ALL")
@Controller
@RequestMapping("/bootstrap")
public class BootstrapController {

    // These are all common objects that don't change user by user.
    private HashMap<String, Properties> datasets;

    private static Logger logger = LoggerFactory.getLogger(BootstrapController.class);

    /**
     * Load config files before anything else. This is the only object shared among user sessions.
     *
     * This only loads config files with the prefix 'bs-' (for bootstrap)
     */
    public BootstrapController() {
        File configfolder = new File("config");

        File[] configfiles = configfolder.listFiles();

        datasets = new HashMap<>();

        for(File f : configfiles){
            if(f.getName().endsWith("~")) continue;
            if(!f.getName().startsWith("bs-")) continue;

            Properties prop = new Properties();

            InputStream input = null;

            try {
                input = new FileInputStream(f);
                // load a properties file
                prop.load(input);

                datasets.put(prop.getProperty("name"), prop);

            }catch(IOException e){

            }
        }
    }

    private static Analyzer analyzer =
            new Analyzer() {
                @Override
                protected TokenStreamComponents createComponents(String fieldName) {
                    Tokenizer source = new WhitespaceTokenizer();
                    TokenStream filter = new ShingleFilter(source);
                    //TokenStream filter2 = new NGramTokenFilter(filter, 1, 4);
                    return new Analyzer.TokenStreamComponents(source, filter);
                }
            };


//    /**
//     * This is done before anything else...
//     * @param sd
//     * @throws IOException
//     */
//    public void buildmemoryindex(SessionData sd) throws IOException {
//
//        // we write to this open file object.
//        RAMDirectory rd = sd.ramDirectory;
//
//        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
//
//        IndexWriter writer = new IndexWriter(rd, cfg);
//
//        for(Constituent sent : sd.allsents.values()){
//            StringReader sr = new StringReader(sent.getTokenizedSurfaceForm());
//
//            Document d = new Document();
//            TextField tf = new TextField("body", sr);
//            d.add(tf);
//            d.add(new StringField("filename", getSentId(sent), Field.Store.YES));
//            writer.addDocument(d);
//        }
//        writer.close();
//
//    }

    /**
     * A little convenience function.
     * @param sent
     * @return
     */
    public static String getSentId(Constituent sent){
        return sent.getTextAnnotation().getId() + ":" + sent.getSentenceId();
    }


    @RequestMapping(value = "/loaddata", method=RequestMethod.GET)
    public String loaddata(@RequestParam(value="dataname") String dataname, HttpSession hs) throws Exception {

        Properties prop = datasets.get(dataname);
        // this refers to a folder containing a large number of conll files.
        String folderpath = prop.getProperty("folderpath");


        // this refers to the index made by lucene (probably of the folder)
        String indexpath = prop.getProperty("indexpath");
        hs.setAttribute("indexpath", indexpath);

        SentenceCache cache = new SentenceCache(folderpath, indexpath);

        SessionData sd = new SessionData(hs);

        // Add terms to the session
        // FIXME: don't add term prefixes.
        HashSet<String> terms = new HashSet<>();
        String[] termarray = prop.getProperty("terms").split(",");
        for(String term : termarray){
            terms.add(term);
        }

        // FIXME: this folder contains entire files, many sentences of which are not annotated. When they are read back in, we will incorrectly mark sentences as annotated.

        // now check the annotation folder to see what this user has already annotated.
        // if there is anything, load it here.
        String outfolder = folderpath.replaceAll("/$","") + "-sentanno-" + sd.username + "/";

        logger.info("Now looking in user annotation folder: " + outfolder);
        HashMap<String, Constituent> annosents = new HashMap<>();

        String sentidsfname = new File(folderpath).getParent() + "/annosents-" + sd.username + ".txt";
        HashSet<String> annosentids = new HashSet<>();
        if(new File(sentidsfname).exists()){
            annosentids.addAll(LineIO.read(sentidsfname));
        }

        if((new File(outfolder)).exists()) {
            CoNLLNerReader cnl = new CoNLLNerReader(outfolder);
            while (cnl.hasNext()) {
                TextAnnotation ta = cnl.next();
                View sents = ta.getView(ViewNames.SENTENCE);

                // this will overwrite whatever was previously there.
                for (Constituent sent : sents.getConstituents()) {
                    String sentid = getSentId(sent);

                    // only keep those sentences that we have annotated.
                    if(!annosentids.contains(sentid)) continue;

                    annosents.put(sentid, sent);

                    // this just to cache the sentence.
                    cache.put(sentid, sent);

                    View ner = ta.getView(ViewNames.NER_CONLL);
                    for(Constituent name : ner.getConstituentsCovering(sent)){
                        terms.add(name.getTokenizedSurfaceForm());
                    };
                }
            }
        }

        hs.setAttribute("cache", cache);
        hs.setAttribute("annosents", annosents);
        hs.setAttribute("terms", terms);

        sd = new SessionData(hs);

        // build groups here.
        HashMap<String, HashSet<String>> groups = new HashMap<>();
        //updategroups(sd.indexpath, terms, cache, sd.annosents, groups);
        updategroups2(sd.indexpath, terms, cache, groups);
        hs.setAttribute("groups", groups);

        // use only if you have want an in-memory index (as opposed to a disk index)
        // it's important to load this again because of all the attributes added to hs.
        //sd = new SessionData(hs);
        //buildmemoryindex(sd);

        String labelsproperty = prop.getProperty("labels");
        List<String> labels = new ArrayList<>();
        List<String> csslines = new ArrayList<String>();
        for(String labelandcolor: labelsproperty.split(" ")){
            String[] sl = labelandcolor.split(":");
            labels.add(sl[0]);
            csslines.add("." + sl[0] + "{ background-color: " + sl[1] + "; }");
        }
        logger.debug("using labels: " + labels.toString());
        LineIO.write("src/main/resources/static/css/labels.css", csslines);

        hs.setAttribute("labels", labels);
        hs.setAttribute("dataname", dataname);
        hs.setAttribute("prop", prop);

        return "redirect:/bootstrap/sents";
    }

    @RequestMapping("/")
    public String home(Model model, HttpSession hs) throws IOException {
        model.addAttribute("datasets", datasets.keySet());
        model.addAttribute("user", new User());

        return "bs-home";
    }

    @RequestMapping(value="/setname")
    public String setname(@ModelAttribute User user, HttpSession hs){
        logger.info("Setting name to: " + user.getName());
        // Just make sure everything is clear first... just in case.
        logger.info("Logging in!");
        hs.removeAttribute("username");
        hs.removeAttribute("dataname");
        hs.removeAttribute("tas");
        hs.removeAttribute("dict");
        hs.removeAttribute("suffixes");
        hs.removeAttribute("prop");

        //hs.setMaxInactiveInterval(10);
        //System.out.println("Setting timeout interval to 10 seconds.");

        hs.setAttribute("username", user.getName());


        return "redirect:/bootstrap/";
    }


    public static void updategroups2(String indexdir, HashSet<String> terms, SentenceCache cache, HashMap<String, HashSet<String>> groups) throws IOException {
        logger.info("Updating groups2... ({})", cache.size());

        // all sentence ids that appear in groups.
        HashSet<String> allgroups = new HashSet<>();

        for(String term : groups.keySet()){
            allgroups.addAll(groups.get(term));
        }

        // actually build groups
        for(String term : terms){
            if(!groups.containsKey(term)){
                int k = 15;
                HashSet<String> group = cache.gatherTopK(term, allgroups, k);
                groups.put(term, group);
            }
        }

        // now resolve groups
        // important to do this after groups is fully built.
        for(String term : groups.keySet()){
            // TODO: consider flipping these and doing an intersection
            for(String sent : groups.get(term)){
                for(String otherterm : groups.keySet()){
                    if(term.equals(otherterm)) continue;

                    HashSet<String> fulllist = cache.getAllResults(otherterm);

                    // if this sentence is also present in the FULL LIST of other term, then add it to the group.
                    if(fulllist != null && fulllist.contains(sent)){
                        groups.get(otherterm).add(sent);
                    }
                }
            }
        }
        logger.info("Done updating groups2... ({})", cache.size());
    }


//    /**
//     * This uses the terms variable inside SessionData object to query the index for matching sentences.
//     * @param sd
//     * @return
//     * @throws IOException
//     */
//    public static void updategroups(String indexdir, HashSet<String> terms, SentenceCache cache, HashMap<String, Constituent> annosents, HashMap<String, HashSet<Constituent>> groups) throws IOException {
//        logger.info("Updating groups... ({})", cache.size());
//
//        // just for run!
//        // FIXME: consider opening this only once and storing as a session variable.
//        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexdir)));
//        IndexSearcher searcher = new IndexSearcher(reader);
//
//        // This contains {query : (sentid, sentid, ...), ...}
//        //HashMap<String, HashSet<String>> allresults = new HashMap<>();
//
//        // FIXME: consider caching query lookups.
//        for(String term : terms) {
//
//            if(cache.getAllResults(term) != null){
//                continue;
//            }
//
//            //Query q = new QueryParser("body", analyzer).parse("\"" + query + "\"*");
//            Query query = new PrefixQuery(new Term("body", term));
//
//            // Assume a large text collection. We want to store EVERY SINGLE INSTANCE.
//            int k = Integer.MAX_VALUE;
//            TopDocs searchresults = searcher.search(query, k);
//            ScoreDoc[] hits = searchresults.scoreDocs;
//
//            HashSet<String> queryids = new HashSet<>();
//
//            for (int i = 0; i < hits.length; ++i) {
//                int luceneId = hits[i].doc;
//                Document d = searcher.doc(luceneId);
//
//                String sentid = d.get("filename");
//                queryids.add(sentid);
//            }
//
//            cache.putQueryResult(term, queryids);
//
//            //allresults.put(term, queryids);
//        }
//
//        // this is the number of elements to display to users.
//        int k = 15;
//
//        for(String term : terms){
//            // Don't update already formed groups!
//            if(groups.containsKey(term)) continue;
//
//            HashSet<String> queryids = cache.getAllResults(term);
//
//            // annosents is ALL sentences with any annotations.
//            HashSet<String> annointersection = new HashSet<>(annosents.keySet());
//            annointersection.retainAll(queryids);
//
//            // now annointersection contains only these sentence ids which are annotated (with something) and also
//            // contain the search term.
//
//            // initialize
//            groups.put(term, new HashSet<Constituent>());
//            HashSet<Constituent> querygroup = groups.get(term);
//
//            for(String sentid : annointersection){
//                // FIXME: understand the connection between annosents and cache.
//                // Is cache always kept up to date with annotated sentences??
//                querygroup.add(annosents.get(sentid));
//                if(querygroup.size() == k) break;
//            }
//
//            if(querygroup.size() >= k){
//                // we are good to go!
//                // no need to augment appropriate groups because these are all already in place.
//                logger.info(term + " :Found all sents in annotated sentences!");
//                continue;
//            }
//
//            if(annosents.size() > 100){
//                logger.info("annosents size is: " + annosents.size() + ", so we don't add any more.");
//                continue;
//            }
//
//            HashSet<String> allintersection = new HashSet<>(cache.keySet());
//            allintersection.removeAll(annointersection);
//            allintersection.retainAll(queryids);
//
//            // now allintersection has cached sentids which are NOT in annosents, but which contain the search term.
//            for(String sentid : allintersection){
//                // NOTE: this should NOT be reading from file. These should all be cached... that's the whole point.
//                Constituent sent = cache.getSentence(sentid);
//                querygroup.add(sent);
//
//                // this is the difficult part.
//                // if sent SHOULD BE in any other group, then add it now.
//                for(String term2 : cache.getAllKeys()){
//                    HashSet<String> termids = cache.getAllResults(term2);;
//                    if(termids.contains(sentid)){
//                        if(!groups.containsKey(term2)){
//                            groups.put(term2, new HashSet<>());
//                        }
//                        logger.debug("group: {} getting new sent: {}", term2, sentid);
//                        groups.get(term2).add(sent);
//                    }
//                }
//                if(querygroup.size() == k) break;
//            }
//
//            if(querygroup.size() >= k){
//                logger.info(term + " : Found all sents in cache!");
//                continue;
//            }
//
//            logger.info(term + " :( had to go to disk.");
//
//            // Sigh. Now we need to go to disk.
//            for(String sentid : cache.getAllResults(term)){
//                // This avoids have discussion forum results (which can be noisy) and huge files.
//                int sentind = Integer.parseInt(sentid.split(":")[1]);
//                if(sentid.contains("_DF_") || sentind > 200){
//                    continue;
//                }
//
//                Constituent sent = cache.getSentence(sentid);
//                querygroup.add(sent);
//
//                // this is the difficult part.
//                // if sent SHOULD BE in any other group, then add it now.
//                for(String term2 : cache.getAllKeys()){
//                    HashSet<String> termids = cache.getAllResults(term2);;
//                    if(termids.contains(sentid)){
//                        if(!groups.containsKey(term2)){
//                            groups.put(term2, new HashSet<>());
//                        }
//                        logger.debug("group: {} getting new sent: {}", term2, sentid);
//                        groups.get(term2).add(sent);
//                    }
//                }
//
//                if(querygroup.size() == k) break;
//            }
//        }
//
//        reader.close();
//
//        logger.info("Done building groups. ({})", cache.size());
//    }

    @RequestMapping(value="/addspan", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public void addspan(@RequestParam(value="label") String label, @RequestParam(value="starttokid") String starttokid, @RequestParam(value="endtokid") String endtokid, @RequestParam(value="groupid") String groupid, @RequestParam(value="sentid") String sentid, HttpSession hs, Model model) throws Exception {

        SessionData sd = new SessionData(hs);

        logger.debug("called addspan with: {}, {}, {}, {}, {}", label, starttokid, endtokid, groupid, sentid);

        HashMap<String, HashSet<String>> groups = sd.groups;
        HashSet<String> group = groups.get(groupid);

        int start = Integer.parseInt(starttokid);
        int end = Integer.parseInt(endtokid);

        String text = null;

        List<Constituent> candidates = new ArrayList<>();

        // This loop finds the sent in question. Would be faster if this was a map?
        for(String groupsentid : group){
            Constituent sent = sd.cache.getSentence(groupsentid);
            if(groupsentid.equals(sentid)){
                TextAnnotation ta = sent.getTextAnnotation();
                View ner = ta.getView(ViewNames.NER_CONLL);

                int sentstart = sent.getStartSpan();

                Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, sentstart + start, sentstart + end);
                candidates.add(newc);

                text = newc.getTokenizedSurfaceForm();

                break;
            }
        }

        logger.debug("Text is: " + text);

        addtext(text, label, groupid, hs, model);
        logger.debug("Done adding spans...");

    }

    @RequestMapping(value="/addtext", method=RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public void addtext(@RequestParam(value="text") String text, @RequestParam(value="label") String label, @RequestParam(value="groupid") String groupid, HttpSession hs, Model model) throws IOException {
        SessionData sd = new SessionData(hs);
        HashMap<String, HashSet<String>> groups = sd.groups;
        HashSet<String> group = groups.get(groupid);
        List<Constituent> candidates = new ArrayList<>();
        for(String groupsentid : group){
            Constituent sent = sd.cache.getSentence(groupsentid);
            logger.debug("addspan:: group {{}) has sent: {}", groupid, getSentId(sent));
            String surf = sent.getTokenizedSurfaceForm();

            if(surf.contains(text)){

                // need to find index of tokens into sentence (could be multiple indices).

                TextAnnotation ta = sent.getTextAnnotation();
                View sents = sent.getView();
                View ner = ta.getView(ViewNames.NER_CONLL);

                int sentstart = sent.getStartSpan();

                Pattern pattern = Pattern.compile("\\b"+text+"[^ ]*\\b", Pattern.CASE_INSENSITIVE);
                // in case you would like to ignore case sensitivity,
                // you could use this statement:
                // Pattern pattern = Pattern.compile("\\s+", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(surf);
                // check all occurance
                while (matcher.find()) {
                    // character offsets need to be converted to token offsets.
                    int startind = StringUtils.countMatches(surf.substring(0, matcher.start()), " ");
                    int endind = startind + text.split(" ").length;

                    Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, sentstart + startind, sentstart + endind);

                    // it may already be there...
                    // TODO: how does this work? I hope it defines equality by content.
                    if(!ner.containsConstituent(newc)) {
                        candidates.add(newc);
                    }
                }
            }
        }

        // This logic taken almost verbatim from AnnotationController.
        for(Constituent cand : candidates){
            View ner = cand.getView();
            IntPair span = cand.getSpan();
            List<Constituent> lc = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());

            // this span is already labeled!
            if (lc.size() > 0) {
                boolean removed = false;
                for (Constituent oldc : lc) {
                    IntPair oldspan = oldc.getSpan();

                    int a = span.getFirst();
                    int b = span.getSecond();
                    int c = oldspan.getFirst();
                    int d = oldspan.getSecond();

                    if(a == c && b >= d){
                        ner.removeConstituent(oldc);
                        removed = true;
                    }else if(a <= c && b == d){
                        ner.removeConstituent(oldc);
                        removed = true;
                    }
                }

                // if we did not remove the constituent on this span, then don't add another one!
                // just skip this span.
                if(!removed){
                    continue;
                }
            }

            // an O label means don't add the constituent.
            if (label.equals("O")) {
                System.err.println("Should never happen: label is O");
            } else{
                ner.addConstituent(cand);
            }
        }
    }

    @RequestMapping(value="/addtextsave", method=RequestMethod.GET)
    public String addtextandsave(@RequestParam(value="text") String text, @RequestParam(value="label") String label, @RequestParam(value="groupid") String groupid, HttpSession hs, Model model) throws IOException {
        addtext(text, label, groupid, hs, model);
        save(groupid, hs, model);

        return "redirect:/bootstrap/sents";
    }

    @RequestMapping(value="/logout")
    public String logout(HttpSession hs){
        logger.info("Logging out...");
//        hs.removeAttribute("username");
//        hs.removeAttribute("dataname");
//        hs.removeAttribute("tas");

        // I think this is preferable.
        hs.invalidate();

        return "redirect:/bootstrap/";
    }

    @RequestMapping(value = "/save", method=RequestMethod.GET)
    @ResponseBody
    public void save(@RequestParam(value="groupid", required=true) String groupid, HttpSession hs, Model model) throws IOException {
        logger.info("Save has been called for group: " + groupid);

        SessionData sd = new SessionData(hs);

        HashMap<String, Constituent> annosents = sd.annosents;

        HashMap<String, HashSet<String>> groups = sd.groups;
        HashSet<String> group = groups.get(groupid);

        HashSet<TextAnnotation> tas = new HashSet<>();
        for(String sentid : group){
            Constituent sent = sd.cache.getSentence(sentid);

            View ner = sent.getTextAnnotation().getView(ViewNames.NER_CONLL);
            for(Constituent name : ner.getConstituentsCovering(sent)){
                String surf = name.getTokenizedSurfaceForm();
                sd.terms.add(surf);
            };
            annosents.put(getSentId(sent), sent);
            tas.add(sent.getTextAnnotation());
        }

        // convert the set (with no duplicates) into a list.
        List<TextAnnotation> talist = new ArrayList<>(tas);

        // write out to
        String username = sd.username;
        String folder = sd.dataname;

        Properties props = datasets.get(folder);
        String folderpath = props.getProperty("folderpath");
        String foldertype = props.getProperty("type");

        LineIO.write(new File(folderpath).getParent() + "/annosents-" + username + ".txt", annosents.keySet());

        if(username != null && folderpath != null) {
            folderpath = folderpath.replaceAll("/$", "");
            String outpath = folderpath + "-sentanno-" + username + "/";
            logger.info("Writing out to: " + outpath);

            CoNLLNerReader.TaToConll(talist, outpath);
        }else{
            logger.error("Output folder is null. Probably because the config file needs a 'folderpath' option.");
        }
    }

    @RequestMapping(value="/sents", method= RequestMethod.GET)
    public String annotation(@RequestParam(value="groupid", required=false) String groupid, Model model, HttpSession hs) throws IOException {
        SessionData sd = new SessionData(hs);

        HashMap<String, HashSet<String>> groups = sd.groups;

        // TODO: this is slow. Does it need to be here?
        //updategroups(sd.indexpath, sd.terms, sd.cache, sd.annosents, groups);
        updategroups2(sd.indexpath, sd.terms, sd.cache, groups);

        if(groupid != null) {
            HashSet<String> sentids = groups.get(groupid);

            HashMap<String, String> id2html = new HashMap<>();
            for (String sentid : sentids) {
                String html = getHTMLfromSent(sd.cache.get(sentid), groupid);
                id2html.put(sentid, html);
            }

            model.addAttribute("groupid", groupid);
            model.addAttribute("id2html", id2html);

        }else{

            HashMap<String, HashSet<String>> annogroups = new HashMap<>();
            HashMap<String, HashSet<String>> unannogroups = new HashMap<>();

            HashMap<String, Integer> unlabeledamount = new HashMap<>();

            for(String groupkey : groups.keySet()){
                HashSet<String> group = groups.get(groupkey);
                // FIXME: assume that groupid is the literal query string for that group (will change when context is also used).

                int numunlabeled = 0;
                for(String sentid : group){
                    Constituent sent = sd.cache.getSentence(sentid);
                    View ner = sent.getTextAnnotation().getView(ViewNames.NER_CONLL);

                    List<Constituent> nercons = ner.getConstituentsCovering(sent);
                    boolean grouplabeledinsentence = false;
                    for(Constituent nercon : nercons){
                        if(nercon.getTokenizedSurfaceForm().contains(groupkey)){
                            grouplabeledinsentence = true;
                            break;
                        }
                    }
                    // by here, I know if sentence is group labeled. If answer is YES, then keep checking sentences.
                    // if answer is NO, then break and put in unannogroups.
                    if(!grouplabeledinsentence){
                        numunlabeled += 1;
                    }
                }

                if(numunlabeled > 0) {
                    unannogroups.put(groupkey, group);
                    unlabeledamount.put(groupkey, numunlabeled);
                }else {
                    annogroups.put(groupkey, group);
                }

            }

            int totaltokens = 1;
            int labeledtokens = 0;
            HashMap<String, Constituent> annosents = sd.annosents;
            for(String sentid : annosents.keySet()){
                Constituent sent = annosents.get(sentid);
                totaltokens += sent.size();
                View ner = sent.getTextAnnotation().getView(ViewNames.NER_CONLL);
                List<Constituent> nercons = ner.getConstituentsCovering(sent);
                for(Constituent nercon : nercons){
                    labeledtokens += nercon.size();
                }
            }

            model.addAttribute("labeledtokens", labeledtokens);
            model.addAttribute("totaltokens", totaltokens);
            model.addAttribute("annosents", annosents);

            model.addAttribute("annogroups", annogroups);
            model.addAttribute("unannogroups", unannogroups);
            model.addAttribute("unlabeledamount", unlabeledamount);

        }

        model.addAttribute("labels", hs.getAttribute("labels"));

        return "bs-group-anno";
    }

    @RequestMapping(value="/removetoken", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String removetoken(@RequestParam(value="sentid") String sentid, @RequestParam(value="tokid") String tokid, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("Sentence with id %s: remove token (id:%s).", sentid, tokid));

        // assume sentid
        SessionData sd = new SessionData(hs);
        Constituent sent = sd.cache.getSentence(sentid);
        TextAnnotation ta = sent.getTextAnnotation();

        int tokint= Integer.parseInt(tokid);
        Pair<Integer, Integer> tokspan = new Pair<>(sent.getStartSpan() + tokint, sent.getStartSpan() + tokint+1);

        View ner = ta.getView(ViewNames.NER_CONLL);
        List<Constituent> lc = ner.getConstituentsCoveringSpan(tokspan.getFirst(), tokspan.getSecond());

        if(lc.size() > 0) {
            Constituent oldc = lc.get(0);

            int origstart = oldc.getStartSpan();
            int origend = oldc.getEndSpan();
            String origlabel = oldc.getLabel();
            ner.removeConstituent(oldc);

            if(origstart != tokspan.getFirst()){
                // this means last token is being changed.
                Constituent newc = new Constituent(origlabel, ViewNames.NER_CONLL, ta, origstart, tokspan.getFirst());
                ner.addConstituent(newc);
            }else if(origend != tokspan.getSecond()){
                // this means first token is being changed.
                Constituent newc = new Constituent(origlabel, ViewNames.NER_CONLL, ta, tokspan.getSecond(), origend);
                ner.addConstituent(newc);
            }
        }

        return getHTMLfromSent(sd.cache.get(sentid));
    }


    @RequestMapping(value="/gethtml", method= RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String gethtml(@RequestParam(value="sentid", required=true) String sentid, Model model, HttpSession hs){
        SessionData sd = new SessionData(hs);

        return getHTMLfromSent(sd.cache.get(sentid));
    }


    public static String getHTMLfromSent(Constituent sent){
        return getHTMLfromSent(sent, "");
    }

    public static String getHTMLfromSent(Constituent sent, String keyword){

        IntPair sentspan = sent.getSpan();

        TextAnnotation ta = sent.getTextAnnotation();

        View ner = ta.getView(ViewNames.NER_CONLL);

        // take just the
        String[] text = Arrays.copyOfRange(ta.getTokenizedText().split(" "), sentspan.getFirst(), sentspan.getSecond());

        // add spans to every word that is not a constituent.
        for(int t = 0; t < text.length; t++){
            String id = getSentId(sent);
            text[t] = "<span class='token pointer' id='tok-" + id + ":" + t + "'>" + text[t] + "</span>";
        }

        List<Constituent> sentner = ner.getConstituentsCoveringSpan(sentspan.getFirst(), sentspan.getSecond());

        for(Constituent c : sentner){

            int start = c.getStartSpan() - sentspan.getFirst();
            int end = c.getEndSpan() - sentspan.getFirst();

            // important to also include 'cons' class, as it is a keyword in the html
            text[start] = String.format("<span class='%s pointer cons' id='cons-%d-%d'>%s", c.getLabel(), start, end, text[start]);
            text[end-1] += "</span>";
        }


        String out = StringUtils.join(text, "&nbsp;");
        return out;
    }

}
