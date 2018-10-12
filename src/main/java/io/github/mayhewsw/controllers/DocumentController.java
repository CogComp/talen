package io.github.mayhewsw.controllers;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.StringUtils;
import io.github.mayhewsw.*;
import io.github.mayhewsw.Dictionary;
import io.github.mayhewsw.utils.HtmlGenerator;
import io.github.mayhewsw.utils.IO;
import io.github.mayhewsw.utils.Utils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.*;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This contains the main logic of the whole thing.
 */
@SuppressWarnings("ALL")
@Controller
@RequestMapping("/document/")
public class DocumentController {

    private static Logger logger = LoggerFactory.getLogger(DocumentController.class);

    // These are all common objects that don't change user by user.
    //private HashMap<String, Properties> datasets;

    private List<String> labels;

    /**
     * Important to add folders and user to the model.
     * @param model
     * @return
     */
    @RequestMapping("/")
    public String home(Model model, HttpSession hs){
        Common.loadConfig();

        //model.addAttribute("datasets", datasets.keySet());
        model.addAttribute("user", new User());

        return "document/home";
    }

    /**
     * Given a foldername (first field in folders.txt), this will get the path to that folder (second field
     * in folders.txt) and will read all textannotations from that folder.
     *
     * This returns a TreeMap from integer id to TextAnnotation. These ids are assigned only here -- they do
     * not correspond to the internal TextAnnotation id.
     *
     * @param folder folder identifier
     * @param datasets
     * @return
     * @throws IOException
     */
    public TreeMap<String, TextAnnotation> loadFolder(String dataname, String username, HashMap<String, ConfigFile> datasets) throws Exception {

        ConfigFile props = datasets.get(dataname);
        String folderurl = props.getFolderpath();
        String foldertype = props.getFormat();
        if(foldertype == null){
            // default to tajson
            foldertype = "tajson";
            props.setProperty("format", "tajson");
        }

        // This will be ordered by its keys.
        TreeMap<String, TextAnnotation> ret = new TreeMap<>(new KeyComparator());
        TextStatisticsController.resetstats();

        IO.read(foldertype, folderurl, ret);

        for(TextAnnotation ta : ret.values()){
            TextStatisticsController.updateCounts(ta.getTokens());

            if(!ta.hasView(ViewNames.NER_CONLL)){
                View ner = new View(ViewNames.NER_CONLL, "", ta, 1.0);
                ta.addView(ViewNames.NER_CONLL, ner);
            }
        }

        // now check the annotation folder to see what this user has already annotated.
        // if there is anything, load it here.
        String outfolder = folderurl.replaceAll("/$", "") + "-annotation-" + username + "/";

        logger.info("Now looking in user annotation folder: " + outfolder);

        if ((new File(outfolder)).exists()) {
            IO.read(foldertype, outfolder, ret);
        }

        return ret;
    }

    /**
     * Update all the patterns. This is expensive... probably best to not use this.
     * @param sd
     */
    public void updateallpatterns(SessionData sd){
        // update all patterns all the time.
        logger.info("Updating all patterns...");
        sd.patterns.clear();

        // this maps label -> {prevword: count, prevword: count, ...}
        HashMap<String, HashMap<String, Double>> labelcounts = new HashMap<>();

        // Initialize
        for(String label : labels){
            labelcounts.put(label, new HashMap<>());
        }

        HashMap<Pair<String, String>, Double> counts = new HashMap<>();
        HashMap<String, Integer> featcounts = new HashMap<>();

        // loop over all TAs.
        for(String newtaid : sd.tas.keySet()) {
            TextAnnotation ta = sd.tas.get(newtaid);

            // Extract features from this TA. This adds a new view called "feats"
            FeatureExtractor.extract(ta);

            View feats = ta.getView("feats");
            View ner = ta.getView(ViewNames.NER_CONLL);
            for(Constituent f : feats.getConstituents()){
                // All features have exactly the same span as the NER constituent. This may be inefficient.
                List<Constituent> nercs = ner.getConstituentsCoveringSpan(f.getStartSpan(), f.getEndSpan());

                // assume that is length 1
                // (should be by definition?)
                if(nercs.size() > 0) {
                    String label = nercs.get(0).getLabel();

                    // increment the count for this (feature, label) combination.
                    counts.merge(new Pair<>(f.getLabel(), label), 1., (oldValue, one) -> oldValue + one);
                    // increment the count for this feature
                    featcounts.merge(f.getLabel(), 1, (oldValue, one) -> oldValue + one);
                }
            }
        }

        int k = labels.size();
        // these values come directly from collins and singer paper.
        double alpha = 0.1;
        double threshold = 0.95;
        double fullstringthreshold = 0.8;

        for(Pair<String, String> fp : counts.keySet()){
            String feat = fp.getFirst();
            int featoccurrences = featcounts.get(feat);

            double newvalue = (counts.get(fp) + alpha) / (featoccurrences + k*alpha);

            // this allows that full-strings need only appear 2 or 3 times.
            if(feat.startsWith("full-string") && newvalue > fullstringthreshold){
                sd.patterns.put(fp, newvalue);
            }
            else if(newvalue > threshold){
                sd.patterns.put(fp, newvalue);
            }
        }

        logger.info("Done updating patterns.");
    }

    /**
     * This is called when the user clicks on the language button on the homepage.
     * @param folder
     * @param hs
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/loaddata", method=RequestMethod.GET)
    public String loaddata(@RequestParam(value="dataname") String dataname, HttpSession hs, Model model) throws Exception {
        SessionData sd = new SessionData(hs);
        String username = sd.username;

        ConfigFile prop = sd.datasets.get(dataname);
        String folderpath = prop.getFolderpath();

        // No, users should definitely provide labels.
        // If a dataset is unlabeled, you need to say what labels you want.
        String labelsproperty = prop.getLabels();
        labels = new ArrayList<>();
        List<String> csslines = new ArrayList<String>();
        for(String label: labelsproperty.split(" ")){
            label = label.replaceAll(":", "");
            label = label.replaceAll("\\.", "");
            label = label.replaceAll("#", "");

            labels.add(label);

            String color = Utils.getColorOrRandom(label);

            csslines.add("." + label + "{ background-color: " + color + "; }");
        }
        logger.debug("using labels: " + labels.toString());
        LineIO.write("src/main/resources/static/css/labels.css", csslines);


        String dictpath = prop.getProperty("dict");
        Dictionary dict;
        if(dictpath != null){
            logger.info("Loading dictionary: " + dictpath);
            dict = new Dictionary(dataname, dictpath, sd.username);
        }else{
            logger.info("No dictionary specified.");
            dict = new Dictionary(dataname, sd.username);
        }
        hs.setAttribute("dict", dict);


        // this ensures that the suffixes item is never null.
//        String suffixlist = prop.getProperty("suffixes");
//        ArrayList<String> suffixes = new ArrayList<>();
//        if(suffixlist != null){
//            logger.info("Loading suffixes...");
//
//            for(String suff : suffixlist.split(" ")){
//                suffixes.add(suff);
//            }
//        }

        // check to see if there are suffixes created by the user, in file suffixes-username.txt.
//        String folderparent = (new File(folderpath)).getParent();
//        File suffixfile = new File(folderparent, "suffixes-" + username + ".txt");
//        if(suffixfile.exists()){
//            // open and read
//            String suffixline = LineIO.read(suffixfile.getAbsolutePath()).get(0).trim();
//            for(String suff : suffixline.split(" ")){
//                suffixes.add(suff);
//            }
//        }else{
//            logger.error("COULD NOT FIND SUFFIX FILE: " + suffixfile.getAbsolutePath());
//        }

//        suffixes.sort((String s1, String s2)-> s2.length()-s1.length());

        HashMap<Pair<String, String>, Integer> patterns = new HashMap<>();

        String errormsg = null;
        TreeMap<String, TextAnnotation> tas;
        try{
            tas = loadFolder(dataname, username, sd.datasets);
            hs.setAttribute("tas", tas);
        }catch(Exception e){
            e.printStackTrace();
            errormsg = e.getMessage();
        }

        if(errormsg != null){
            model.addAttribute("datasets", sd.datasets.keySet());
            model.addAttribute("user", new User());
            model.addAttribute("errormsg", errormsg);

            // in case you want to add a new one!
            model.addAttribute("config", new ConfigFile());

            return "index";
        }


        String logpath = "logs/";
        String logfile= String.format("%s/%s-%s.log", logpath, dataname,username);
        hs.setAttribute("logfile", logfile);

        logger.info("Writing to logfile: " + logfile);

        hs.setAttribute("ramdirectory", new RAMDirectory());

        hs.setAttribute("dataname", dataname);
        hs.setAttribute("prop", prop);
        hs.setAttribute("patterns", patterns);

//        hs.setAttribute("suffixes", suffixes);

        sd = new SessionData(hs);

        // not sure there is any point to this??
        //updateallpatterns(sd);
        buildmemoryindex(sd);

        return "redirect:/document/annotation/";
    }

    public static void logwrite(String msg, HttpSession hs){
        SessionData sd = new SessionData(hs);
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date();
        String time = dateFormat.format(date);

        msg = time + "\t" + msg;
        try {
            LineIO.append(sd.logfile, msg);
        } catch (IOException e) {
            logger.error("Cannot log to: " + sd.logfile);
            e.printStackTrace();
        }
    }


    @RequestMapping(value = "/save", method=RequestMethod.POST)
    @ResponseBody
    public HashMap<String, Double> save(@RequestParam(value="sentids[]", required=true) String[] sentids, HttpSession hs) throws Exception {

        SessionData sd = new SessionData(hs);

        // write out to
        String username = sd.username;
        String folder = sd.dataname;

        ConfigFile props = sd.datasets.get(folder);
        String folderpath = props.getFolderpath();
        String foldertype = props.getFormat();

        assert(sentids.length == 1);
        String taid = sentids[0];

        if(username != null && folderpath != null) {

            folderpath = folderpath.replaceAll("/$", "");
            String outpath = folderpath + "-annotation-" + username + "/";
            logger.info("Writing out to: " + outpath);
            logger.info("id is: " + taid);

            TreeMap<String, TextAnnotation> tas = sd.tas;
            TextAnnotation taToSave = tas.get(taid);

            IO.save(foldertype, outpath, taToSave);

            //String config = sd.prop.getProperty("nerconfig");
            //Sandbox.TrainAndAnnotate(config, outpath, tas);

        }

        String indexDir = sd.prop.getProperty("indexDir");
        HashMap<String, Double> result = new LinkedHashMap<>();

        // only do this if the property is not null!
        if(indexDir != null) {

            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
            IndexSearcher searcher = new IndexSearcher(reader);

            HashMap<String, Double> docstosee = new HashMap<>();

            // when the doc is saved, then we want to look at high-weight patterns, and spellings and
            // select a doc with high score.

            for (Pair<String, String> featpair : sd.patterns.keySet()) {
                // search for this rule, and if the top 5 docs are all fully annotated, then get the next rule.
                String feat = featpair.getFirst();
                if(!feat.startsWith("full-string")){
                    continue;
                }

                String text = feat.split("=")[1];
                String label = featpair.getSecond();

                // parse exact matches.
                Query q = new QueryParser("body", analyzer).parse("\"" + text + "\"");
                System.out.println(q);
                TopScoreDocCollector collector = TopScoreDocCollector.create(5);
                searcher.search(q, collector);
                ScoreDoc[] hits = collector.topDocs().scoreDocs;

                // 4. display results
                System.out.println("Found " + hits.length + " hits.");
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d = searcher.doc(docId);

                    System.out.println((i + 1) + ". " + d.get("filename") + " score=" + hits[i].score);
                    String docid = d.get("filename");

                    // don't include docs that don't exist.
                    if (!sd.tas.containsKey(docid)) continue;

                    // don't include the current doc.
                    if(docid.equals(taid)) continue;

                    TextAnnotation docta = sd.tas.get(docid);
                    View ner = docta.getView(ViewNames.NER_CONLL);

                    boolean add = true;
                    for(Constituent c : ner.getConstituents()){
                        // there exists at least one labeled form of this text in this document.
                        // therefore: don't look at this document.
                        if(c.getTokenizedSurfaceForm().equals(text)){
                            add = false;
                            break;
                        }
                    }

                    if(add) {
                        double currscore = docstosee.getOrDefault(docid, 0.0);
                        docstosee.put(docid, currscore + hits[i].score);
                    }

                    // TODO: This is very slow, but the functionality is important.
//                    for (IntPair span : docta.getSpansMatching(text)) {
//                        List<Constituent> cons = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());
//                        if (cons.size() == 0) {
//                            // then annotator needs to see this!
//                            double currscore = docstosee.getOrDefault(docid, 0.0);
//                            docstosee.put(docid, currscore + hits[i].score);
//                            break;
//                        }
//                    }
                }
            }

            System.out.println("Check out these docs:");
            docstosee.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(5)
                    .forEachOrdered(x -> result.put(x.getKey(), x.getValue()));
            System.out.println(result);

            reader.close();
        }

        return result;
    }

    private static Analyzer analyzer =
            new Analyzer() {
                @Override
                protected TokenStreamComponents createComponents(String fieldName) {
                    Tokenizer source = new WhitespaceTokenizer();
                    TokenStream filter = new ShingleFilter(source);
                    //TokenStream filter2 = new NGramTokenFilter(filter, 1, 4);
                    return new TokenStreamComponents(source, filter);
                }
            };

    @RequestMapping(value="/setname")
    public String setname(@ModelAttribute User user, HttpSession hs){
        logger.info("Setting name to: " + user.getName());
        // Just make sure everything is clear first... just in case.
        logger.info("Logging in!");
        logwrite("Logging in!", hs);
        hs.removeAttribute("username");
        hs.removeAttribute("dataname");
        hs.removeAttribute("tas");
        hs.removeAttribute("dict");
        hs.removeAttribute("suffixes");
        hs.removeAttribute("prop");

        //hs.setMaxInactiveInterval(10);
        //System.out.println("Setting timeout interval to 10 seconds.");

        hs.setAttribute("username", user.getName());

        // session variable that controls whethor not to show word definitions.
        hs.setAttribute("showdefs", false);

        return "redirect:/";
    }

//    @RequestMapping(value="/logout")
//    public String logout(HttpSession hs){
//        logger.info("Logging out...");
////        hs.removeAttribute("username");
////        hs.removeAttribute("dataname");
////        hs.removeAttribute("tas");
//
//        // I think this is preferable.
//        hs.invalidate();
//
//        return "redirect:/";
//    }

    @RequestMapping(value="/search", method=RequestMethod.GET)
    public String search(@RequestParam(value="query", required=true) String query, HttpSession hs, Model model) throws IOException, ParseException {
        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;

        List<String> annotatedfiles = new ArrayList<>();

        // Load all annotated files so far.
        String dataname = sd.dataname;
        Properties props = sd.datasets.get(dataname);
        String folderpath = props.getProperty("folderpath");
        String username = sd.username;

        String outfolder = folderpath.replaceAll("/$","") + "-annotation-" + username + "/";

        logger.info("Now looking in user annotation folder: " + outfolder);

        File f = new File(outfolder);
        if(f.exists()) {
            annotatedfiles.addAll(Arrays.asList(f.list()));
        }

        System.out.println(annotatedfiles);

        TreeMap<String, TextAnnotation> newtas = filterTA(query, sd);

        model.addAttribute("tamap", newtas);
        model.addAttribute("annotatedfiles", annotatedfiles);

        return "document/getstarted";
    }


    /**
     * This is done before anything else...
     * @param sd
     * @throws IOException
     */
    public void buildmemoryindex(SessionData sd) throws IOException {

        // we write to this open file object.
        RAMDirectory rd = sd.ramDirectory;

        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);

        IndexWriter writer = new IndexWriter(rd, cfg);

        for(String taid : sd.tas.keySet()){
            TextAnnotation ta = sd.tas.get(taid);

            StringReader sr = new StringReader(ta.getTokenizedText());

            Document d = new Document();
            TextField tf = new TextField("body", sr);
            d.add(tf);
            d.add(new StringField("filename", ta.getId(), Field.Store.YES));
            writer.addDocument(d);
        }
        writer.close();

    }

    /**
     * Given a string query and treemap of tas, return only those matching some criteria in the filter.
     * @param s
     * @param sd
     * @return
     */
    public TreeMap<String, TextAnnotation> filterTA(String query, SessionData sd) throws IOException, ParseException {
        TreeMap<String, TextAnnotation> tas = sd.tas;
        TreeMap<String, TextAnnotation> ret = new TreeMap<>(new KeyComparator());

        IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(sd.ramDirectory));

        //Query q = new QueryParser("body", analyzer).parse("\"" + query + "\"*");
        Query q = new PrefixQuery(new Term("body", query));

        TopScoreDocCollector collector = TopScoreDocCollector.create(20);
        searcher.search(q, collector);

        ScoreDoc[] hits = collector.topDocs().scoreDocs;

        for (int i = 0; i < hits.length; ++i) {
            int luceneId = hits[i].doc;
            Document d = searcher.doc(luceneId);

            String docid = d.get("filename");

            TextAnnotation docta = sd.tas.get(docid);
            ret.put(docid, docta);
        }

        return ret;

    }

    @RequestMapping(value="/trysomeunianno", method=RequestMethod.GET)
    public String unifiedannotation(@RequestParam(value="taid", required=false) String taid, HttpSession hs, Model model) throws FileNotFoundException {
        SessionData sd = new SessionData(hs);
        Map.Entry<String, TextAnnotation> entry = sd.tas.firstEntry();
        TextAnnotation ta = entry.getValue();

        String html = HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);

        model.addAttribute("html", html);


        model.addAttribute("labels", labels);

        return "unified-annotation";
    }


    @RequestMapping(value="/annotation", method=RequestMethod.GET)
    public String annotation(@RequestParam(value="taid", required=false) String taid, HttpSession hs, Model model) throws FileNotFoundException {

        SessionData sd = new SessionData(hs);

        TreeMap<String, TextAnnotation> tas = sd.tas;
        io.github.mayhewsw.Dictionary dict = sd.dict;

        // Go to the homepage.
        if(tas == null){
            return "redirect:/document";
        }

        // If there's no taid, then return the getstarted page (not a redirect).
        if(taid == null){
            List<String> annotatedfiles = new ArrayList<>();

            // Load all annotated files so far.
            String dataname = sd.dataname;
            Properties props = sd.datasets.get(dataname);
            String folderpath = props.getProperty("folderpath");
            String username = sd.username;

            String outfolder = folderpath.replaceAll("/$","") + "-annotation-" + username + "/";

            logger.info("Now looking in user annotation folder: " + outfolder);

            File f = new File(outfolder);
            if(f.exists()) {
                annotatedfiles.addAll(Arrays.asList(f.list()));
            }

            model.addAttribute("tamap", sd.tas);
            model.addAttribute("annotatedfiles", annotatedfiles);
            List<String> stats = new ArrayList<>();
            stats.add("Numdocs: " + sd.tas.size());
            stats.add("Num annotated: " + annotatedfiles.size());

            HashSet<String> surfaces = new HashSet<>();
            int totalsurfaces = 0;
            int totaltokens = 0;
            for(TextAnnotation ta : sd.tas.values()){
                totaltokens += ta.getTokens().length;
                List<String> ret = ta.getView(ViewNames.NER_CONLL).getConstituents().stream().map(c -> c.getTokenizedSurfaceForm()).collect(Collectors.toList());
                totalsurfaces += ret.size();
                surfaces.addAll(ret);
            }
            stats.add("Total tokens: "+ totaltokens);
            stats.add("Total entity surfaces: "+ totalsurfaces);
            stats.add("Unique entity surfaces: "+ surfaces.size());


            model.addAttribute("stats", stats);

            return "document/getstarted";
        }

        if(!tas.containsKey(taid)){
            return "redirect:document/annotation";
        }

        logwrite(String.format("Viewing page with taid: %s", taid), hs);

        TextAnnotation ta = tas.get(taid);

        model.addAttribute("ta", ta);

        View ner = ta.getView(ViewNames.NER_CONLL);
        View sents = ta.getView(ViewNames.SENTENCE);

        // set up the html string.
        String out = HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);
        model.addAttribute("html", out);

        if(!tas.firstKey().equals(taid)) {
            model.addAttribute("previd", tas.lowerKey(taid));
        }else{
            model.addAttribute("previd", -1);
        }

        if(!tas.lastKey().equals(taid)) {
            model.addAttribute("nextid", tas.higherKey(taid));
        }else{
            model.addAttribute("nextid", -1);
        }

        model.addAttribute("labels", labels);

        HashMap<String, Integer> freqs = new HashMap<>();
        for(String word : ta.getTokens()){
            // ignore punctuation, and single length words.
            if(word.length() <= 1) continue;
            if(!freqs.containsKey(word)){
                freqs.put(word, 0);
            }
            freqs.put(word, freqs.get(word) + 1);
        }

        List<String> docwords = new ArrayList<>();

        freqs.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEachOrdered(x -> docwords.add(x.getKey()));

        model.addAttribute("docwords", docwords.subList(0, Math.min(50, docwords.size())));

        String parallelpath = sd.prop.getProperty("parallelpath");
        if(parallelpath!= null){
            // assume path looks like: ti-XXXX.conll
            String parid = taid.split("-")[1].split("\\.")[0];

            String file = LineIO.slurp(parallelpath + "/en-" + parid);
            System.out.println("Trying to read: " + parallelpath + "/en-" + parid);

            model.addAttribute("engtext", file);
        }

        return "document/annotation";
    }

    /**
     * This should never get label O
     * @param label
     * @param spanid
     * @param idstring
     * @param hs
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping(value="/addspan", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public void addspan(@RequestParam(value="label") String label, @RequestParam(value="starttokid") String starttokid, @RequestParam(value="endtokid") String endtokid, @RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: change span %s-%s to label: %s.", idstring, starttokid,endtokid, label));

        int starttokint= Integer.parseInt(starttokid);
        int endtokint = Integer.parseInt(endtokid);

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;

        TextAnnotation ta = tas.get(idstring);

        // cannot annotate across sentence boundaries. Return with no changes if this happens.
        if(ta.hasView(ViewNames.SENTENCE)){
            View sents = ta.getView(ViewNames.SENTENCE);
            List<Constituent> sentlc = sents.getConstituentsCoveringSpan(starttokint, endtokint);
            if(sentlc.size() != 1){
                String out = HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);
                System.out.println("Cannot annotate across sentences");
                return;
            }
        }

        String text = StringUtils.join(" ", ta.getTokensInSpan(starttokint, endtokint));

        // spans is either the single span that was entered, or all matching spans.
        List<IntPair> spans = new ArrayList<>();
        boolean propagate = true;
        if(propagate){
            spans = ta.getSpansMatching(text);
        }

        if(spans.size() == 0 || !propagate){
            spans = new ArrayList<>();
            spans.add(new IntPair(starttokint, endtokint));
        }

        View ner = ta.getView(ViewNames.NER_CONLL);

        
        for(IntPair span : spans) {
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
                Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, span.getFirst(), span.getSecond());
                ner.addConstituent(newc);
                logwrite(String.format("%s span (%s-%s) to label: %s", idstring, span.getFirst(),span.getSecond(), label), hs);
            }
        }

        // TODO: remove this because it is slow!!!
        //updateallpatterns(sd);

        //String out = HtmlGenerator.getHTMLfromTA(ta, sd.showdefs);
        //return out;
    }

    @RequestMapping(value="/removetoken", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String removetoken(@RequestParam(value="tokid") String tokid,  @RequestParam(value="sentid") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: remove token (id:%s).", idstring, tokid));
        logwrite(String.format("%s tokenid (%s) remove label", idstring, tokid), hs);

        int tokint= Integer.parseInt(tokid);
        Pair<Integer, Integer> tokspan = new Pair<>(tokint, tokint+1);

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        io.github.mayhewsw.Dictionary dict = sd.dict;

        Boolean showdefs = sd.showdefs;


        TextAnnotation ta = tas.get(idstring);

        String[] spantoks = ta.getTokensInSpan(tokspan.getFirst(), tokspan.getSecond());
        String text = StringUtils.join(" ", spantoks);

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

        // TODO: remove this because it is slow!!!
        //updateallpatterns(sd);

        String out = HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);
        return out;
    }

    @RequestMapping(value="/removeall", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String removeall(@RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        io.github.mayhewsw.Dictionary dict = sd.dict;
        TextAnnotation ta = tas.get(idstring);

        Boolean showdefs = sd.showdefs;

        View ner = ta.getView(ViewNames.NER_CONLL);
        //ner.removeAllConsituents();

        for(Constituent c : ner.getConstituents()){
            ner.removeConstituent(c);
        }

        String out = HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);
        return out;
    }

    @RequestMapping(value="/toggledefs", method= RequestMethod.GET)
    @ResponseBody
    public String toggledefs(@RequestParam(value="idlist[]") String[] idlist, HttpSession hs) {

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        TextAnnotation ta = tas.get(idlist[0]);

        Boolean showdefs = sd.showdefs;
        showdefs = !showdefs;
        hs.setAttribute("showdefs", showdefs);
        sd.showdefs = showdefs;

        return HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);
    }

    @RequestMapping(value="/togglerom", method= RequestMethod.GET)
    @ResponseBody
    public String toggleroman(@RequestParam(value="idlist[]") String[] idlist, HttpSession hs) {

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        TextAnnotation ta = tas.get(idlist[0]);

        Boolean showroman = sd.showroman;
        showroman = !showroman;
        hs.setAttribute("showroman", showroman);
        sd.showroman = showroman;

        return HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);
    }

    @RequestMapping(value="/togglecopy", method= RequestMethod.GET)
    @ResponseBody
    public String allowcopy(@RequestParam(value="idlist[]") String[] idlist, HttpSession hs) {

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        TextAnnotation ta = tas.get(idlist[0]);

        Boolean allowcopy = sd.allowcopy;
        allowcopy = !allowcopy;
        hs.setAttribute("allowcopy", allowcopy);
        sd.allowcopy = allowcopy;

        String ret;
        if(allowcopy) {
            ret = HtmlGenerator.getCopyableHTMLFromTA(ta, sd.dict, sd.showdefs, sd.showroman);
        }else{
            ret = HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);
        }

        return ret;
    }


    @RequestMapping(value="/addsuffix", method= RequestMethod.GET)
    @ResponseBody
    public String addsuffix(@RequestParam(value="suffix") String suffix, @RequestParam(value="taid") String taid, HttpSession hs) {

        SessionData sd = new SessionData(hs);
        Properties prop = sd.datasets.get(sd.dataname);
        String folderpath = prop.getProperty("folderpath");

        TreeMap<String, TextAnnotation> tas = sd.tas;
        TextAnnotation ta = tas.get(taid);

        logger.info(sd.suffixes.toString());
        logger.info(suffix);

        // in case the user starts the string with a dash
        if(suffix.startsWith("-")){
            suffix = suffix.substring(1);
        }

        // if it's not there, add it, and save it.
        if(!sd.suffixes.contains(suffix)) {
            sd.suffixes.add(suffix);
            // sort it
            sd.suffixes.sort((String s1, String s2)-> s2.length()-s1.length());

            // write it out to file. Don't care if the file is clobbered...
            String folderparent = (new File(folderpath)).getParent();
            File suffixfile = new File(folderparent, "suffixes-" + sd.username + ".txt");

            try {
                LineIO.write(suffixfile.getAbsolutePath(), Collections.singletonList(StringUtils.join(" ", sd.suffixes)));
            } catch (IOException e) {
                logger.error("Could not save suffix file: " + suffixfile.getAbsolutePath());
            }

        }

        return HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);
    }


    /**
     * Uses the patterns collections to give suggestions inline.
     *
     * @param ta
     * @return
     */
    public static List<Suggestion> getdocsuggestions(TextAnnotation ta, SessionData sd){

        List<Suggestion> suggestions = new ArrayList<>();

        List<Suggestion> contextsuggestions = new ArrayList<>();
        if(sd.patterns != null) {
            contextsuggestions = FeatureExtractor.findfeatfires(ta, sd.patterns);
            suggestions.addAll(contextsuggestions);
        }

        return suggestions;
    }

    @RequestMapping(value = "/gethtml", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String gethtml(@RequestParam(value = "sentids[]", required = true) String[] sentids, String query, Model model, HttpSession hs) throws FileNotFoundException {
        SessionData sd = new SessionData(hs);

        String ret = "";
        for(String sentid : sentids){
            TextAnnotation ta = sd.tas.get(sentid);
            String html = HtmlGenerator.getHTMLfromTA(ta, sd.dict, sd.showdefs, sd.showroman);
            ret += html + "\n";
        }

        return ret;
    }


    public static void main(String[] args) throws Exception {
        DocumentController c = new DocumentController();

    }


}
