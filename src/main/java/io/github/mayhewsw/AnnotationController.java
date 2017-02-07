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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * This contains the main logic of the whole thing.
 */
@SuppressWarnings("ALL")
@Controller
public class AnnotationController {

    private static Logger logger = LoggerFactory.getLogger(AnnotationController.class);

    // These are all common objects that don't change user by user.
    private HashMap<String, String> folders;
    private List<String> labels;
    private HashMap<String,String> foldertypes;
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

        logger.debug("Loading folders.txt");
        List<String> lines = LineIO.read("config/folders.txt");
        folders = new HashMap<String, String>();
        foldertypes = new HashMap<>();
        for(String line: lines){
            if(line.length() == 0 || line.startsWith("#")){
                continue;
            }
            String[] sl = line.trim().split("\\s+");
            logger.debug(line);
            logger.debug(sl.length + "");
            folders.put(sl[0], sl[1]);
            foldertypes.put(sl[0], sl[2]);
        }

        logger.debug("Loading labels.txt");
        List<String> labellines = LineIO.read("config/labels.txt");
        List<String> csslines = new ArrayList<String>();
        labels = new ArrayList<>();
        for(String line: labellines){
            if(line.length() == 0 || line.startsWith("#")){
                continue;
            }
            String[] sl = line.trim().split("\\s+");
            labels.add(sl[0]);
            csslines.add("." + sl[0] + "{ background-color: " + sl[1] + "; }");
        }
        logger.debug("using labels: " + labels.toString());

        LineIO.write("src/main/resources/static/css/labels.css", csslines);

    }

    /**
     * Important to add folders and user to the model.
     * @param model
     * @return
     */
    @RequestMapping("/")
    public String home(Model model, HttpSession hs){
        model.addAttribute("folders", folders.keySet());
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
    public TreeMap<String, TextAnnotation> loadFolder(String folder, String username) throws Exception {

        String folderurl = folders.get(folder);
        String foldertype = foldertypes.get(folder);

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
     * This is called when the user clicks on the language button on the homepage.
     * @param folder
     * @param hs
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/loaddata", method=RequestMethod.GET)
    public String loaddata(@RequestParam(value="folder") String folder, HttpSession hs) throws Exception {
        String username = (String) hs.getAttribute("username");
        TreeMap<String, TextAnnotation> tas = loadFolder(folder, username);

        HashMap<String, Integer> rules = loadallrules(tas);

        hs.setAttribute("tas", tas);
        hs.setAttribute("dataname", folder);
        hs.setAttribute("rules", rules);

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
    public String save(@RequestParam(value="taid", required=true) String taid, HttpSession hs) throws IOException {

        // write out to
        String username = (String) hs.getAttribute("username");
        String folder = (String) hs.getAttribute("dataname");
        String folderpath = folders.get(folder);
        String foldertype = foldertypes.get(folder);

        if(username != null && folderpath != null) {

            folderpath = folderpath.replaceAll("/$", "");
            String outpath = folderpath + "-annotation-" + username + "/";
            logger.info("Writing out to: " + outpath);
            logger.info("id is: " + taid);

            TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
            TextAnnotation taToSave = tas.get(taid);
            String savepath = outpath + taid;

            if(foldertype.equals(FOLDERTA)) {
                SerializationHelper.serializeTextAnnotationToFile(taToSave, savepath, true);
            }else if(foldertype.equals(FOLDERCONLL)) {
                CoNLLNerReader.TaToConll(Collections.singletonList(taToSave), outpath);
            }
        }
        // nothing happens to this...
        return "redirect:/";
    }

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

        // set an empty placeholder.


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

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
        Dictionary dict = (Dictionary) hs.getAttribute("dict");
        HashMap<String, Integer> rules = (HashMap<String, Integer>) hs.getAttribute("rules");

        // Go to the homepage.
        if(tas == null){
            return "redirect:/";
        }

        // If there's no taid, then return the getstarted page (not a redirect).
        if(taid == null){
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

        // set up the html string.
        String out = this.getHTMLfromTA(ta, dict);
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

        HashMap<String, Integer> docrules = getdocrules(ta, rules);
        model.addAttribute("docrules", docrules.keySet());

        return "annotation";
    }


    /**
     * Given a TA, this returns the HTML string.
     * @param
     * @return
     */
    public String getHTMLfromTA(TextAnnotation ta, Dictionary dict){
        View ner = ta.getView(ViewNames.NER_CONLL);
        View sents = ta.getView(ViewNames.SENTENCE);

        String[] text = ta.getTokenizedText().split(" ");

        // add spans to every word that is not a constituent.
        for(int t = 0; t < text.length; t++){
            String def = null;
            if(dict.containsKey(text[t])){
                def = dict.get(text[t]).get(0);
            }
            text[t] = "<span class='token pointer' def='<i>"+ def +"</i>' id='tok-" + t + "'>" + text[t] + "</span>";
        }

        for(Constituent c : ner.getConstituents()){

            int start = c.getStartSpan();
            int end = c.getEndSpan();

            // important to also include 'cons' class, as it is a keyword in the html
            text[start] = String.format("<span class='%s pointer cons' id='cons-%d-%d'>%s", c.getLabel(), start, end, text[start]);
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

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
        Dictionary dict = (Dictionary)hs.getAttribute("dict");
        HashMap<String, Integer> rules = (HashMap<String, Integer>) hs.getAttribute("rules");

        TextAnnotation ta = tas.get(idstring);

        // cannot annotate across sentence boundaries. Return with no changes if this happens.
        View sents = ta.getView(ViewNames.SENTENCE);
        List<Constituent> sentlc = sents.getConstituentsCoveringSpan(starttokint, endtokint);
        if(sentlc.size() != 1){
            String out = this.getHTMLfromTA(ta, dict);
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

        String out = this.getHTMLfromTA(ta, dict);
        return out;

    }

    @RequestMapping(value="/removetoken", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String removetoken(@RequestParam(value="tokid") String tokid,  @RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: remove token (id:%s).", idstring, tokid));

        int tokint= Integer.parseInt(tokid);
        Pair<Integer, Integer> tokspan = new Pair<>(tokint, tokint+1);

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
        Dictionary dict = (Dictionary)hs.getAttribute("dict");
        HashMap<String, Integer> rules = (HashMap<String, Integer>) hs.getAttribute("rules");

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

        String out = this.getHTMLfromTA(ta, dict);
        return out;
    }

    @RequestMapping(value="/removeall", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String removeall(@RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
        Dictionary dict = (Dictionary)hs.getAttribute("dict");
        HashMap<String, Integer> rules = (HashMap<String, Integer>) hs.getAttribute("rules");
        TextAnnotation ta = tas.get(idstring);

        View ner = ta.getView(ViewNames.NER_CONLL);
        //ner.removeAllConsituents();

        for(Constituent c : ner.getConstituents()){
            ner.removeConstituent(c);
        }

        String out = this.getHTMLfromTA(ta, dict);
        return out;
    }


    // not sure how to pass objects between controllers, so will hold off on this for now.

    @RequestMapping(value="/updaterules", method= RequestMethod.GET)
    @ResponseBody
    public HashMap<String, Integer> update(@RequestParam(value="taid") String taid, HttpSession hs, Model model) {
        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
        HashMap<String, Integer> rules = (HashMap<String, Integer>) hs.getAttribute("rules");
        TextAnnotation ta = tas.get(taid);
        return getdocrules(ta, rules);
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


    @RequestMapping(value = "/applyrule", method=RequestMethod.GET)
    @ResponseBody
    public String apply(@RequestParam(value="rule") String rule, @RequestParam(value="id") String idstring, HttpSession hs) throws Exception {

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
        Dictionary dict = (Dictionary)hs.getAttribute("dict");
        HashMap<String, Integer> rules = (HashMap<String, Integer>) hs.getAttribute("rules");
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

        String out = this.getHTMLfromTA(ta, dict);
        return out;
    }



}
