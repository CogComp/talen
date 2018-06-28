package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.SpanLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import io.github.mayhewsw.Dictionary;
import io.github.mayhewsw.SessionData;
import io.github.mayhewsw.Suggestion;


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONObject;

import static io.github.mayhewsw.controllers.DocumentController.getdocsuggestions;

/**
 * Created by stephen on 8/31/17.
 */
@SuppressWarnings("ALL")
public class HtmlGenerator {


//    public static String getHTMLfromTA(TextAnnotation ta, boolean showdefs) {
//        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), "", null, showdefs);
//    }

    public static String getHTMLfromTA(TextAnnotation ta, Dictionary dict, boolean showdefs) {
        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), "", dict, showdefs, false);
    }

    public static String getHTMLfromTA(TextAnnotation ta, String query, Dictionary dict, boolean showdefs) {
        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), query, dict, showdefs, false);
    }


    public static String getHTMLfromTA(TextAnnotation ta, Dictionary dict, Boolean showdefs, Boolean showroman) {
        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), "", dict, showdefs, showroman);
    }


    /**
     * Given a sentence, produce the HTML for display. .

     * @return
     */
    public static String getHTMLfromTA(TextAnnotation ta, IntPair span, String id, String query, Dictionary dict, boolean showdefs, boolean showroman) {

        IntPair sentspan = span;

        View ner = ta.getView(ViewNames.NER_CONLL);

        View nersugg = null;
        if(ta.hasView("NER_SUGGESTION")) {
            nersugg = ta.getView("NER_SUGGESTION");
        }else{
            // create a dummy view!
            nersugg = new SpanLabelView("NER_SUGGESTION", ta);
            ta.addView("NER_SUGGESTION", nersugg);
        }

        String[] nonroman_text = ta.getTokens().clone();

        // We clone the text so that when we modify it (below) the TA is unchanged.
        String[] text;
        if(showroman) {
            text = Utils.getRomanTaToksIfPresent(ta);
        }else {
            text = ta.getTokens().clone();
        }

        if(sentspan.getFirst() != -1) {
            text = Arrays.copyOfRange(text, sentspan.getFirst(), sentspan.getSecond());
        }

        // add spans to every word that is not a constituent.
        for (int t = 0; t < text.length; t++) {
            String def = null;
            if (dict != null && dict.containsKey(nonroman_text[t])) {
                def = dict.get(nonroman_text[t]).get(0);
            }

            String tokid = String.format("tok-%s-%s", id, t);


            if (showdefs && def != null) {
                text[t] = "<span class='token pointer def' orig=\""+text[t]+"\" id='"+tokid+"'>" + def + "</span>";
            } else {
                // FIXME: this will only work for single word queries.
                if (query.length() > 0 && text[t].startsWith(query)) {
                    text[t] = "<span class='token pointer emph' orig=\""+text[t]+"\" id='"+tokid+"'>" + text[t] + "</span>";
                } else {
                    text[t] = "<span class='token pointer' orig=\""+text[t]+"\" id='"+tokid+"'>" + text[t] + "</span>";
                }
            }
        }

        List<Constituent> sentner;
        List<Constituent> sentnersugg;
        int startoffset;
        if(sentspan.getFirst() == -1){
            sentner = ner.getConstituents();
            sentnersugg = nersugg.getConstituents();
            startoffset = 0;
        }else {
            sentner = ner.getConstituentsCoveringSpan(sentspan.getFirst(), sentspan.getSecond());
            sentnersugg = ner.getConstituentsCoveringSpan(sentspan.getFirst(), sentspan.getSecond());
            startoffset = sentspan.getFirst();
        }

        for (Constituent c : sentner) {

            int start = c.getStartSpan() - startoffset;
            int end = c.getEndSpan() - startoffset;

            // important to also include 'cons' class, as it is a keyword in the html
            text[start] = String.format("<span class='%s pointer cons' id='cons-%d-%d'>%s", c.getLabel(), start, end, text[start]);
            text[end - 1] += "</span>";
        }

//        for (Constituent c : sentnersugg) {
//
//            int start = c.getStartSpan() - startoffset;
//            int end = c.getEndSpan() - startoffset;
//
//            // important to also include 'cons' class, as it is a keyword in the html
//            text[start] = String.format("<strong>%s", text[start]);
//            text[end - 1] += "</strong>";
//        }

        // Then add sentences.
        List<Constituent> sentlist;
        View sentview = ta.getView(ViewNames.SENTENCE);
        if(sentspan.getFirst() == -1){
            sentlist = sentview.getConstituents();
            startoffset = 0;
        }else {
            sentlist = sentview.getConstituentsCoveringSpan(sentspan.getFirst(), sentspan.getSecond());
            startoffset = sentspan.getFirst();
        }

        for (Constituent c : sentlist) {

            int start = c.getStartSpan() - startoffset;
            int end = c.getEndSpan() - startoffset;

            text[start] = "<p>" + text[start];
            text[end - 1] += "</p>";
        }

        String html = StringUtils.join(text, "");

        View candgen = null;
        if(ta.hasView("CANDGEN")){
          candgen = ta.getView("CANDGEN");
        } else {
          candgen = new SpanLabelView("CANDGEN", ta);
          ta.addView("CANDGEN", candgen);
        }

        List<Constituent> candlist;
        if(sentspan.getFirst() == -1){
            candlist = candgen.getConstituents();
            startoffset = 0;
        }else {
            candlist = candgen.getConstituentsCoveringSpan(sentspan.getFirst(), sentspan.getSecond());
            startoffset = sentspan.getFirst();
        }

        for(Constituent c: candlist){

              Map<String, Double> labelScoreMap = c.getLabelsToScores();
              TreeMap<String, String> labelScoreMapString = new TreeMap<>();
              if(labelScoreMap != null){
                for(String s : labelScoreMap.keySet()){
                  labelScoreMapString.put(s, labelScoreMap.get(s).toString());
                }
              }

              String jsonString  = (labelScoreMap == null) ? "{}" : new JSONObject(labelScoreMapString).toString();

              int end = c.getEndSpan() - startoffset;
              int start = c.getStartSpan() - startoffset;
              html += String.format("<span id='candgen-tok-%s-%d-%d' class='candgen-list-hidden' hidden>", id, start, end - 1) + jsonString + "</span>";

        }

        String htmltemplate = "<div class=\"card\">" +
                "<div class=\"card-header\">%s</div>" +
                "<div class=\"card-body text\" id=%s>%s</div></div>";
        String out = String.format(htmltemplate, id, id, html) + "\n";


        return out;
    }


    /**
     * Given a TA, this returns the HTML string.
     * @param
     * @return
     */
    public static String getHTMLfromTA_OLD(TextAnnotation ta, SessionData sd){

        View ner = ta.getView(ViewNames.NER_CONLL);
        View sents = ta.getView(ViewNames.SENTENCE);

        String[] text = ta.getTokenizedText().split(" ");

        ArrayList<String> suffixes = sd.suffixes;

        if(suffixes == null){
            new ArrayList<>();
        }else{
            suffixes.sort((String s1, String s2)-> s2.length()-s1.length());
        }

        // add spans to every word that is not a constituent.
        for(int t = 0; t < text.length; t++){
            String def = null;
            if(sd.dict != null && sd.dict.containsKey(text[t])){
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
                text[t] = "<span class='token pointer def' id='tok-"+ t + "'>" + def + "</span>";
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

            System.out.println(start + " " + end + ": " + s.reason + " " + s);

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

        String out = StringUtils.join("", text);
        return out;
    }

}
