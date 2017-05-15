package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.*;
import java.util.*;

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

    public BootstrapController() {
        File configfolder = new File("config");

        File[] configfiles = configfolder.listFiles();

        datasets = new HashMap<>();

        for(File f : configfiles){
            if(f.getName().endsWith("~")) continue;
            if(!f.getName().startsWith("bs-")) continue;

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


    /**
     * This is done before anything else...
     * @param sd
     * @param allsents
     * @throws IOException
     */
    public void buildmemoryindex(SessionData sd) throws IOException {

        // we write to this open file object.
        RAMDirectory rd = sd.ramDirectory;

        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);

        IndexWriter writer = new IndexWriter(rd, cfg);

        for(Constituent sent : sd.allsents.values()){
            StringReader sr = new StringReader(sent.getTokenizedSurfaceForm());

            Document d = new Document();
            TextField tf = new TextField("body", sr);
            System.out.println(tf);
            d.add(tf);
            d.add(new StringField("filename", getSentId(sent), Field.Store.YES));
            writer.addDocument(d);
        }
        writer.close();

    }

    public static String getSentId(Constituent sent){
        return sent.getTextAnnotation().getId() + ":" + sent.getSentenceId();
    }


    @RequestMapping(value = "/loaddata", method=RequestMethod.GET)
    public String loaddata(@RequestParam(value="dataname") String dataname, HttpSession hs) throws Exception {

        hs.setAttribute("ramdirectory", new RAMDirectory());



        Properties prop = datasets.get(dataname);
        String folderpath = prop.getProperty("path");

        // Add allsents to the session
        HashMap<String, Constituent> allsents = new HashMap<>();
        CoNLLNerReader cnl = new CoNLLNerReader(folderpath);

        while(cnl.hasNext()) {
            TextAnnotation ta = cnl.next();
            View sents = ta.getView(ViewNames.SENTENCE);

            for (Constituent sent : sents.getConstituents()) {
                allsents.put(getSentId(sent), sent);
            }
        }
        hs.setAttribute("allsents", allsents);

        // Add terms to the session
        HashSet<String> terms = new HashSet<>();
        String[] termarray = prop.getProperty("terms").split(",");
        for(String term : termarray){
            terms.add(term);
        }
        hs.setAttribute("terms", terms);


        SessionData sd = new SessionData(hs);
        buildmemoryindex(sd);


        // TODO: abstract to config.
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



    public static HashMap<String, List<Constituent>> buildgroups(SessionData sd) throws IOException {
        // just for run!
        System.out.println("Using ramdirectory: " + sd.ramDirectory);
        IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(sd.ramDirectory));

        HashMap<String, List<Constituent>> groups = new HashMap<>();

        for(String query : sd.terms) {

            //Query q = new QueryParser("body", analyzer).parse("\"" + query + "\"*");
            Query q = new PrefixQuery(new Term("body", query));
            System.out.println("query is " + q);

            // I actually want all...
            TopScoreDocCollector collector = TopScoreDocCollector.create(2000000);
            searcher.search(q, collector);

            ScoreDoc[] hits = collector.topDocs().scoreDocs;

            List<Constituent> querygroup = new ArrayList<>();

            System.out.println("Found " + hits.length + " hits.");
            for (int i = 0; i < hits.length; ++i) {
                int luceneId = hits[i].doc;
                Document d = searcher.doc(luceneId);

                String sentid = d.get("filename");
                Constituent sent = sd.allsents.get(sentid);
                querygroup.add(sent);
            }

            groups.put(query, querygroup);
        }

        return groups;
    }

    @RequestMapping(value="/addspan", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String addspan(@RequestParam(value="label") String label, @RequestParam(value="starttokid") String starttokid, @RequestParam(value="endtokid") String endtokid, @RequestParam(value="groupid") String groupid, @RequestParam(value="sentid") String sentid, HttpSession hs, Model model) throws Exception {

        SessionData sd = new SessionData(hs);

        HashMap<String, List<Constituent>> groups = (HashMap<String, List<Constituent>>) hs.getAttribute("groups");
        List<Constituent> group = groups.get(groupid);

        int start = Integer.parseInt(starttokid);
        int end = Integer.parseInt(endtokid);

        String text = null;

        // TODO: inefficient b/c two loops
        for(Constituent sent : group){
            if(getSentId(sent).equals(sentid)){
                TextAnnotation ta = sent.getTextAnnotation();
                View ner = ta.getView(ViewNames.NER_CONLL);

                int sentstart = sent.getStartSpan();

                Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, sentstart + start, sentstart + end);
                ner.addConstituent(newc);

                text = newc.getTokenizedSurfaceForm();

                break;
            }
        }

        for(Constituent sent : group){
            String surf = sent.getTokenizedSurfaceForm();
            if(surf.contains(text)){

                // need to find index of tokens into sentence (could be multiple indices).

                TextAnnotation ta = sent.getTextAnnotation();
                View sents = sent.getView();
                View ner = ta.getView(ViewNames.NER_CONLL);

                int sentstart = sent.getStartSpan();

                int i = 0;
                int ind;
                while((ind = surf.indexOf(text, i)) != -1){

                    // ind is a character offset into the surface string.
                    // I want to convert this to a token offset.
                    int startind = StringUtils.countMatches(surf.substring(0, ind), " ");
                    int endind = startind + text.split(" ").length;

                    Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, sentstart + startind, sentstart + endind);

                    // it may already be there...
                    // TODO: how does this work? I hope it defines equality by content.
                    if(!ner.containsConstituent(newc)) {
                        ner.addConstituent(newc);
                    }

                    i += text.length() + ind;
                }

            }
        }

        // update the terms
        // TODO: should really be done when saving.
        sd.terms.add(text);

        return label + starttokid + endtokid + groupid + sentid;
    }



    @RequestMapping(value="/sents", method= RequestMethod.GET)
    public String annotation(@RequestParam(value="groupid", required=false) String groupid, Model model, HttpSession hs) throws IOException {
        SessionData sd = new SessionData(hs);

        HashMap<String, List<Constituent>> groups = buildgroups(sd);
        hs.setAttribute("groups", groups);

        if(groupid != null) {

            List<Constituent> sents = groups.get(groupid);

            for (Constituent sent : sents) {
                sent.addAttribute("html", getHTMLfromSent(sent));
            }

            model.addAttribute("groupid", groupid);
            model.addAttribute("sents", sents);
            model.addAttribute("labels", hs.getAttribute("labels"));
        }else{
            model.addAttribute("groups", groups);
        }

        return "bs-group-anno";
    }

    @RequestMapping(value="/gethtml", method= RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String gethtml(@RequestParam(value="sentid", required=true) String sentid, Model model, HttpSession hs){
        SessionData sd = new SessionData(hs);

        return getHTMLfromSent(sd.allsents.get(sentid));
    }



    public static String getHTMLfromSent(Constituent sent){

        IntPair sentspan = sent.getSpan();

        TextAnnotation ta = sent.getTextAnnotation();

        View ner = ta.getView(ViewNames.NER_CONLL);

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
