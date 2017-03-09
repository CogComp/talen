package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.SpanLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * Created by mayhew2 on 3/7/17.
 */
public class FeatureExtractor {

    public static void extract(TextAnnotation ta){

        View ner = ta.getView(ViewNames.NER_CONLL);


        View myfeats = new SpanLabelView("feats", ta);

        // TODO: this should all be in lowercase, but it messes up the pattern matching back in the ta
        for(Constituent c : ner.getConstituents()){
            String surface = c.getTokenizedSurfaceForm();

            IntPair span = c.getSpan();

            // deal with padding.
            String prevword = "_";
            String prevprevword = "_";
            if(span.getFirst() > 0) {
                prevword = ta.getToken(span.getFirst() - 1);
            }
            if(span.getFirst() > 1){
                prevprevword = ta.getToken(span.getFirst()-2);
            }

            myfeats.addConstituent(new Constituent("full-string=" + surface.replaceAll(" ", "_"), "feats", ta, span.getFirst(), span.getSecond()));
//            for(String token : surface.split(" ")) {
//                myfeats.addConstituent(new Constituent("contains=" + token, "feats", ta, span.getFirst(), span.getSecond()));
//            }

//            if(!StringUtils.isAlpha(surface)){
//                //feats.add();
//                myfeats.addConstituent(new Constituent("nonalpha=" + surface, "feats", ta, span.getFirst(), span.getSecond()));
//            }

            myfeats.addConstituent(new Constituent("context=" + prevword, "feats", ta, span.getFirst(), span.getSecond()));
            myfeats.addConstituent(new Constituent("context=" + prevprevword + "_" + prevword, "feats", ta, span.getFirst(), span.getSecond()));

        }
        ta.addView("feats", myfeats);
    }

    public static List<Suggestion> findfeatfires(TextAnnotation ta, HashMap<Pair<String, String>, Double> patterns){
        List<Suggestion> suggestions = new ArrayList<>();

        for(Pair<String,String> feat : patterns.keySet()){
            String featname = feat.getFirst();
            String label = feat.getSecond();
            if(featname.startsWith("context=")){
                String context = featname.split("=")[1];
                if(context.contains("_")){
                    context = context.replaceAll("_", " ");
                }

                // now find occurrences of this context in the ta.
                for(IntPair span : ta.getSpansMatching(context)){
                    IntPair nextspan = new IntPair(span.getSecond(), span.getSecond()+1);
                    Suggestion s = new Suggestion(nextspan, label, String.format("%s for %s, weight: %f", featname, label, patterns.get(feat)));
                    suggestions.add(s);
                }
            }
        }

        return suggestions;
    }

}
