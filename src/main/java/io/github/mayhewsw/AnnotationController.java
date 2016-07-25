package io.github.mayhewsw;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import edu.illinois.cs.cogcomp.core.utilities.StringUtils;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import edu.illinois.cs.cogcomp.transliteration.SPModel;
import edu.illinois.cs.cogcomp.utils.TopList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.xml.soap.Text;
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

    final String ugrevmodel = "/shared/corpora/transliteration/lorelei/models/probs-ug-rev.txt";
    private static SPModel spmodel;

    private HashMap<String, String> folders;
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

        spmodel = new SPModel(ugrevmodel);

        List<String> lines = LineIO.read("config/folders.txt");
        folders = new HashMap<>();
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
    public TreeMap<String, TextAnnotation> loadFolder(String folder) throws IOException {

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
            int limit = Math.min(files.length, 300);
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
    public String dummy(@RequestParam(value="folder") String folder, HttpSession hs) throws IOException {
        TreeMap<String, TextAnnotation> tas = loadFolder(folder);
        hs.setAttribute("tas", tas);
        hs.setAttribute("dataname", folder);

        return "redirect:/annotation";
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

    @RequestMapping("/")
    public String home(Model model){
        model.addAttribute("folders", folders.keySet());
        model.addAttribute("user", new User());
        return "home";
    }

    @RequestMapping(value="/setname")
    public String setname(@ModelAttribute User user, HttpSession hs){
        logger.info("Setting name to: " + user.getName());
        // Just make sure everything is clear first... just in case.
        logger.info("Logging in!");
        hs.removeAttribute("username");
        hs.removeAttribute("dataname");
        hs.removeAttribute("tas");

        hs.setAttribute("username", user.getName());
        return "redirect:/";
    }

    @RequestMapping(value="/logout")
    public String logout(HttpSession hs){
        logger.info("Logging out...");
        hs.removeAttribute("username");
        hs.removeAttribute("dataname");
        hs.removeAttribute("tas");
        return "redirect:/";
    }

    @RequestMapping(value="/annotation", method=RequestMethod.GET)
    public String annotation(@RequestParam(value="taid", required=false) String taid, HttpSession hs, Model model, RedirectAttributes redirectAttributes) {

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");

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
        View ner = ta.getView(ViewNames.NER_CONLL);

        model.addAttribute("ta", ta);

        logger.info(String.format("Viewing TextAnnotation (id=%s)", taid));
        logger.info("Text (trunc): " + ta.getTokenizedText().substring(0, Math.min(20, ta.getTokenizedText().length())));
        logger.info("Num Constituents: " + ner.getConstituents().size());

        String[] text = ta.getTokenizedText().split(" ");

        // add spans to every word that is not a constituent.
        for(int t = 0; t < text.length; t++){
            text[t] = "<span class='token pointer' id='tok-" + t + "'>" + text[t] + "</span>";
        }

        for(Constituent c : ner.getConstituents()){
            if(c.getLabel().equals("MISC")) continue;

            int start = c.getStartSpan();
            int end = c.getEndSpan();

            // important to also include 'cons' class, as it is a keyword in the html
            text[start] = String.format("<span class='%s pointer cons' id='cons-%d-%d'>%s", c.getLabel(), start, end, text[start]);
            text[end-1] += "</span>";

        }

        String out = StringUtils.join(" ", text);

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

        return "annotation";
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
    @RequestMapping(value="/addtoken", method=RequestMethod.POST)
    public String addtoken(@RequestParam(value="label") String label, @RequestParam(value="spanid") String spanid, @RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: change span (id:%s) to label: %s.", idstring, spanid, label));

        String[] ss = spanid.split("-");
        Pair<Integer, Integer> span = new Pair<>(Integer.parseInt(ss[1]), Integer.parseInt(ss[2]));

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");

        TextAnnotation ta = tas.get(idstring);
        String[] spantoks = ta.getTokensInSpan(span.getFirst(), span.getSecond());

        String text = StringUtils.join(" ", spantoks);
        logger.info(text);
        logger.info(spanid);

        //String outname = "";
        // for(String sn : spantoks){
        //     TopList<Double, String> cands = spmodel.Generate(sn);
        //     if (cands.size() > 0) {
        //         sn = cands.getFirst().getSecond();
        //     } else {
        //         // don't do anything.
        //     }
        //     outname += sn + " ";
        // }
        // logger.info(outname);


        View ner = ta.getView(ViewNames.NER_CONLL);
        List<Constituent> lc = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());

        int origstart = span.getFirst();
        int origend = span.getSecond();
        String origlabel = null;
        if(lc.size() > 0) {
            Constituent oldc = lc.get(0);
//            origstart = oldc.getStartSpan();
//            origend = oldc.getEndSpan();
//            origlabel = oldc.getLabel();
            ner.removeConstituent(oldc);
        }


//        if(origstart != span.getFirst()){
//            // this means last token is being changed.
//            Constituent newc = new Constituent(origlabel, ViewNames.NER_CONLL, ta, origstart, span.getFirst());
//            ner.addConstituent(newc);
//        }else if(origend != span.getSecond()){
//            // this means first token is being changed.
//            Constituent newc = new Constituent(origlabel, ViewNames.NER_CONLL, ta, span.getSecond(), origend);
//            ner.addConstituent(newc);
//        }

        // an O label means don't add the constituent.
        if(label.equals("O")) {
            System.err.println("Should never happen: label is O");
        }else{
            Constituent newc = new Constituent(label, ViewNames.NER_CONLL, ta, span.getFirst(), span.getSecond());
            ner.addConstituent(newc);
        }

        // just a dummy response...
        return "dummy";
    }

    @RequestMapping(value="/removetoken", method=RequestMethod.POST)
    public String removetoken(@RequestParam(value="tokid") String tokid,  @RequestParam(value="id") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: remove token (id:%s).", idstring, tokid));

        String[] ss = tokid.split("-");
        int inttokid = Integer.parseInt(ss[1]);
        Pair<Integer, Integer> tokspan = new Pair<>(inttokid, inttokid+1);

        TreeMap<String, TextAnnotation> tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");

        TextAnnotation ta = tas.get(idstring);

        String[] spantoks = ta.getTokensInSpan(tokspan.getFirst(), tokspan.getSecond());
        String text = StringUtils.join(" ", spantoks);
        logger.info(text);

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

                // just a dummy response...
        return "dummy";
    }


}
