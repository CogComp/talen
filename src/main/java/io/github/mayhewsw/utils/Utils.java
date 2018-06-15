package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mayhew2 on 6/7/17.
 */
public class Utils {

    /**
     * Given a TextAnnotation, this will return the tokens in a cloned String[] array. If the TRANSLITERATION
     * view is present, the tokens will come from there.
     * @param ta TextAnnotation
     * @return an array of words, romanized if available.
     */
    public static String[] getRomanTaToksIfPresent(TextAnnotation ta){
        String[] text;
//        if(ta.hasView(ViewNames.TRANSLITERATION)){
//            View translit = ta.getView(ViewNames.TRANSLITERATION);
//            StringBuilder sb = new StringBuilder();
//            for(Constituent c : translit.getConstituents()){
//                String romantext = c.getLabel().replace(" ", "_");
//                if (romantext.length() == 0){
//                    romantext = "_";
//                }
//                sb.append(romantext +" ");
//            }
//            text = sb.toString().trim().split(" ");
//        }else {
//            text = ta.getTokens().clone();
//        }

        text = ta.getTokens().clone();
        return text;
    }


    /**
     * This removes all stems from a word, even if they are stacked.
     * @param word
     * @param suffixes
     * @return
     */
    public static String stem(String word, List<String> suffixes){
        boolean stemmed = false;
        while(!stemmed) {
            stemmed = true;
            for (String suff : suffixes) {
                if (word.endsWith(suff)) {
                    word = word.substring(0, word.length() - suff.length());
                    stemmed = false;
                }
            }
        }
        return word;
    }

    public static void main(String[] args) {
        String w = "jungoliqlarning";
        List<String> suf = new ArrayList<>();
        suf.add("liq");
        suf.add("lar");
        suf.add("ning");
        System.out.println(stem(w, suf));
    }

}
