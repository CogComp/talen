package io.github.mayhewsw.controllers;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import io.github.mayhewsw.SessionData;
import io.github.mayhewsw.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by mayhew2 on 2/3/17.
 */

@Controller
@RequestMapping("/stats")
public class TextStatisticsController {

    private static Logger logger = LoggerFactory.getLogger(TextStatisticsController.class);
    private static HashMap<String, Integer> counts = new HashMap<>();
    private static int numdocs = 0;
    private static HashMap<String, Integer> term2numdocs = new HashMap<>();

    @RequestMapping(value="gettopstats", method=RequestMethod.POST)
    @ResponseBody
    public String gettopstats(@RequestParam(value="docid") String docid, HttpSession hs, Model model) {

        SessionData sd = new SessionData(hs);

        TextAnnotation ta = sd.tas.get(docid);

        // need to keep track of the number of times this word appears in the document.
        HashMap<String, Integer> doccounts = new HashMap<>();
        String[] romantext = Utils.getRomanTaToks(ta);
        HashMap<String, String> orig2roman = new HashMap<>();

        for(int i = 0; i < ta.size(); i++){
            String word = ta.getToken(i);
            int c = doccounts.getOrDefault(word, 0);
            doccounts.put(word, c+1);
            orig2roman.put(word, romantext[i]);
        }

        String ret = "<small><table class=\"table table-sm table-hover\"><thead><tr>";
        ret += "<th scope=\"col\">Word</th>";
        ret += "<th scope=\"col\">Cnt</th>";
        ret += "<th scope=\"col\">%docs</th>";
        ret += "<th scope=\"col\">Tfidf</th>";
        ret += "</tr></thead><tbody>";

        HashSet<Pair<String, Double>> toptfidf = new HashSet();

        for (String word : ta.getTokens()){
            toptfidf.add(new Pair<>(word, tfidf(word, doccounts.get(word))));
        }

        int k = 10;
        List<Pair<String, Double>> toptfidflist = toptfidf.stream()
                .sorted((a,b) -> b.getSecond().compareTo(a.getSecond()))
                .limit(k)
                .collect(Collectors.toList());

        for(Pair<String, Double> p : toptfidflist){
            String word = p.getFirst();
            Double tfidf = p.getSecond();

            String def = word;
            if (sd.showroman && orig2roman.containsKey(word)){
                def = orig2roman.get(word);
            } else if (sd.showdefs && sd.dict != null && sd.dict.containsKey(word)) {
                def = "<i>" + sd.dict.get(word).get(0) + "</i>";
            }

            String row = "<tr>";
            row += "<td>"+def+"</td>";
            row += "<td>"+counts.getOrDefault(word, 0)+"</td>";
            row += String.format("<td>%.2f</td>",term2numdocs.getOrDefault(word, 0)/(float)numdocs);
            row += String.format("<td>%.2f</td>", tfidf);
            row += "</tr>";
            ret += row;
        }


        ret += "</tbody></table></small>";

        return ret;
    }


    /**
     * This is meant to be a method that gets stats for individual words (e.g. if they are out of the top 10 words that are displayed
     * by default. But I think it is not that important and adds complexity.
     */
//    @RequestMapping(value="getstats", method=RequestMethod.POST)
//    @ResponseBody
//    public String getstats(@RequestParam(value="text") String text, @RequestParam(value="alltext[]") String[] alltext, HttpSession hs, Model model) {
//
//        SessionData sd = new SessionData(hs);
//
//        // need to keep track of the number of times this word appears in the document.
//        HashMap<String, Integer> doccounts = new HashMap<>();
//        for(String word : alltext){
//            int c = doccounts.getOrDefault(word, 0);
//            doccounts.put(word, c+1);
//        }
//
//        String ret = "<small><table class=\"table table-hover\"><thead><tr>";
//        ret += "<th scope=\"col\">Word</th>";
//        ret += "<th scope=\"col\">Cnt</th>";
//        ret += "<th scope=\"col\">%docs</th>";
//        ret += "<th scope=\"col\">Tfidf</th>";
//        ret += "</tr></thead><tbody>";
//
//        String[] words = text.split(" ");
//        for (String word : words){
//
//            String def = word;
//            if (sd.showdefs && sd.dict != null && sd.dict.containsKey(word)) {
//                def = "<i>" + sd.dict.get(word).get(0) + "</i>";
//            }
//
//            String row = "<tr>";
//            row += "<td>"+def+"</td>";
//            row += "<td>"+counts.getOrDefault(word, 0)+"</td>";
//            row += String.format("<td>%.2f</td>",term2numdocs.getOrDefault(word, 0)/(float)numdocs);
//            row += String.format("<td>%.2f</td>", tfidf(word, doccounts.get(word)));
//            row += "</tr>";
//
//            ret += row;
//        }
//
//        ret += "</tbody></table></small>";
//
//        return ret;
//    }

    public static void resetstats() {
        counts = new HashMap<>();
        numdocs = 0;
        term2numdocs = new HashMap<>();
    }

    /**
     * Assume that the text is tokenized with spaces. Assume this is
     * called once per document!
     * @param words
     */
    public static void updateCounts(String[] words){
        HashSet<String> uniqwords = new HashSet<>();
        for (String word : words){
            int c  = counts.getOrDefault(word, 0);
            counts.put(word, c +1);
            uniqwords.add(word);
        }

        for(String word : uniqwords) {
            int doccounts = term2numdocs.getOrDefault(word, 0);
            term2numdocs.put(word, doccounts + 1);
        }

        numdocs++;
    }

    public static double tfidf(String w, int doccounts){
        if(term2numdocs.containsKey(w)) {
            return doccounts * Math.log(numdocs / term2numdocs.get(w));
        }else{
            return 0;
        }
    }

    // This came from loadData in document controller. Assumes a word frequency file that
    // has word freq on each line. Worth doing??
    //        String wordfreqsfile = prop.getProperty("wordfreqsfile");
//        HashMap<String, Float> wordfreqs = new HashMap<>();
//        if(wordfreqsfile != null){
//            ArrayList<String> lines = LineIO.read(wordfreqsfile);
//            for(String line : lines){
//                String[] sline = line.split(" ");
//                String word = sline[0];
//                int freq = Integer.parseInt(sline[1]);
//                wordfreqs.put(word, (float)freq);
//            }
//        }else{
//        }
//        hs.setAttribute("wordfreqs", wordfreqs);






}
