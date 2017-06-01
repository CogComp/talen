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
import java.util.stream.Collectors;

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

            try {
                // we want utf-8.
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF8"));

                // load a properties file
                prop.load(in);

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


    @RequestMapping(value="/search", method=RequestMethod.GET)
    public String search(@RequestParam(value="query", required=true) String query, HttpSession hs, Model model) throws IOException {
        SessionData sd = new SessionData(hs);

        HashSet<String> sents = sd.cache.getAllResults(query);
        // serch only among annotated sentences.
        HashMap<String, HashSet<String>> annosents = sd.annosents;

        // combine all values from annosents into one list.
        HashSet<String> annovalues = new HashSet<>();
        for(Set<String> v : annosents.values()){
            annovalues.addAll(v);
        }

        annovalues.retainAll(sents);

        // this is a special group.
        String html = this.getAllHTML(annovalues, sd);

        model.addAttribute("groupid", "specialgroup-" + query);
        model.addAttribute("html", html);
        model.addAttribute("labels", hs.getAttribute("labels"));
        return "bs-group-anno";
    }


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
        // this refers to a folder containing a large number of unannotated conll files.
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

        String dictpath = prop.getProperty("dictionary");
        Dictionary dict;
        if(dictpath != null){
            logger.info("Loading dictionary: " + dictpath);
            dict = new Dictionary(dataname, dictpath);
            hs.setAttribute("dict", dict);

            // TODO: also load the user dictionary.

        }else{
            logger.info("No dictionary specified.");
            dict = new Dictionary();
        }


        // FIXME: this folder contains entire files, many sentences of which are not annotated. When they are read back in, we will incorrectly mark sentences as annotated.

        // now check the annotation folder to see what this user has already annotated.
        // if there is anything, load it here.
        String outfolder = folderpath.replaceAll("/$","") + "-sentanno-" + sd.username + "/";

        logger.info("Now looking in user annotation folder: " + outfolder);

        // annosents format is: term<tab>sentence
        HashMap<String, HashSet<String>> annosents = new HashMap<>();

        // build groups here.
        HashMap<String, HashSet<String>> groups = new HashMap<>();

        String sentidsfname = new File(folderpath).getParent() + "/annosents-" + sd.username + ".txt";
        if(new File(sentidsfname).exists()){
            List<String> annolines = LineIO.read(sentidsfname);

            for(String annoline : annolines){
                String[] sannoline = annoline.split("\t");
                String term = sannoline[0];
                String[] sentids = sannoline[1].split(",");

                groups.put(term, new HashSet<String>(Arrays.asList(sentids)));
                annosents.put(term, new HashSet<String>(Arrays.asList(sentids)));
            }
        }

        if((new File(outfolder)).exists()) {
            CoNLLNerReader cnl = new CoNLLNerReader(outfolder);
            while (cnl.hasNext()) {
                TextAnnotation ta = cnl.next();
                View sents = ta.getView(ViewNames.SENTENCE);

                // this will overwrite whatever was previously there.
                for (Constituent sent : sents.getConstituents()) {
                    String sentid = getSentId(sent);

                    // this just to cache the sentence.
                    cache.put(sentid, sent);
                    List<Constituent> nercons= sent.getTextAnnotation().getView(ViewNames.NER_CONLL).getConstituentsCovering(sent);
                    for(Constituent nercon : nercons){
                        terms.add(nercon.getTokenizedSurfaceForm());
                    }

                }
            }
        }

        hs.setAttribute("cache", cache);
        hs.setAttribute("annosents", annosents);
        hs.setAttribute("terms", terms);

        sd = new SessionData(hs);


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

        if(hs.getAttribute("dict") == null) {
            hs.setAttribute("dict", new Dictionary());
        }

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
        logger.info("Updating groups2...");

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
        logger.info("Done updating groups2...");
    }


    @RequestMapping(value="/addspan", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public void addspan(@RequestParam(value="label") String label, @RequestParam(value="starttokid") String starttokid, @RequestParam(value="endtokid") String endtokid, @RequestParam(value="groupid") String groupid, @RequestParam(value="sentid") String sentid, @RequestParam(value="sentids[]", required=true) String[] sentids, HttpSession hs, Model model) throws Exception {

        SessionData sd = new SessionData(hs);

        logger.debug("called addspan with: {}, {}, {}, {}, {}", label, starttokid, endtokid, groupid, sentid);

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

    @RequestMapping(value="/addtext", method=RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public void addtext(@RequestParam(value="text") String text, @RequestParam(value="label") String label, @RequestParam(value="sentids[]", required=true) String[] sentids, HttpSession hs, Model model) throws IOException {
        SessionData sd = new SessionData(hs);

        List<Constituent> candidates = new ArrayList<>();
        for(String groupsentid : sentids){
            Constituent sent = sd.cache.getSentence(groupsentid);
            String surf = sent.getTokenizedSurfaceForm();

            if(surf.contains(text)){

                logger.debug("Found sent with id: " + groupsentid);

                // need to find index of tokens into sentence (could be multiple indices).

                TextAnnotation ta = sent.getTextAnnotation();
                View sents = sent.getView();
                View ner = ta.getView(ViewNames.NER_CONLL);

                int sentstart = sent.getStartSpan();

                Pattern pattern = Pattern.compile("\\b"+text+"[^ ]*", Pattern.CASE_INSENSITIVE);
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

        SessionData sd = new SessionData(hs);

        HashMap<String, HashSet<String>> groups = sd.groups;
        HashSet<String> group = groups.get(groupid);
        String[] grouparray = group.toArray(new String[group.size()]);

        addtext(text, label, grouparray, hs, model);

        save(groupid, grouparray, hs, model);

        return "redirect:/bootstrap/sents";
    }

    @RequestMapping(value="/logout")
    public String logout(HttpSession hs){
        logger.info("Logging out...");

        // I think this is preferable.
        hs.invalidate();

        return "redirect:/bootstrap/";
    }

    @RequestMapping(value = "/save", method=RequestMethod.POST)
    @ResponseBody
    public void save(@RequestParam(value="groupid", required=true) String groupid, @RequestParam(value="sentids[]", required=true) String[] sentids, HttpSession hs, Model model) throws IOException {
        logger.info("Save has been called for list: " + sentids);

        SessionData sd = new SessionData(hs);

        HashMap<String, HashSet<String>> annosents = sd.annosents;

        HashSet<String> group = new HashSet<>(Arrays.asList(sentids));

        HashSet<TextAnnotation> tas = new HashSet<>();
        for(String sentid : group){
            Constituent sent = sd.cache.getSentence(sentid);

            View ner = sent.getTextAnnotation().getView(ViewNames.NER_CONLL);
            for(Constituent name : ner.getConstituentsCovering(sent)){
                String surf = name.getTokenizedSurfaceForm();
                sd.terms.add(surf);
            };
            tas.add(sent.getTextAnnotation());
        }

        if(!groupid.startsWith("specialgroup-")) {
            annosents.put(groupid, group);
        }

        // convert the set (with no duplicates) into a list.
        List<TextAnnotation> talist = new ArrayList<>(tas);

        // write out to
        String username = sd.username;
        String folder = sd.dataname;

        Properties props = datasets.get(folder);
        String folderpath = props.getProperty("folderpath");
        String foldertype = props.getProperty("type");


        List<String> annolines = new ArrayList<>();
        for(String term : annosents.keySet()){
            HashSet<String> annogroup = annosents.get(term);
            String annoline = term + "\t" + StringUtils.join(annogroup, ",");
            annolines.add(annoline);
        }

        LineIO.write(new File(folderpath).getParent() + "/annosents-" + username + ".txt", annolines);

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

        if(groupid != null) {
            HashSet<String> sentids = groups.get(groupid);

            String html = this.getAllHTML(sentids, sd);

//            HashMap<String, String> id2html = new HashMap<>();
//            for (String sentid : sentids) {
//                String html = getHTMLfromSent(sd.cache.get(sentid), sd.dict, sd.showdefs);
//                id2html.put(sentid, html);
//            }

            model.addAttribute("groupid", groupid);
            model.addAttribute("html", html);

        }else{

            // TODO: this is slow. Does it need to be here?
            //updategroups(sd.indexpath, sd.terms, sd.cache, sd.annosents, groups);
            updategroups2(sd.indexpath, sd.terms, sd.cache, groups);

            // all sentence ids that appear in groups.
            HashSet<String> allgroups = new HashSet<>();
            for(String term : groups.keySet()){
                allgroups.addAll(groups.get(term));
            }
            int numsentsingroups = allgroups.size();

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

            // set to 1 so we avoid division by 0 errors.
            int totaltokens = 1;
            int labeledtokens = 0;

            HashMap<String, HashSet<String>> annosents = sd.annosents;

            // combine all values from annosents into one list.
            HashSet<String> annovalues = new HashSet<>();
            for(Set<String> v : annosents.values()){
                annovalues.addAll(v);
            }

            for(String sentid : annovalues){
                Constituent sent = sd.cache.get(sentid);
                totaltokens += sent.size();
                View ner = sent.getTextAnnotation().getView(ViewNames.NER_CONLL);
                List<Constituent> nercons = ner.getConstituentsCovering(sent);
                for(Constituent nercon : nercons){
                    labeledtokens += nercon.size();
                }
            }

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

        return getHTMLfromSent(sd.cache.get(sentid), sd.dict, sd.showdefs);
    }


    @RequestMapping(value="/gethtml", method= RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String gethtml(@RequestParam(value="sentids[]", required=true) String[] sentids, Model model, HttpSession hs){
        SessionData sd = new SessionData(hs);
        HashSet<String> sentset = new HashSet<>(Arrays.asList(sentids));
        return getAllHTML(sentset, sd);
    }


    /**
     * This returns one large HTML string for all sentences.
     * @param sentids
     * @param sd
     * @return
     */
    public String getAllHTML(HashSet<String> sentids, SessionData sd){
        HashMap<String, String> id2html = new HashMap<>();

        String ret = "";

        String htmltemplate = "<div class=\"panel panel-default\">" +
                "<div class=\"panel-heading\">%s</div>" +
                "<div class=\"panel-body text\" id=%s>%s</div></div>";

        for (String sentid : sentids) {
            String html = getHTMLfromSent(sd.cache.get(sentid), sd.dict, sd.showdefs);
            //id2html.put(sentid, html);
            ret += String.format(htmltemplate, sentid, sentid, html) + "\n";
        }

        return ret;
    }


    @RequestMapping(value="/toggledefs", method= RequestMethod.GET)
    @ResponseBody
    public String toggledefs(@RequestParam(value="sentids[]") String[] sentids, HttpSession hs) {
        SessionData sd = new SessionData(hs);

        HashSet<String> group = new HashSet<>(Arrays.asList(sentids));

        Boolean showdefs = sd.showdefs;
        showdefs = !showdefs;
        hs.setAttribute("showdefs", showdefs);
        sd.showdefs = showdefs;

        return this.getAllHTML(group, sd);
    }

    /**
     * Given a sentence, produce the HTML for display. .
     * @param sent
     * @param keyword
     * @return
     */
    public static String getHTMLfromSent(Constituent sent, Dictionary dict, boolean showdefs){

        IntPair sentspan = sent.getSpan();

        TextAnnotation ta = sent.getTextAnnotation();

        View ner = ta.getView(ViewNames.NER_CONLL);

        // take just the
        String[] text = Arrays.copyOfRange(ta.getTokenizedText().split(" "), sentspan.getFirst(), sentspan.getSecond());

        // add spans to every word that is not a constituent.
        for(int t = 0; t < text.length; t++){
            String def = null;
            if(dict != null && dict.containsKey(text[t])){
                def = dict.get(text[t]).get(0);
            }

            String id = getSentId(sent);

            if(showdefs && def != null) {
                text[t] = "<span class='token pointer def' id='tok-" + t + "'>" + def + "</span>";
            }else{
                text[t] = "<span class='token pointer' id='tok-" + t + "'>" + text[t] + "</span>";
            }
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
