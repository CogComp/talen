package io.github.mayhewsw.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mayhew2 on 6/7/17.
 */
public class Utils {

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
