package hello;
import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.annotation.AnnotatorService;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.*;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.nlp.pipeline.IllinoisPipelineFactory;
import org.apache.commons.lang3.StringUtils;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

@Controller
public class AnnotationController {

    private static Logger logger = LoggerFactory.getLogger(AnnotationController.class);

    //private HashMap<String, TextAnnotation> tas;

    private HashMap<String, String> folders;

    public AnnotationController() throws FileNotFoundException {
        List<String> lines = LineIO.read("config/folders.txt");
        folders = new HashMap<>();
        for(String line: lines){
            if(line.length() == 0){
                continue;
            }
            String[] sl = line.trim().split("\\s+");
            System.out.println(line);
            System.out.println(sl.length);
            folders.put(sl[0], sl[1]);
        }
    }

    public TreeMap<Integer, TextAnnotation> loadFolder(String folder) throws IOException {

        String folderurl = folders.get(folder);

        File f = new File(folderurl);

        TreeMap<Integer, TextAnnotation> ret = new TreeMap<>();


        String[] files = f.list();

        Random r = new Random();

        for(int i = 0; i < 100; i++){
            int randint = r.nextInt(files.length);
            String file = files[randint];
            TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(folderurl + "/" + file);
            ret.put(i, ta);
        }

        return ret;
    }


    @RequestMapping(value = "/loaddata", method=RequestMethod.GET)
    public String dummy(@RequestParam(value="folder") String folder, HttpSession hs,  Model model) throws IOException {
        TreeMap<Integer, TextAnnotation> tas = loadFolder(folder);
        hs.setAttribute("tas", tas);
        hs.setAttribute("dataname", folder);

        return "redirect:/annotation";
    }


    @RequestMapping(value = "/save", method=RequestMethod.GET)
    public String save(HttpSession hs) throws IOException {

        // write out to

        String username = (String) hs.getAttribute("name");
        if(username != null) {
            String folder = (String) hs.getAttribute("dataname");
            String folderpath = folders.get(folder);

            folderpath = folderpath.replaceAll("/$", "");
            String outpath = folderpath + "-annotation-" + username + "/";
            logger.info("Writing out to: " + outpath);
            TreeMap<Integer, TextAnnotation> tas = (TreeMap<Integer, TextAnnotation>) hs.getAttribute("tas");
            for(int id: tas.keySet()){
                SerializationHelper.serializeTextAnnotationToFile(tas.get(id), outpath + id, true);
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
        logger.debug("Setting name to: " + user.getName());
        hs.setAttribute("name", user.getName());
        return "redirect:/";
    }

    @RequestMapping(value="/logout")
    public String logout(HttpSession hs){
        hs.removeAttribute("name");
        return "redirect:/";
    }

    @RequestMapping(value="/annotation", method=RequestMethod.GET)
    public String annotation(@RequestParam(value="taid", required=false) Integer taid, HttpSession hs, Model model, RedirectAttributes redirectAttributes) {

        TreeMap<Integer, TextAnnotation> tas = (TreeMap<Integer, TextAnnotation>) hs.getAttribute("tas");

        if(tas == null){
            return "redirect:/";
        }


        if(taid == null || !tas.containsKey(taid)){
            model.addAttribute("tas", tas);
            List<Integer> li = new ArrayList<>(tas.keySet());
            Collections.sort(li);
            model.addAttribute("sortedkeys", li);
            return "getstarted";
        }

        TextAnnotation ta = tas.get(taid);
        View ner = ta.getView(ViewNames.NER_CONLL);

        model.addAttribute("ta", ta);

        logger.info(String.format("Viewing TextAnnotation (id=%s)", taid));
        logger.info("\tText: " + ta.getTokenizedText());
        logger.info("\tConstituents: " + ner.getConstituents());
//        System.out.println(ta.getTokenizedText());
//        System.out.println(ner.getConstituents());
//        for(Constituent c : ner.getConstituents()){
//            System.out.println(c.getSurfaceForm());
//            System.out.println(c.getLabel());
//            System.out.println(c.getSpan());
//        }

        String[] text = ta.getTokenizedText().split(" ");

        for(Constituent c : ner.getConstituents()){
            int start = c.getStartSpan();
            int end = c.getEndSpan();

            text[start] = String.format("<span class='%s pointer' id='cons-%d-%d'>%s", c.getLabel(), start, end, text[start]);
            text[end-1] += "</span>";

        }

        String out = StringUtils.join(text, " ");

        model.addAttribute("htmlstring", out);
        model.addAttribute("previd", taid-1);
        if(taid < tas.size()-1) {
            model.addAttribute("nextid", taid + 1);
        }else{
            model.addAttribute("nextid", -1);
        }

        return "annotation";
    }


    @RequestMapping(value="/result", method=RequestMethod.POST)
    public String result(@RequestParam(value="label") String label, @RequestParam(value="spanid") String spanid, @RequestParam(value="id") String id, HttpSession hs, Model model) {

        logger.info(String.format("TextAnnotation with id %s: change span (id:%s) to label: %s.", id, spanid, label));

        int idint = Integer.parseInt(id);

        String[] ss = spanid.split("-");
        Pair<Integer, Integer> span = new Pair<>(Integer.parseInt(ss[1]), Integer.parseInt(ss[2]));

        TreeMap<Integer, TextAnnotation> tas = (TreeMap<Integer, TextAnnotation>) hs.getAttribute("tas");

        TextAnnotation ta = tas.get(idint);
        View ner = ta.getView(ViewNames.NER_CONLL);
        List<Constituent> lc = ner.getConstituentsCoveringSpan(span.getFirst(), span.getSecond());


        Constituent c = lc.get(0);
        Constituent newc = c.cloneForNewViewWithDestinationLabel(ViewNames.NER_CONLL, label);
        
        ner.removeConstituent(c);
        ner.addConstituent(newc);


        // just a dummy response...
        return "dummy";
    }

}
