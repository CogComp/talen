package io.github.mayhewsw;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import edu.illinois.cs.cogcomp.core.utilities.StringUtils;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This contains the main logic of the whole thing.
 */
@SuppressWarnings("ALL")
@Controller
public class AnnotationController {

    private static Logger logger = LoggerFactory.getLogger(AnnotationController.class);

    // These are all common objects that don't change user by user.
    private HashMap<String, Properties> datasets;

    private List<String> labels;
    private final String FOLDERTA = "ta";
    private final String FOLDERCONLL = "conll";

    /**
     * When this class is loaded, it reads a file called config/folders.txt. This is made up
     * of lines formatted as:
     *    name path
     * The name is an identifier, the path is the absolute path to the folder. This
     * folder path must contain TextAnnotations.
     *
     * @throws FileNotFoundException
     */
    public AnnotationController() throws IOException {

        File configfolder = new File("config");

        File[] configfiles = configfolder.listFiles();

        datasets = new HashMap<>();

        for(File f : configfiles){
            System.out.println(f);
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

    /**
     * Important to add folders and user to the model.
     * @param model
     * @return
     */
    @RequestMapping("/")
    public String home(Model model, HttpSession hs){
        model.addAttribute("datasets", datasets.keySet());
        model.addAttribute("user", new User());

        if(hs.getAttribute("dict") == null) {
            hs.setAttribute("dict", new Dictionary());
        }
        return "home";
    }

    /**
     * Given a foldername (first field in folders.txt), this will get the path to that folder (second field
     * in folders.txt) and will read all textannotations from that folder.
     *
     * This returns a TreeMap from integer id to TextAnnotation. These ids are assigned only here -- they do
     * not correspond to the internal TextAnnotation id.
     *
     * @param folder folder identifier
     * @return
     * @throws IOException
     */
    public TreeMap<String, TextAnnotation> loadFolder(String dataname, String username) throws Exception {

        Properties props = datasets.get(dataname);
        String folderurl = props.getProperty("path");
        String foldertype = props.getProperty("type");

        File f = new File(folderurl);

        // This will be ordered by it's keys.
        TreeMap<String, TextAnnotation> ret = new TreeMap<>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                int retval;
                try{
                    retval = Integer.compare(Integer.parseInt(o1), Integer.parseInt(o2));
                }catch(NumberFormatException e){
                    retval = o1.compareTo(o2);
                }
                return retval;
            }
        });

        if(foldertype.equals(FOLDERTA)) {
            String[] files = f.list();
            int limit = Math.min(files.length, 500);
            for (int i = 0; i < limit; i++) {
                String file = files[i];
                TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(folderurl + "/" + file);
                ret.put(file, ta);
            }
        }else if(foldertype.equals(FOLDERCONLL)){
            CoNLLNerReader cnl = new CoNLLNerReader(folderurl);
            while(cnl.hasNext()){
                TextAnnotation ta = cnl.next();
                logger.info("Loading: " + ta.getId());
                ret.put(ta.getId(), ta);
            }
        }

        // now check the annotation folder to see what this user has already annotated.
        // if there is anything, load it here.
        String outfolder = folderurl.replaceAll("/$","") + "-annotation-" + username + "/";

        logger.info("Now looking in user annotation folder: " + outfolder);

        if((new File(outfolder)).exists()) {

            if (foldertype.equals(FOLDERTA)) {
                File outf = new File(outfolder);
                String[] files = outf.list();
                int limit = Math.min(files.length, 300);

                for (int i = 0; i < limit; i++) {
                    String file = files[i];
                    TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(outfolder + "/" + file);
                    ret.put(file, ta);
                }
            } else if (foldertype.equals(FOLDERCONLL)) {
                CoNLLNerReader cnl = new CoNLLNerReader(outfolder);
                while (cnl.hasNext()) {
                    TextAnnotation ta = cnl.next();
                    logger.info("Loading: " + ta.getId());

                    ret.put(ta.getId(), ta);
                }
            }
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
        for(String newtaid : sd.tas.keySet()) {
            updatepatterns(sd.tas.get(newtaid), sd.patterns);
        }
        logger.info("Done updating patterns.");
    }


    public void updatepatterns(TextAnnotation ta, HashMap<String, Integer> patterns){
        View ner = ta.getView(ViewNames.NER_CONLL);
        for(Constituent c : ner.getConstituents()){
            int start = c.getStartSpan();

            if(start > 0) {
                String prevtoken = ta.getToken(start - 1);
                String type = "token-1-" + prevtoken + "-" + c.getLabel();

                if(!patterns.containsKey(type)){
                    patterns.put(type, 0);
                }
                patterns.put(type, patterns.get(type) + 1);
            }
        }
    }


    /**
     * THere must be some way to avoid this...
     * @return
     */
    @RequestMapping(value="/instructions")
    public String intr(){
        return "instructions";
    }

    /**
     * This is called when the user clicks on the language button on the homepage.
     * @param folder
     * @param hs
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/loaddata", method=RequestMethod.GET)
    public String loaddata(@RequestParam(value="dataname") String dataname, HttpSession hs) throws Exception {
        SessionData sd = new SessionData(hs);
        String username = sd.username;

        Properties prop = datasets.get(dataname);
        String folderpath = prop.getProperty("path");

        String labelsproperty = prop.getProperty("labels");
        labels = new ArrayList<>();
        List<String> csslines = new ArrayList<String>();
        for(String labelandcolor: labelsproperty.split(" ")){
            String[] sl = labelandcolor.split(":");
            labels.add(sl[0]);
            csslines.add("." + sl[0] + "{ background-color: " + sl[1] + "; }");
        }
        logger.debug("using labels: " + labels.toString());
        LineIO.write("src/main/resources/static/css/labels.css", csslines);


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

        // check to see if there are dictionary created by the user, in file suffixes-username.txt.
        String folderparent = (new File(folderpath)).getParent();
        File dictfile = new File(folderparent, "dict-" + dataname + "-" + username + ".txt");
        if(dictfile.exists()){
            // open and read
            for(String dictline : LineIO.read(dictfile.getAbsolutePath())){
                String[] kv = dictline.split("\t");
                dict.add(kv[0], kv[1]);
            }
        }else{
            logger.error("COULD NOT FIND DICT FILE: " + dictfile.getAbsolutePath());
        }



        // this ensures that the suffixes item is never null.
        String suffixlist = prop.getProperty("suffixes");
        ArrayList<String> suffixes = new ArrayList<>();
        if(suffixlist != null){
            logger.info("Loading suffixes...");

            for(String suff : suffixlist.split(" ")){
                suffixes.add(suff);
            }
        }

        // check to see if there are suffixes created by the user, in file suffixes-username.txt.
        File suffixfile = new File(folderparent, "suffixes-" + username + ".txt");
        if(suffixfile.exists()){
            // open and read
            String suffixline = LineIO.read(suffixfile.getAbsolutePath()).get(0).trim();
            for(String suff : suffixline.split(" ")){
                suffixes.add(suff);
            }
        }else{
            logger.error("COULD NOT FIND SUFFIX FILE: " + suffixfile.getAbsolutePath());
        }

        suffixes.sort((String s1, String s2)-> s2.length()-s1.length());

        HashMap<String, Integer> patterns = new HashMap<>();

        TreeMap<String, TextAnnotation> tas = loadFolder(dataname, username);

        HashMap<String, Integer> rules = loadallrules(tas);

        // update patterns from all tas.
        for(String taid : tas.keySet()) {
            updatepatterns(tas.get(taid), patterns);
        }

        hs.setAttribute("tas", tas);
        hs.setAttribute("dataname", dataname);
        hs.setAttribute("rules", rules);
        hs.setAttribute("prop", prop);
        hs.setAttribute("patterns", patterns);

        // TODO: rules and patterns should probably be the same thing.

        hs.setAttribute("suffixes", suffixes);


        return "redirect:/annotation";
    }

    /**
     * The rules object is fully defined by the TAs.
     * TODO: this may change if we allow probabilistic context-type rules.
     * @param tas
     * @return
     */
    public HashMap<String, Integer> loadallrules(TreeMap<String, TextAnnotation> tas){
        HashMap<String, Integer> rules = new HashMap<>();

        for(String taid : tas.keySet()){
            TextAnnotation ta = tas.get(taid);
            View ner = ta.getView(ViewNames.NER_CONLL);

            // count each annotation just once per document.
            HashSet<String> set = new HashSet<>();
            for(Constituent c : ner.getConstituents()){
                String rulekey = c.getTokenizedSurfaceForm() + ":::" + c.getLabel();
                set.add(rulekey);
            }

            for(String rulekey : set) {
                if (!rules.containsKey(rulekey)) {
                    rules.put(rulekey, 0);
                }
                rules.put(rulekey, rules.get(rulekey) + 1);
            }
        }
        return rules;
    }


    @RequestMapping(value = "/save", method=RequestMethod.GET)
    public String save(@RequestParam(value="taid", required=true) String taid, HttpSession hs) throws IOException, ParseException {

        SessionData sd = new SessionData(hs);

        // write out to
        String username = sd.username;
        String folder = sd.dataname;

        Properties props = datasets.get(folder);
        String folderpath = props.getProperty("path");
        String foldertype = props.getProperty("type");

        if(username != null && folderpath != null) {

            folderpath = folderpath.replaceAll("/$", "");
            String outpath = folderpath + "-annotation-" + username + "/";
            logger.info("Writing out to: " + outpath);
            logger.info("id is: " + taid);

            TreeMap<String, TextAnnotation> tas = sd.tas;
            TextAnnotation taToSave = tas.get(taid);
            String savepath = outpath + taid;

            if(foldertype.equals(FOLDERTA)) {
                SerializationHelper.serializeTextAnnotationToFile(taToSave, savepath, true);
            }else if(foldertype.equals(FOLDERCONLL)) {
                CoNLLNerReader.TaToConll(Collections.singletonList(taToSave), outpath);
            }

        }

        String indexDir = "/tmp/index";

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
        IndexSearcher searcher = new IndexSearcher(reader);

        HashMap<String, Double> docstosee = new HashMap<>();

        // when the doc is saved, then we want to look at high-weight patterns, and spellings and
        // select a doc with high score.
        // look through rules first.
        for(String rule : sd.rules.keySet()){
            // search for this rule, and if the top 5 docs are all fully annotated, then get the next rule.
            String[] rs = rule.split(":::");
            String text = rs[0];
            String label = rs[1];
            // parse exact matches.
            Query q = new QueryParser("body", analyzer).parse( "\"" + text + "\"");
            System.out.println(q);
            TopScoreDocCollector collector = TopScoreDocCollector.create(5);
            searcher.search(q, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;



            // 4. display results
            System.out.println("Found " + hits.length + " hits.");
            for(int i=0; i<hits.length; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);

                System.out.println((i + 1) + ". " + d.get("filename") + " score=" + hits[i].score);
                String docid = d.get("filename");

                if(!sd.tas.containsKey(docid)) continue;

                TextAnnotation docta = sd.tas.get(docid);
                View ner = docta.getView(ViewNames.NER_CONLL);

                for(IntPair span : docta.getSpansMatching(text)){
                    List<Constituent> cons = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());
                    if(cons.size() == 0){
                        // then annotator needs to see this!

                        double currscore = docstosee.getOrDefault(docid, 0.0);
                        docstosee.put(docid, currscore + hits[i].score);
                        break;
                    }
                }
            }



        }
        System.out.println("Check out these docs:");
        
        Map<String, Double> result = new LinkedHashMap<>();
        docstosee.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEachOrdered(x -> result.put(x.getKey(), x.getValue()));
        System.out.println(result);

        reader.close();


        // nothing happens to this...
        return "redirect:/";
    }

    private static Analyzer analyzer = new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new WhitespaceTokenizer();
            TokenStream filter = new ShingleFilter(source);
            return new TokenStreamComponents(source, filter);
        }
    };

    @RequestMapping(value="/setname")
    public String setname(@ModelAttribute User user, HttpSession hs){
        logger.info("Setting name to: " + user.getName());
        // Just make sure everything is clear first... just in case.
        logger.info("Logging in!");
        hs.removeAttribute("username");
        hs.removeAttribute("dataname");
        hs.removeAttribute("tas");

        //hs.setMaxInactiveInterval(10);
        //System.out.println("Setting timeout interval to 10 seconds.");

        hs.setAttribute("username", user.getName());

        // session variable that controls whethor not to show word definitions.
        hs.setAttribute("showdefs", true);

        return "redirect:/";
    }

    @RequestMapping(value="/logout")
    public String logout(HttpSession hs){
        logger.info("Logging out...");
//        hs.removeAttribute("username");
//        hs.removeAttribute("dataname");
//        hs.removeAttribute("tas");

        // I think this is preferable.
        hs.invalidate();

        return "redirect:/";
    }


    @RequestMapping(value="/annotation", method=RequestMethod.GET)
    public String annotation(@RequestParam(value="taid", required=false) String taid, HttpSession hs, Model model) {

        SessionData sd = new SessionData(hs);

        TreeMap<String, TextAnnotation> tas = sd.tas;
        Dictionary dict = sd.dict;
        HashMap<String, Integer> rules = sd.rules;

        Boolean showdefs = sd.showdefs;

        // Go to the homepage.
        if(tas == null){
            return "redirect:/";
        }

        // If there's no taid, then return the getstarted page (not a redirect).
        if(taid == null){
            List<String> annotatedfiles = new ArrayList<>();

            // Load all annotated files so far.
            String dataname = sd.dataname;
            Properties props = datasets.get(dataname);
            String folderpath = props.getProperty("path");
            String username = sd.username;

            String outfolder = folderpath.replaceAll("/$","") + "-annotation-" + username + "/";

            logger.info("Now looking in user annotation folder: " + outfolder);

            File f = new File(outfolder);
            if(f.exists()) {
                annotatedfiles.addAll(Arrays.asList(f.list()));
            }

            model.addAttribute("annotatedfiles", annotatedfiles);
            return "getstarted";
        }

        if(!tas.containsKey(taid)){
            return "redirect:/annotation";
        }

        TextAnnotation ta = tas.get(taid);

        model.addAttribute("ta", ta);
        model.addAttribute("taid", taid);

        View ner = ta.getView(ViewNames.NER_CONLL);
        View sents = ta.getView(ViewNames.SENTENCE);
        logger.info(String.format("Viewing TextAnnotation (id=%s)", taid));
        logger.info("Text (trunc): " + ta.getTokenizedText().substring(0, Math.min(20, ta.getTokenizedText().length())));
        logger.info("Num Constituents: " + ner.getConstituents().size());
        logger.info("Constituents: " + ner.getConstituents());

        // get the rules that apply
        HashMap<String, Integer> docrules = getdocrules(ta, rules);
        model.addAttribute("docrules", docrules.keySet());

        // set up the html string.
        String out = this.getHTMLfromTA(ta, sd);
        model.addAttribute("htmlstring", out);

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

        model.addAttribute("docwords", docwords.subList(0, Math.min(10, docwords.size())));

        return "annotation";
    }


    /**
     * Given a TA, this returns the HTML string.
     * @param
     * @return
     */
    public static String getHTMLfromTA(TextAnnotation ta, SessionData sd){


        View ner = ta.getView(ViewNames.NER_CONLL);
        View sents = ta.getView(ViewNames.SENTENCE);

        String[] text = ta.getTokenizedText().split(" ");

        ArrayList<String> suffixes = sd.suffixes;

        suffixes.sort((String s1, String s2)-> s2.length()-s1.length());


        // add spans to every word that is not a constituent.
        for(int t = 0; t < text.length; t++){
            String def = null;
            if(sd.dict.containsKey(text[t])){
                def = sd.dict.get(text[t]).get(0);
            }

            for(String suffix : suffixes){
                if(text[t].endsWith(suffix)){
                    //System.out.println(text[t] + " ends with " + suffix);
                    text[t] = text[t].substring(0, text[t].length()-suffix.length()) + "<span class='suffix'>" + suffix + "</span>";
                    break;
                }
            }

            if(sd.showdefs && def != null) {
                text[t] = "<span class='token pointer def' id='tok-" + t + "'>" + def + "</span>";
            }else{
                text[t] = "<span class='token pointer' id='tok-" + t + "'>" + text[t] + "</span>";
            }
        }

        for(Constituent c : ner.getConstituents()){

            int start = c.getStartSpan();
            int end = c.getEndSpan();

            // important to also include 'cons' class, as it is a keyword in the html
            text[start] = String.format("<span class='%s pointer cons' id='cons-%d-%d'>%s", c.getLabel(), start, end, text[start]);
            text[end-1] += "</span>";
        }

        List<Suggestion> suggestions = getdocsuggestions(ta, sd);

        for(Suggestion s : suggestions){

            int start = s.getStartSpan();
            int end = s.getEndSpan();

            // don't suggest spans that cover already tagged areas.
            if(ner.getConstituentsCoveringSpan(start, end).size() > 0) continue;

            // important to also include 'cons' class, as it is a keyword in the html
            text[start] = String.format("<span class='pointer suggestion' data-toggle=\"tooltip\" title='%s' id='cons-%d-%d'>%s", s.reason, start, end, text[start]);
            text[end-1] += "</span>";
        }

        for(Constituent c : sents.getConstituents()){
            int start = c.getStartSpan();
            int end = c.getEndSpan();
            text[start] = "<p>" + text[start];
            text[end-1] += "</p>";
        }

        String out = StringUtils.join(" ", text);
        return out;
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
    public String addspan(@RequestParam(value="label") String label, @RequestParam(value="starttokid") String starttokid, @RequestParam(value="endtokid") String endtokid, @RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: change span %s-%s to label: %s.", idstring, starttokid,endtokid, label));

        int starttokint= Integer.parseInt(starttokid);
        int endtokint = Integer.parseInt(endtokid);

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        HashMap<String, Integer> rules = sd.rules;

        TextAnnotation ta = tas.get(idstring);

        // cannot annotate across sentence boundaries. Return with no changes if this happens.
        View sents = ta.getView(ViewNames.SENTENCE);
        List<Constituent> sentlc = sents.getConstituentsCoveringSpan(starttokint, endtokint);
        if(sentlc.size() != 1){
            String out = this.getHTMLfromTA(ta, sd);
            return out;
        }

        String text = StringUtils.join(" ", ta.getTokensInSpan(starttokint, endtokint));

        // TODO: there's really no reason to add this rule immediately. Add rules when the TA is saved.
        String rulekey = text + ":::" + label;
        if(!rules.containsKey(rulekey)){
            rules.put(rulekey, 0);
        }
        rules.put(rulekey, rules.get(rulekey) + 1);
        logger.debug(rules.toString());

        // spans is either the single span that was entered, or all matching spans.
        List<IntPair> spans;
        boolean propagate = true;
        if(propagate){
            spans = ta.getSpansMatching(text);
        }else{
            spans = new ArrayList<>();
            spans.add(new IntPair(starttokint, endtokint));
        }

        View ner = ta.getView(ViewNames.NER_CONLL);

        for(IntPair span : spans) {
            List<Constituent> lc = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());

            if (lc.size() > 0) {
                for (Constituent oldc : lc) {
                    ner.removeConstituent(oldc);
                }
            }

            // an O label means don't add the constituent.
            if (label.equals("O")) {
                System.err.println("Should never happen: label is O");
            } else {
                Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, span.getFirst(), span.getSecond());
                ner.addConstituent(newc);
            }
        }

        // TODO: remove this because it is slow!!!
        updateallpatterns(sd);

        String out = this.getHTMLfromTA(ta, sd);
        return out;

    }

    @RequestMapping(value="/removetoken", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String removetoken(@RequestParam(value="tokid") String tokid,  @RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: remove token (id:%s).", idstring, tokid));

        int tokint= Integer.parseInt(tokid);
        Pair<Integer, Integer> tokspan = new Pair<>(tokint, tokint+1);

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        Dictionary dict = sd.dict;
        HashMap<String, Integer> rules = sd.rules;

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
        updateallpatterns(sd);

        String out = this.getHTMLfromTA(ta, sd);
        return out;
    }

    @RequestMapping(value="/removeall", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String removeall(@RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        Dictionary dict = sd.dict;
        HashMap<String, Integer> rules = sd.rules;
        TextAnnotation ta = tas.get(idstring);

        Boolean showdefs = sd.showdefs;

        View ner = ta.getView(ViewNames.NER_CONLL);
        //ner.removeAllConsituents();

        for(Constituent c : ner.getConstituents()){
            ner.removeConstituent(c);
        }

        String out = this.getHTMLfromTA(ta, sd);
        return out;
    }


    // not sure how to pass objects between controllers, so will hold off on this for now.

    @RequestMapping(value="/updaterules", method= RequestMethod.GET)
    @ResponseBody
    public HashMap<String, Integer> update(@RequestParam(value="taid") String taid, HttpSession hs, Model model) {
        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        HashMap<String, Integer> rules = sd.rules;
        TextAnnotation ta = tas.get(taid);
        return getdocrules(ta, rules);
    }


    @RequestMapping(value="/toggledefs", method= RequestMethod.GET)
    @ResponseBody
    public String toggledefs(@RequestParam(value="taid") String taid, HttpSession hs) {

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        TextAnnotation ta = tas.get(taid);

        Boolean showdefs = sd.showdefs;
        showdefs = !showdefs;
        hs.setAttribute("showdefs", showdefs);
        sd.showdefs = showdefs;

        return this.getHTMLfromTA(ta, sd);
    }

    @RequestMapping(value="/addsuffix", method= RequestMethod.GET)
    @ResponseBody
    public String addsuffix(@RequestParam(value="suffix") String suffix, @RequestParam(value="taid") String taid, HttpSession hs) {

        SessionData sd = new SessionData(hs);
        Properties prop = datasets.get(sd.dataname);
        String folderpath = prop.getProperty("path");

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



        return this.getHTMLfromTA(ta, sd);
    }


    /**
     * This finds rules which should fire in the current doc.
     * @return
     */
    public HashMap<String, Integer> getdocrules(TextAnnotation ta, HashMap<String, Integer> rules){
        // Find all rules that need to be applied in this document.
        HashMap<String, Integer> docrules = new HashMap<>();
        for(String rule : rules.keySet()){
            String[] rs = rule.split(":::");
            String text = rs[0];
            String label = rs[1];

            View ner = ta.getView(ViewNames.NER_CONLL);

            // if even a single span is not fully labeled, then add the rule.
            boolean addit = false;
            for(IntPair span : ta.getSpansMatching(text)){



                List<Constituent> cons = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());
                // should not have more than one label...
                if(cons.size() > 1){
                    logger.error("text should not have multiple labels...");
                }else if(cons.size() == 1){
                    Constituent c = cons.get(0);

                    // if c has different label, or does not cover span, then add it.

                    IntPair cs = c.getSpan();
                    boolean covering = span.getFirst() <= cs.getFirst() && span.getSecond() >= cs.getSecond();

                    if(!c.getLabel().equals(label) || (!cs.equals(span) && covering)){
                        addit = true;
                    }
                }else{
                    // this means that the span is not labeled.
                    addit = true;
                }

                // important to break because we only want to add the rule once.
                if(addit){
                    docrules.put(rule, rules.get(rule));
                    break;
                }
            }
        }

        return docrules;
    }

    /**
     * Uses the rules and patterns collections to give suggestions inline.
     *
     * @param ta
     * @param rules
     * @return
     */
    public static List<Suggestion> getdocsuggestions(TextAnnotation ta, SessionData sd){
        // Find all rules that need to be applied in this document.
        List<Suggestion> suggestions = new ArrayList<>();
        for(String rule : sd.rules.keySet()){
            String[] rs = rule.split(":::");
            String text = rs[0];
            String label = rs[1];

            View ner = ta.getView(ViewNames.NER_CONLL);

            // if even a single span is not fully labeled, then add the rule.
            boolean addit = false;
            for(IntPair span : ta.getSpansMatching(text)){
                Suggestion s = new Suggestion(span, label, String.format("dataset rule: %s, seen %s times", label, sd.rules.get(rule)));
                suggestions.add(s);
            }
        }

        for(String pattern : sd.patterns.keySet()){
            String[] ps = pattern.split("-");
            String prevtoken = ps[2];
            String label = ps[3];

            int freq = sd.patterns.get(pattern);

            // TODO: this will need to be more sophisticated... should use probability.
            if(freq < 2) continue;

            for(IntPair span : ta.getSpansMatching(prevtoken)){
                IntPair nextspan = new IntPair(span.getFirst()+1, span.getSecond()+1);
                Suggestion s = new Suggestion(nextspan, label, String.format("context rule %s for %s, seen %d times", pattern, label, sd.patterns.get(pattern)));
                suggestions.add(s);
            }
        }



        return suggestions;
    }


    @RequestMapping(value = "/applyrule", method=RequestMethod.GET)
    @ResponseBody
    public String apply(@RequestParam(value="rule") String rule, @RequestParam(value="id") String idstring, HttpSession hs) throws Exception {

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        TextAnnotation ta = tas.get(idstring);

        String[] rs = rule.split(":::");
        String text = rs[0];
        String label = rs[1];

        // spans is either the single span that was entered, or all matching spans.
        List<IntPair> spans = ta.getSpansMatching(text);

        View ner = ta.getView(ViewNames.NER_CONLL);

        for(IntPair span : spans) {
            List<Constituent> lc = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());

            if (lc.size() > 0) {
                for (Constituent oldc : lc) {
                    ner.removeConstituent(oldc);
                }
            }

            // an O label means don't add the constituent.
            if (label.equals("O")) {
                System.err.println("Should never happen: label is O");
            } else {
                Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, span.getFirst(), span.getSecond());
                ner.addConstituent(newc);
            }
        }

        String out = this.getHTMLfromTA(ta, sd);
        return out;
    }

    public static void main(String[] args) throws Exception {
        AnnotationController c = new AnnotationController();

    }


}
