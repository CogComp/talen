package hello;
import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.annotation.AnnotatorService;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.*;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.nlp.pipeline.IllinoisPipelineFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.*;

@Controller
public class AnnotationController {

    private HashMap<String, TextAnnotation> tas;

    public HashMap<String, TextAnnotation> loadTAs(){
        TextAnnotation ta = TextAnnotationUtilities.createFromTokenizedString("Rashid Buffet is my name , and I 'm from Norway .");
        SpanLabelView emptyview = new SpanLabelView(ViewNames.NER_CONLL, "UserSpecified", ta, 1d);
        ta.addView(ViewNames.NER_CONLL, emptyview);

        Constituent c = new Constituent("PER", ViewNames.NER_CONLL, ta, 0, 2);
        emptyview.addConstituent(c);

        Constituent c2 = new Constituent("LOC", ViewNames.NER_CONLL, ta, 10, 11);
        emptyview.addConstituent(c2);


        TextAnnotation ta2 = TextAnnotationUtilities.createFromTokenizedString("Harold Jones is my game , and I 'm from Serbia .");
        SpanLabelView emptyview2 = new SpanLabelView(ViewNames.NER_CONLL, "UserSpecified", ta2, 1d);
        ta2.addView(ViewNames.NER_CONLL, emptyview2);

        Constituent cc = new Constituent("PER", ViewNames.NER_CONLL, ta2, 0, 2);
        emptyview2.addConstituent(cc);

        Constituent cc2 = new Constituent("LOC", ViewNames.NER_CONLL, ta2, 10, 11);
        emptyview2.addConstituent(cc2);

        HashMap<String, TextAnnotation> ret = new HashMap<>();
        ret.put("0",ta);
        ret.put("1", ta2);

        return ret;
    }

    public HashMap<String, TextAnnotation> loadEnglish() throws AnnotatorException, IOException {
        ResourceManager rm = new ResourceManager( "config/pipeline-config.properties" );
        AnnotatorService pipeline = IllinoisPipelineFactory.buildPipeline( rm );

        HashMap<String, TextAnnotation> ret = new HashMap<>();

        List<String> lines = LineIO.read("eng.txt");

        for(int i = 0; i < 20; i++){
            String text = lines.get(i);
            TextAnnotation ta = pipeline.createAnnotatedTextAnnotation( "nothing", i+"", text );
            ret.put(i + "", ta);
        }

        return ret;
    }

    @RequestMapping(value = "/dummy", method=RequestMethod.GET)
    public String dummy(Model model){
        tas = loadTAs();
        model.addAttribute("tas", tas);
        return "getstarted";
    }

    @RequestMapping(value = "/english", method=RequestMethod.GET)
    public String english(Model model){
        try {
            tas = loadEnglish();
            model.addAttribute("tas", tas);
            return "getstarted";

        } catch (AnnotatorException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "dummy";
    }

    @RequestMapping("/")
    public String home(Model model){
        return "home";
    }


    @RequestMapping(value="/annotation", method=RequestMethod.GET)
    public String annotation(@RequestParam(value="taid", defaultValue="-1") String taid, Model model) {

        if(!tas.containsKey(taid)){
            model.addAttribute("tas", tas);
            return "getstarted";
        }

        TextAnnotation ta = tas.get(taid);
        View ner = ta.getView(ViewNames.NER_CONLL);

//        Annotation a = new Annotation();
//        a.setText(ta.getText());
//        a.setLabel(c.getLabel());
//        a.setStartspan(c.getStartSpan());
//        a.setEndspan(c.getEndSpan());


        model.addAttribute("ta", ta);

        String out = "";
        String[] text = ta.getText().split(" ");
        for(int i = 0; i < text.length; i++){
            List<Constituent> lc = ner.getConstituentsCoveringToken(i);

            if(lc.size() > 0) {
                Constituent c = lc.get(0);

                if(i == c.getStartSpan()) {
                    out += String.format("<span class='%s pointer' id='cons-%d-%d'>%s ", c.getLabel(), c.getStartSpan(), c.getEndSpan(), text[i]);
                } else if(i == c.getEndSpan()-1){
                    out += String.format("%s</span> ", text[i]);
                } else {
                    out += text[i] + " ";
                }


                if (lc.size() > 1) {
                    System.err.println("I don't think this should happen... more than one constituent for a token.");
                }
            }else{
                out += text[i] + " ";
            }
        }

        model.addAttribute("htmlstring", out);
        model.addAttribute("previd", Integer.parseInt(taid)-1);
        model.addAttribute("nextid", Integer.parseInt(taid)+1);


        return "annotation";
    }


    @RequestMapping(value="/result", method=RequestMethod.POST)
    public String result(@RequestParam(value="label") String label, @RequestParam(value="spanid") String spanid, @RequestParam(value="id") String id, Model model) {

        System.out.println(label);
        System.out.println(spanid);
        System.out.println(id);

        String[] ss = spanid.split("-");
        Pair<Integer, Integer> span = new Pair<>(Integer.parseInt(ss[1]), Integer.parseInt(ss[2]));

        TextAnnotation ta = tas.get(id);
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
