package io.github.mayhewsw.utils;

import cz.jirutka.unidecode.Unidecode;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Created by mayhew2 on 6/7/17.
 */
public class Utils {

    public static HashMap<String, String> labelcolors;
    static {
        labelcolors = new HashMap<>();
        // put some common label colors here.
        labelcolors.put("PER", "yellow");
        labelcolors.put("LOC", "greenyellow");
        labelcolors.put("GPE", "coral");
        labelcolors.put("MISC", "coral");
        labelcolors.put("ORG", "lightblue");
    }

    /**
     * Given a label, this will return a standard color, or a random color.
     * @param label
     * @return
     */
    public static String getColorOrRandom(String label){
        String color;
        if(Utils.labelcolors.containsKey(label)){
            color = Utils.labelcolors.get(label);
        }else{
            Random random = new Random();
            int nextInt = random.nextInt(256*256*256);
            color = String.format("#%06x", nextInt);
        }
        return color;
    }

    /**
     * Given a TextAnnotation, this will return the tokens in a cloned String[] array. If the ROMANIZATION
     * view is present, the tokens will come from there. Otherwise, this uses unidecode to get a base romanization.
     *
     * We recommend using the excellent Uroman library: https://www.isi.edu/~ulf/uroman.html
     *
     * @param ta TextAnnotation
     * @return an array of words, romanized if available.
     */
    public static String[] getRomanTaToks(TextAnnotation ta){
        String[] text;
        if(ta.hasView("ROMANIZATION")){
            View translit = ta.getView("ROMANIZATION");
            StringBuilder sb = new StringBuilder();
            for(Constituent c : translit.getConstituents()){
                String romantext = c.getLabel().replace(" ", "_");
                if (romantext.length() == 0){
                    romantext = "_";
                }
                sb.append(romantext +" ");
            }
            text = sb.toString().trim().split(" ");
        }else {

            Unidecode unidecode = Unidecode.toAscii();

            text = ta.getTokens().clone();
            for(int t = 0; t < text.length; t++){
                text[t] = unidecode.decode(text[t]);
            }
        }

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
