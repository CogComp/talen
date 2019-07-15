package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.SpanLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import io.github.mayhewsw.Dictionary;

import org.apache.commons.lang3.StringUtils;


import java.util.Arrays;
import java.util.List;

/**
 * Created by stephen on 8/31/17.
 */
@SuppressWarnings("ALL")
public class HtmlGenerator {


//    public static String getHTMLfromTA(TextAnnotation ta, boolean showdefs) {
//        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), "", null, showdefs);
//    }

    public static String getHTMLfromTA(TextAnnotation ta, Dictionary dict, boolean showdefs) {
        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), "", dict, showdefs, false, false, false);
    }

    public static String getHTMLfromTA(TextAnnotation ta, String query, Dictionary dict, boolean showdefs) {
        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), query, dict, showdefs, false, false, false);
    }


    public static String getHTMLfromTA(TextAnnotation ta, Dictionary dict, boolean showdefs, boolean showroman) {
        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), "", dict, showdefs, showroman, false, false);
    }


    public static String getHTMLfromTA(TextAnnotation ta, Dictionary dict, boolean showdefs, boolean showroman, boolean allowcopy) {
        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), "", dict, showdefs, showroman, allowcopy, false);
    }

    public static String getHTMLfromTA(TextAnnotation ta, Dictionary dict, boolean showdefs, boolean showroman, boolean allowcopy, boolean showgoogle) {
        return getHTMLfromTA(ta, new IntPair(-1, -1), ta.getId(), "", dict, showdefs, showroman, allowcopy, showgoogle);
    }

    // this is basically read only
    public static String getCopyableHTMLFromTA(TextAnnotation ta, Dictionary dict, boolean showdefs, boolean showroman, boolean showgoogle){
        IntPair sentspan = new IntPair(-1, -1);
        String id = ta.getId();

        // required to have one view or another...
        View ner;
        if(ta.hasView(ViewNames.NER_ONTONOTES)){
            ner = ta.getView(ViewNames.NER_ONTONOTES);
        } else{
            ner = ta.getView(ViewNames.NER_CONLL);
        }

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
            text = Utils.getRomanTaToks(ta);
        }else {
            text = ta.getTokens().clone();
        }
        
        if(showgoogle) {
            text = Utils.getGoogleTaToks(ta);
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

            if (showdefs && def != null) {
                text[t] = def;
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
            text[start] = String.format("<span class='%s cons' id='cons-%d-%d' title='%s'>%s", c.getLabel(), start, end, c.getLabel(), text[start]);
            text[end - 1] += "</span>";
        }

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

        String html = StringUtils.join(text, " ");

        String htmltemplate = "<div class=\"card\">" +
                    "<div class=\"card-header\">%s</div>" +
                    "<div class=\"card-body text\" dir=\"auto\" id=%s>%s</div></div>";
        String out = String.format(htmltemplate, id, id, html) + "\n";


        return out;
    }


    /**
     * Given a sentence, produce the HTML for display. .
     * @return
     */
    public static String getHTMLfromTA(TextAnnotation ta, IntPair span, String id, String query, Dictionary dict, boolean showdefs, boolean showroman, boolean allowcopy, boolean showgoogle) {

        IntPair sentspan = span;

        // required to have one view or another...
        View ner;
        if(ta.hasView(ViewNames.NER_ONTONOTES)){
            ner = ta.getView(ViewNames.NER_ONTONOTES);
        } else{
            ner = ta.getView(ViewNames.NER_CONLL);
        }

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
            text = Utils.getRomanTaToks(ta);
        }else {
            text = ta.getTokens().clone();
        }

        if(showgoogle) {
            text = Utils.getGoogleTaToks(ta);
        }else {
            text = ta.getTokens().clone();
        }

        if(sentspan.getFirst() != -1) {
            text = Arrays.copyOfRange(text, sentspan.getFirst(), sentspan.getSecond());
            nonroman_text = Arrays.copyOfRange(nonroman_text, sentspan.getFirst(), sentspan.getSecond());

        }

        // add spans to every word that is not a constituent.
        for (int t = 0; t < text.length; t++) {
            String def = null;
            if (dict != null && dict.containsKey(nonroman_text[t])) {
                def = dict.get(nonroman_text[t]).get(0);
            }

            String tokid = String.format("tok-%s-%s", id, t);


            // The orig attribute is used in the dictionary.
            if (showdefs && def != null) {
                text[t] = "<span class='token pointer def' orig=\""+nonroman_text[t]+"\" id='"+tokid+"'>" + def + "</span>";
            } else {
                // FIXME: this will only work for single word queries.
                if (query.length() > 0 && text[t].startsWith(query)) {
                    text[t] = "<span class='token pointer emph' orig=\""+nonroman_text[t]+"\" id='"+tokid+"'>" + text[t] + "</span>";
                } else {
                    text[t] = "<span class='token pointer' orig=\""+nonroman_text[t]+"\" id='"+tokid+"'>" + text[t] + "</span>";
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
            text[start] = String.format("<span class='%s pointer cons' id='cons-%d-%d' title='%s'>%s", c.getLabel(), start, end, c.getLabel(), text[start]);
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

        String sep = "";
        if(allowcopy){
            sep = " ";
        }
        String html = StringUtils.join(text, sep);

        String htmltemplate;
        if(allowcopy){
            htmltemplate = "<div class=\"card\">" +
                    "<div class=\"card-header\">%s</div>" +
                    "<div class=\"card-body text\" dir=\"auto\" id=%s>%s</div></div>";
        }else{
            htmltemplate = "<div class=\"card\">" +
                    "<div class=\"card-header\">%s</div>" +
                    "<div class=\"card-body text nocopy\" dir=\"auto\" id=%s>%s</div></div>";
        }
        String out = String.format(htmltemplate, id, id, html) + "\n";


        return out;
    }

}
