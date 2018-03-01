package io.github.mayhewsw.controllers;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotationUtilities;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.utilities.StringUtils;
import io.github.mayhewsw.Dictionary;
import io.github.mayhewsw.SessionData;
import io.github.mayhewsw.controllers.DocumentController;
import io.github.mayhewsw.utils.HtmlGenerator;
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
 * This contains the main logic of the whole thing.
 */
@SuppressWarnings("ALL")
@Controller
@RequestMapping("/mturk/")
public class MTurkController {

    private static Logger logger = LoggerFactory.getLogger(DocumentController.class);


    /**
     * When this class is loaded, it looks for files in config.
     *
     * @throws FileNotFoundException
     */
    public MTurkController() throws IOException {
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
    public void addspan(@RequestParam(value="label") String label, @RequestParam(value="starttokid") String starttokid, @RequestParam(value="endtokid") String endtokid, @RequestParam(value="text") String text, Model model) throws Exception {

        TextAnnotation ta = TextAnnotationUtilities.createFromTokenizedString(text);

        int starttokint= Integer.parseInt(starttokid);
        int endtokint = Integer.parseInt(endtokid);
        // cannot annotate across sentence boundaries. Return with no changes if this happens.
        View sents = ta.getView(ViewNames.SENTENCE);
        List<Constituent> sentlc = sents.getConstituentsCoveringSpan(starttokint, endtokint);

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
            }
        }
    }

    @RequestMapping(value="/removetoken", method=RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String removetoken(@RequestParam(value="tokid") String tokid,  @RequestParam(value="sentid") String idstring, HttpSession hs, Model model) throws Exception {

        logger.info(String.format("TextAnnotation with id %s: remove token (id:%s).", idstring, tokid));

        int tokint= Integer.parseInt(tokid);
        Pair<Integer, Integer> tokspan = new Pair<>(tokint, tokint+1);

        SessionData sd = new SessionData(hs);
        TreeMap<String, TextAnnotation> tas = sd.tas;
        Dictionary dict = sd.dict;

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

        String out = HtmlGenerator.getHTMLfromTA(ta, sd.showdefs);
        return out;
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

        return HtmlGenerator.getHTMLfromTA(ta, sd.showdefs);
    }

    @RequestMapping(value = "/gethtml", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String gethtml(@RequestParam(value = "sentids[]", required = true) String[] sentids, String query, Model model, HttpSession hs) throws FileNotFoundException {
        SessionData sd = new SessionData(hs);

        String ret = "";
        for(String sentid : sentids){
            TextAnnotation ta = sd.tas.get(sentid);
            String html = HtmlGenerator.getHTMLfromTA(ta, sd.showdefs);
            ret += html + "\n";
        }

        return ret;
    }


    public static void main(String[] args) throws Exception {
        DocumentController c = new DocumentController();

    }


}
