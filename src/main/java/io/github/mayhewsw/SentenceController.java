package io.github.mayhewsw;


import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import edu.illinois.cs.cogcomp.wikirelation.core.CooccuranceMapLinker;
import io.github.mayhewsw.utils.SentenceCache;
import io.github.mayhewsw.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.print.attribute.HashAttributeSet;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by mayhew2 on 5/10/17.
 */
@SuppressWarnings("ALL")
@Controller
@RequestMapping("/sentence/")
public class SentenceController {

    // These are all common objects that don't change user by user.
    private HashMap<String, Properties> datasets;

    private static Logger logger = LoggerFactory.getLogger(SentenceController.class);

    CooccuranceMapLinker linker;

    /**
     * Load config files before anything else. This is the only object shared among user sessions.
     * <p>
     * This only loads config files with the prefix 'bs-' (for bootstrap)
     */
    public SentenceController() {

        linker= new CooccuranceMapLinker(true);

        File configfolder = new File("config");

        File[] configfiles = configfolder.listFiles();

        datasets = new HashMap<>();

        for (File f : configfiles) {
            if (f.getName().endsWith("~")) continue;
            if (!f.getName().startsWith("bs-")) continue;

            Properties prop = new Properties();

            try {
                // we want utf-8.
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF8"));

                // load a properties file
                prop.load(in);

                datasets.put(prop.getProperty("name"), prop);

            } catch (IOException e) {

            }
        }
    }

    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public String search(@RequestParam(value = "query", required = true) String query, @RequestParam(value = "searchinanno", required = false) String searchinanno, HttpSession hs, Model model) throws IOException {
        SessionData sd = new SessionData(hs);

        boolean annosearch = false;
        if (searchinanno != null) {
            annosearch = true;
        }

        HashSet<String> sents = sd.cache.getAllResults(query);
        // serch only among annotated sentences.
        HashMap<String, Group> groups = sd.groups;

        // combine all values from annosents into one list.
        HashSet<String> annovalues = new HashSet<>();
        for (Group v : groups.values()) {
            annovalues.addAll(v.getAnno());
        }

        ArrayList<String> grouplist = new ArrayList<String>();

        // whether or not to display all, or just the annotated text.
        if (annosearch) {
            annovalues.retainAll(sents);
            grouplist.addAll(annovalues);
        } else {
            grouplist.addAll(sents);

            // restrict the size of the list.
            grouplist = new ArrayList<>(grouplist.subList(0,Math.min(75, grouplist.size())));
        }

        // this is a special group.
        String html = this.getAllHTML(grouplist, query, sd);

        model.addAttribute("groupid", "specialgroup-" + query);
        model.addAttribute("html", html);
        model.addAttribute("labels", hs.getAttribute("labels"));
        model.addAttribute("grouptype", "(unknown)");

        return "bs-group-anno";
    }


    /**
     * A little convenience function.
     *
     * @param sent
     * @return
     */
    public static String getSentId(Constituent sent) {
        return sent.getTextAnnotation().getId() + ":" + sent.getSentenceId();
    }


    @RequestMapping(value = "/loaddata", method = RequestMethod.GET)
    public String loaddata(@RequestParam(value = "dataname") String dataname, HttpSession hs) throws Exception {


        Properties prop = datasets.get(dataname);
        // this refers to a folder containing a large number of unannotated conll files.
        String folderpath = prop.getProperty("folderpath");

        // this refers to the index made by lucene (probably of the folder)
        String indexpath = prop.getProperty("indexpath");
        hs.setAttribute("indexpath", indexpath);

        SentenceCache cache = new SentenceCache(folderpath, indexpath);

        // load the dictionary, graceful fail if not there.
        String dictpath = prop.getProperty("dictionary");
        Dictionary dict;
        if (dictpath != null) {
            logger.info("Loading dictionary: " + dictpath);
            dict = new Dictionary(dataname, dictpath);
            hs.setAttribute("dict", dict);
        } else {
            logger.info("No dictionary specified.");
            dict = new Dictionary();
        }

        // this ensures that the suffixes item is never null.
        String suffixlist = prop.getProperty("suffixes");
        ArrayList<String> suffixes = new ArrayList<>();
        if (suffixlist != null) {
            logger.info("Loading suffixes...");

            for (String suff : suffixlist.split(" ")) {
                suffixes.add(suff);
            }
        }
        hs.setAttribute("suffixes", suffixes);

        SessionData sd = new SessionData(hs);


        // now check the annotation folder to see what this user has already annotated.
        // if there is anything, load it here.
        String outfolder = folderpath.replaceAll("/$", "") + "-sentanno-" + sd.username + "/";

        logger.info("Now looking in user annotation folder: " + outfolder);

        // Contains all TAs, used for updating patterns.
        List<TextAnnotation> talist = new ArrayList<>();

        HashMap<String, Group> groups = new HashMap<>();

        // Load file. Build annosents based on which sentences are annotated.
        if ((new File(outfolder)).exists()) {
            CoNLLNerReader cnl = new CoNLLNerReader(outfolder);
            while (cnl.hasNext()) {
                TextAnnotation ta = cnl.next();
                View sents = ta.getView(ViewNames.SENTENCE);
                talist.add(ta);

                // this will overwrite whatever was previously there.
                for (Constituent sent : sents.getConstituents()) {
                    String sentid = getSentId(sent);

                    // this just to cache the sentence.
                    cache.put(sentid, sent);
                    List<Constituent> nercons = sent.getTextAnnotation().getView(ViewNames.NER_CONLL).getConstituentsCovering(sent);
                    for (Constituent nercon : nercons) {
                        String stemmed = Utils.stem(nercon.getTokenizedSurfaceForm(), sd.suffixes);

                        groups.putIfAbsent(stemmed, new Group());
                        groups.get(stemmed).addAnno(sentid, nercon.getLabel());
                    }
                }
            }
        }

        // Add terms to the session
        HashSet<String> terms = new HashSet<>();
        String[] termarray = prop.getProperty("terms").split(",");
        for (String term : termarray) {
            groups.putIfAbsent(term, new Group());
        }

        hs.setAttribute("cache", cache);

        updategroups(cache, groups);
        hs.setAttribute("groups", groups);

        String labelsproperty = prop.getProperty("labels");
        List<String> labels = new ArrayList<>();
        List<String> csslines = new ArrayList<String>();
        for (String labelandcolor : labelsproperty.split(" ")) {
            String[] sl = labelandcolor.split(":");
            labels.add(sl[0]);
            csslines.add("." + sl[0] + "{ background-color: " + sl[1] + "; }");
        }
        logger.debug("using labels: " + labels.toString());
        LineIO.write("src/main/resources/static/css/labels.css", csslines);

        hs.setAttribute("labels", labels);
        hs.setAttribute("dataname", dataname);
        hs.setAttribute("prop", prop);

        HashMap<String, Set<String>> contexts = new HashMap<>();
        for(String label : labels){
            contexts.put(label, new HashSet<>());
        }
        hs.setAttribute("contexts", contexts);

        HashMap<String, Bootstrap3> bs3map = new HashMap<>();
        for(String label : labels) {
            bs3map.put(label, new Bootstrap3(cache));
        }
        hs.setAttribute("bs3map", bs3map);

        return "redirect:/sentence/sents";
    }

    @RequestMapping("/")
    public String home(Model model, HttpSession hs) throws IOException {
        model.addAttribute("datasets", datasets.keySet());
        model.addAttribute("user", new User());

        if (hs.getAttribute("dict") == null) {
            hs.setAttribute("dict", new Dictionary());
        }

        return "bs-home";
    }

    @RequestMapping(value = "/setname")
    public String setname(@ModelAttribute User user, HttpSession hs) {

        if(user.getName().length() == 0){
            return "redirect:/sentence/";
        }


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


        return "redirect:/sentence/";
    }


    /**
     * Groups must always contain all terms as keys.
     *
     * @param terms
     * @param cache
     * @param groups
     * @throws IOException
     */
    public static void updategroups(SentenceCache cache, HashMap<String, Group> groups) throws IOException {
        logger.info("Updating groups...");

        // all sentence ids that appear in groups.
        HashSet<String> allgroups = new HashSet<>();
        for (String term : groups.keySet()) {
            allgroups.addAll(groups.get(term));
        }

        // actually build groups
        int k = 5;
        for (String term : groups.keySet()) {
            HashSet<String> currgroup = groups.get(term);
            if (currgroup.size() < k) {
                HashSet<String> group = cache.gatherTopK(term, allgroups, k);
                group.removeAll(currgroup);

                while (group.size() + currgroup.size() > k) {
                    String r = group.iterator().next();
                    group.remove(r);
                }

                groups.get(term).addAll(group);
                allgroups.addAll(group);
            }
        }

        // now resolve groups
        // important to do this after groups is fully built.
        for (String term : groups.keySet()) {
            // TODO: consider flipping these and doing an intersection
            for (String sent : groups.get(term)) {
                for (String otherterm : groups.keySet()) {
                    if (term.equals(otherterm)) continue;

                    HashSet<String> fulllist = cache.getAllResults(otherterm);

                    // if this sentence is also present in the FULL LIST of other term, then add it to the group.
                    if (fulllist != null && fulllist.contains(sent)) {
                        groups.get(otherterm).add(sent);
                    }
                }
            }
        }
        logger.info("Done updating groups...");
    }


    /**
     * This method is given a textannotation and some offsets. It looks up the offsets, and finds what the
     * text to be annotated is. It then passes this text to addtext() to actually annotate the TAs.
     *
     * @param label
     * @param starttokid
     * @param endtokid
     * @param sentid
     * @param sentids
     * @param hs
     * @param model
     * @throws Exception
     */
    @RequestMapping(value = "/addspan", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public void addspan(@RequestParam(value = "label") String label, @RequestParam(value = "starttokid") String starttokid, @RequestParam(value = "endtokid") String endtokid, @RequestParam(value = "sentid") String sentid, @RequestParam(value = "sentids[]", required = true) String[] sentids, @RequestParam(value = "propagate", required = false) boolean propagate, HttpSession hs, Model model) throws Exception {

        SessionData sd = new SessionData(hs);

        logger.debug("called addspan with: {}, {}, {}, {}", label, starttokid, endtokid, sentid);

        // if we don't propagate, just restrict the list of sentences to annotate. Hack?
        if(!propagate){
            logger.debug("Not propagating!");
            sentids = new String[1];
            sentids[0] = sentid;
        }

        int start = Integer.parseInt(starttokid);
        int end = Integer.parseInt(endtokid);

        Constituent sent = sd.cache.getSentence(sentid);

        TextAnnotation ta = sent.getTextAnnotation();
        View ner = ta.getView(ViewNames.NER_CONLL);

        int sentstart = sent.getStartSpan();

        Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, sentstart + start, sentstart + end);
        String text = newc.getTokenizedSurfaceForm();

        logger.debug("Text is: " + text);

        addtext(text, label, sentids, hs, model);
        logger.debug("Done adding spans...");
    }

    @RequestMapping(value = "/addtext", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public void addtext(@RequestParam(value = "text") String text, @RequestParam(value = "label") String label, @RequestParam(value = "sentids[]", required = true) String[] sentids, HttpSession hs, Model model) throws IOException {
        SessionData sd = new SessionData(hs);

        List<Constituent> candidates = new ArrayList<>();
        for (String groupsentid : sentids) {
            Constituent sent = sd.cache.getSentence(groupsentid);
            String surf = sent.getTokenizedSurfaceForm();

            if (surf.contains(text)) {

                logger.debug("Found sent with id: " + groupsentid);

                // need to find index of tokens into sentence (could be multiple indices).

                TextAnnotation ta = sent.getTextAnnotation();
                View sents = sent.getView();
                View ner = ta.getView(ViewNames.NER_CONLL);

                int sentstart = sent.getStartSpan();

                Pattern pattern = Pattern.compile("[^ ]*" + text + "[^ ]*", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(surf);
                // check all occurences
                while (matcher.find()) {
                    // character offsets need to be converted to token offsets.
                    int startind = StringUtils.countMatches(surf.substring(0, matcher.start()), " ");
                    int endind = startind + text.split(" ").length;

                    Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, sentstart + startind, sentstart + endind);

                    // it may already be there...
                    // TODO: how does this work? I hope it defines equality by content.
                    if (!ner.containsConstituent(newc)) {
                        candidates.add(newc);
                    }
                }
            }
        }

        // This logic taken almost verbatim from AnnotationController.
        for (Constituent cand : candidates) {
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

                    if (a == c && b >= d) {
                        ner.removeConstituent(oldc);
                        removed = true;
                    } else if (a <= c && b == d) {
                        ner.removeConstituent(oldc);
                        removed = true;
                    }
                }

                // if we did not remove the constituent on this span, then don't add another one!
                // just skip this span.
                if (!removed) {
                    continue;
                }
            }

            // an O label means don't add the constituent.
            if (!label.equals("O")) {
                ner.addConstituent(cand);
            }
        }
    }

    /**
     * A convenience method that combines addtext() and save() into a single call.
     *
     * @param text
     * @param label
     * @param groupid
     * @param hs
     * @param model
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/addtextsave", method = RequestMethod.GET)
    public String addtextandsave(@RequestParam(value = "text") String text, @RequestParam(value = "label") String label, @RequestParam(value = "groupid") String groupid, HttpSession hs, Model model) throws IOException {

        SessionData sd = new SessionData(hs);

        HashMap<String, Group> groups = sd.groups;
        HashSet<String> group = groups.get(groupid);
        String[] grouparray = group.toArray(new String[group.size()]);

        addtext(text, label, grouparray, hs, model);

        save(groupid, grouparray, hs, model);

        return "redirect:/sentence/sents";
    }

    @RequestMapping(value = "/logout")
    public String logout(HttpSession hs) {
        logger.info("Logging out...");

        // I think this is preferable.
        hs.invalidate();

        return "redirect:/sentence/";
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    @ResponseBody
    public void save(@RequestParam(value = "groupid", required = true) String groupid, @RequestParam(value = "sentids[]", required = true) String[] sentids, HttpSession hs, Model model) throws IOException {
        logger.info("Save has been called for list: " + sentids);



        SessionData sd = new SessionData(hs);

        HashSet<String> group = new HashSet<>(Arrays.asList(sentids));
        HashSet<String> annogroup = new HashSet<>();

        HashSet<TextAnnotation> tas = new HashSet<>();
        for (String sentid : group) {
            Constituent sent = sd.cache.getSentence(sentid);

            View ner = sent.getTextAnnotation().getView(ViewNames.NER_CONLL);
            List<Constituent> nerc = ner.getConstituentsCovering(sent);
            for (Constituent name : nerc) {
                String surf = name.getTokenizedSurfaceForm();
                String stemmed = Utils.stem(surf, sd.suffixes);

                // strategy is to put empty hashset in.
                sd.groups.putIfAbsent(stemmed, new Group());
                sd.groups.get(stemmed).addAnno(sentid, name.getLabel());
            }
            ;

            // only save those sentences that have some annotation.
            if (nerc.size() > 0) {
                tas.add(sent.getTextAnnotation());
                annogroup.add(sentid);
            }
        }

        // convert the set (with no duplicates) into a list.
        List<TextAnnotation> talist = new ArrayList<>(tas);

//        //// FIXME: add this functionality back in!
        // get suggestions for each label.

        for(String label : sd.labels) {
            Bootstrap3 bs3 = sd.bs3map.get(label);
            // get the groups by labels.

            Set<String> labelnames = sd.groups.keySet().stream().filter(x -> sd.groups.get(x).maxType().equals(label)).collect(Collectors.toSet());

            LinkedHashMap<String, Double> sortedcontexts = bs3.getcontexts(labelnames);

            // now I have contexts associated with each label.
            Iterator<String> iter = sortedcontexts.keySet().iterator();

            Set<String> labelset = sd.contexts.get(label);
            labelset.clear();

            while(iter.hasNext() && labelset.size() < 10){
                labelset.add(iter.next());
            }
        }

//        LinkedHashMap<String, Double> sortednames = sd.bs3.getnames(sd.groups.keySet(), sd.contexts);
//
//        for(String t : sortednames.keySet()){
//            sd.patterns.put(new Pair<>(t, "PER"), 1.0);
//        }
//
//        sd.bs3.manualclassifier(sortednames, sd.groups.keySet());

        // write out to
        String username = sd.username;
        String folder = sd.dataname;

        Properties props = datasets.get(folder);
        String folderpath = props.getProperty("folderpath");

        if (username != null && folderpath != null) {
            folderpath = folderpath.replaceAll("/$", "");
            String outpath = folderpath + "-sentanno-" + username + "/";
            logger.info("Writing out to: " + outpath);

            CoNLLNerReader.TaToConll(talist, outpath);
        } else {
            logger.error("Output folder is null. Probably because the config file needs a 'folderpath' option.");
        }
    }

    @RequestMapping(value = "/sents", method = RequestMethod.GET)
    public String annotation(@RequestParam(value = "groupid", required = false) String groupid, Model model, HttpSession hs) throws IOException {
        SessionData sd = new SessionData(hs);

        HashMap<String, Group> groups = sd.groups;

        if(groups == null){
            return "redirect:/sentence/";
        }

        // this means we are looking at a specific sentence.
        if (groupid != null) {
            Group sentids = groups.get(groupid);

            String html = this.getAllHTML(new ArrayList<String>(sentids), sd);

            model.addAttribute("groupid", groupid);
            model.addAttribute("grouptype", sentids.maxType());
            model.addAttribute("html", html);

        } else {

            // TODO: this is slow. Does it need to be here?
            updategroups(sd.cache, groups);

            // all sentence ids that appear in groups.
            HashSet<String> allgroups = new HashSet<>();
            for (String term : groups.keySet()) {
                allgroups.addAll(groups.get(term));
            }
            int numsentsingroups = allgroups.size();

            HashMap<String, HashSet<String>> annogroups = new HashMap<>();
            HashMap<String, HashSet<String>> unannogroups = new HashMap<>();

            HashMap<String, Integer> unlabeledamount = new HashMap<>();

            for (String groupkey : groups.keySet()) {
                HashSet<String> group = groups.get(groupkey);
                // FIXME: assume that groupid is the literal query string for that group (will change when context is also used).

                int numunlabeled = 0;
                for (String sentid : group) {
                    Constituent sent = sd.cache.getSentence(sentid);
                    View ner = sent.getTextAnnotation().getView(ViewNames.NER_CONLL);

                    List<Constituent> nercons = ner.getConstituentsCovering(sent);
                    boolean grouplabeledinsentence = false;
                    for (Constituent nercon : nercons) {
                        if (nercon.getTokenizedSurfaceForm().contains(groupkey)) {
                            grouplabeledinsentence = true;
                            break;
                        }
                    }
                    // by here, I know if sentence is group labeled. If answer is YES, then keep checking sentences.
                    // if answer is NO, then break and put in unannogroups.
                    if (!grouplabeledinsentence) {
                        numunlabeled += 1;
                    }
                }

                if (numunlabeled > 0) {
                    unannogroups.put(groupkey, group);
                    unlabeledamount.put(groupkey, numunlabeled);
                } else {
                    annogroups.put(groupkey, group);
                }
            }

            // set to 1 so we avoid division by 0 errors.
            int totaltokens = 1;
            int labeledtokens = 0;

            // combine all values from annosents into one list.
            HashSet<String> annovalues = new HashSet<>();
            for (Group v : groups.values()) {
                annovalues.addAll(v.getAnno());
            }

            for (String sentid : annovalues) {
                Constituent sent = sd.cache.get(sentid);
                totaltokens += sent.size();
                View ner = sent.getTextAnnotation().getView(ViewNames.NER_CONLL);
                List<Constituent> nercons = ner.getConstituentsCovering(sent);
                for (Constituent nercon : nercons) {
                    labeledtokens += nercon.size();
                }
            }

            model.addAttribute("contexts", sd.contexts);
            model.addAttribute("labeledtokens", labeledtokens);
            model.addAttribute("totaltokens", totaltokens);
            model.addAttribute("numannosents", annovalues.size());

            model.addAttribute("annogroups", annogroups);
            model.addAttribute("unannogroups", unannogroups);
            model.addAttribute("unlabeledamount", unlabeledamount);
            model.addAttribute("numsentsingroups", numsentsingroups);
        }

        model.addAttribute("labels", hs.getAttribute("labels"));

        return "bs-group-anno";
    }


    @RequestMapping(value = "/removeterm", method = RequestMethod.GET)
    public String removeterm(@RequestParam(value = "term") String term, HttpSession hs, Model model) {
        SessionData sd = new SessionData(hs);

        sd.groups.remove(term);

        return "redirect:/sentence/sents";
    }

    @RequestMapping(value = "/removetoken", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String removetoken(@RequestParam(value = "sentid") String sentid, @RequestParam(value = "tokid") String tokid, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("Sentence with id %s: remove token (id:%s).", sentid, tokid));

        // assume sentid
        SessionData sd = new SessionData(hs);
        Constituent sent = sd.cache.getSentence(sentid);
        TextAnnotation ta = sent.getTextAnnotation();

        int tokint = Integer.parseInt(tokid);
        Pair<Integer, Integer> tokspan = new Pair<>(sent.getStartSpan() + tokint, sent.getStartSpan() + tokint + 1);

        View ner = ta.getView(ViewNames.NER_CONLL);
        List<Constituent> lc = ner.getConstituentsCoveringSpan(tokspan.getFirst(), tokspan.getSecond());

        if (lc.size() > 0) {
            Constituent oldc = lc.get(0);

            int origstart = oldc.getStartSpan();
            int origend = oldc.getEndSpan();
            String origlabel = oldc.getLabel();
            ner.removeConstituent(oldc);

            if (origstart != tokspan.getFirst()) {
                // this means last token is being changed.
                Constituent newc = new Constituent(origlabel, ViewNames.NER_CONLL, ta, origstart, tokspan.getFirst());
                ner.addConstituent(newc);
            } else if (origend != tokspan.getSecond()) {
                // this means first token is being changed.
                Constituent newc = new Constituent(origlabel, ViewNames.NER_CONLL, ta, tokspan.getSecond(), origend);
                ner.addConstituent(newc);
            }
        }

        String query = "";
        return getHTMLfromSent(sd.cache.get(sentid), query, sd.dict, sd.showdefs);
    }

    @RequestMapping(value = "/gethtml", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String gethtml(@RequestParam(value = "sentids[]", required = true) String[] sentids, String query, Model model, HttpSession hs) throws FileNotFoundException {
        SessionData sd = new SessionData(hs);
        return getAllHTML(Arrays.asList(sentids), query, sd);
    }

    @RequestMapping(value = "/getsuggestions", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String getsuggestions(@RequestParam(value = "sentids[]", required = true) String[] sentids, String query, Model model, HttpSession hs) throws FileNotFoundException {
        SessionData sd = new SessionData(hs);

        Set<String> entities = new HashSet<String>();

        for(String sentid : sentids){
            Constituent sent = sd.cache.getSentence(sentid);
            IntPair sentspan = sent.getSpan();

            TextAnnotation ta = sent.getTextAnnotation();

            View ner = ta.getView(ViewNames.NER_CONLL);
            List<Constituent> cons = ner.getConstituentsCoveringSpan(sentspan.getFirst(), sentspan.getSecond());
            for(Constituent con : cons){
                entities.add(con.getTokenizedSurfaceForm().replace(" ", "_"));
            }
        }

        String[] ents = entities.toArray(new String[entities.size()]);

        String ret = "";
        String[] candIds = linker.getTopKRelatedNETitles(ents, 10);
        for(String cand : candIds){
            ret += cand + ", ";
        }

        System.out.println("Entities are: " + entities);
        System.out.println("Suggestions are: " + ret);

        return ret;
    }


    /**
     * Convenience function with no query.
     *
     * @param sentids
     * @param sd
     * @return
     */
    public String getAllHTML(List<String> sentids, SessionData sd) throws FileNotFoundException {
        return getAllHTML(sentids, "", sd);
    }

    /**
     * This returns one large HTML string for all sentences.
     *
     * @param sentids
     * @param sd
     * @return
     */
    public String getAllHTML(List<String> sentids, String query, SessionData sd) throws FileNotFoundException {
        HashMap<String, String> id2html = new HashMap<>();

        String ret = "";

        String htmltemplate = "<div class=\"panel panel-default\">" +
                "<div class=\"panel-heading\">%s</div>" +
                "<div class=\"panel-body text\" id=%s>%s</div></div>";

        for (String sentid : sentids) {
            String html = getHTMLfromSent(sd.cache.getSentence(sentid), query, sd.dict, sd.showdefs);
            //id2html.put(sentid, html);
            ret += String.format(htmltemplate, sentid, sentid, html) + "\n";
        }

        return ret;
    }


    @RequestMapping(value = "/toggledefs", method = RequestMethod.GET)
    @ResponseBody
    public String toggledefs(@RequestParam(value = "sentids[]") String[] sentids, @RequestParam(value = "query") String query, HttpSession hs) throws FileNotFoundException {
        SessionData sd = new SessionData(hs);

        Boolean showdefs = sd.showdefs;
        showdefs = !showdefs;
        hs.setAttribute("showdefs", showdefs);
        sd.showdefs = showdefs;

        return this.getAllHTML(Arrays.asList(sentids), query, sd);
    }

    /**
     * Given a sentence, produce the HTML for display. .
     *
     * @param sent
     * @param keyword
     * @return
     */
    public static String getHTMLfromSent(Constituent sent, String query, Dictionary dict, boolean showdefs) {

        IntPair sentspan = sent.getSpan();

        TextAnnotation ta = sent.getTextAnnotation();

        View ner = ta.getView(ViewNames.NER_CONLL);

        // take just the
        String[] text = Arrays.copyOfRange(ta.getTokenizedText().split(" "), sentspan.getFirst(), sentspan.getSecond());

        // add spans to every word that is not a constituent.
        for (int t = 0; t < text.length; t++) {
            String def = null;
            if (dict != null && dict.containsKey(text[t])) {
                def = dict.get(text[t]).get(0);
            }

            String id = getSentId(sent);

            if (showdefs && def != null) {
                text[t] = "<span class='token pointer def' id='tok-" + t + "'>" + def + "</span>";
            } else {
                // FIXME: this will only work for single word queries.
                if (query.length() > 0 && text[t].startsWith(query)) {
                    text[t] = "<span class='token pointer emph' id='tok-" + t + "'>" + text[t] + "</span>";
                } else {
                    text[t] = "<span class='token pointer' id='tok-" + t + "'>" + text[t] + "</span>";
                }
            }
        }

        List<Constituent> sentner = ner.getConstituentsCoveringSpan(sentspan.getFirst(), sentspan.getSecond());

        for (Constituent c : sentner) {

            int start = c.getStartSpan() - sentspan.getFirst();
            int end = c.getEndSpan() - sentspan.getFirst();

            // important to also include 'cons' class, as it is a keyword in the html
            text[start] = String.format("<span class='%s pointer cons' id='cons-%d-%d'>%s", c.getLabel(), start, end, text[start]);
            text[end - 1] += "</span>";
        }


        String out = StringUtils.join(text, "&nbsp;");
        return out;
    }

    @RequestMapping(value = "/annotateall", method = RequestMethod.GET)
    public String annotateall(HttpSession hs, Model model) throws IOException {
        SessionData sd = new SessionData(hs);

        HashMap<String, Group> groups = sd.groups;

        for(String groupid : groups.keySet()) {
            HashSet<String> group = groups.get(groupid);
            String[] grouparray = group.toArray(new String[group.size()]);

            addtext(groupid, groups.get(groupid).maxType(), grouparray, hs, model);
            save(groupid, grouparray, hs, model);
        }

        return "redirect:/sentence/sents";
    }
}
