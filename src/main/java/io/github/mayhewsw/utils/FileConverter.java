package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.SpanLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class FileConverter{
    public static List<TextAnnotation> fromTabSeparated(String folderurl) throws Exception {
        return fromTabSeparated(folderurl, folderurl);
    }

    public static List<TextAnnotation> fromTabSeparated(String corpusfolderurl, String tabfolderurl) throws Exception {
        File[] tabFiles = fileFinder(tabfolderurl, ".tab");
        Map<String, TextAnnotation> tasSeen = new HashMap<>();

        List<TextAnnotation> ret = new ArrayList<>();
        for(File tabfile : tabFiles){
            BufferedReader br = new BufferedReader(new FileReader(tabfile));
            String line;

            while((line = br.readLine()) != null){
                String[] entry = line.split("\t");
                String startTok = entry[1];
                String endTok = entry[2];
                String[] candidates = Arrays.copyOfRange(entry, 3, entry.length);

                View candgen, ner_conll;
                TextAnnotation ta;

                if(tasSeen.keySet().contains(entry[0].trim())){
                    ta = tasSeen.get(entry[0].trim());
                } else {
                    ta = SerializationHelper.deserializeTextAnnotationFromFile(corpusfolderurl + entry[0]);
                }

                if(ta.hasView("CANDGEN")){
                    candgen = ta.getView("CANDGEN");
                } else {
                    candgen = new SpanLabelView("CANDGEN", ta);
                }
                if(ta.hasView(ViewNames.NER_CONLL)) {
                    ner_conll = ta.getView(ViewNames.NER_CONLL);
                } else {
                    ner_conll = new SpanLabelView(ViewNames.NER_CONLL, ta);
                }

                Map<String, Double> lts = new TreeMap<>();
                for(String cand: candidates){
                    lts.put("-1|" + cand, 0.0);
                }

                Constituent newc, conll_c;
                if(!candgen.getConstituentsCoveringSpan(Integer.parseInt(startTok), Integer.parseInt(endTok)).isEmpty()){
                    Constituent oldc = candgen.getConstituentsCoveringSpan(Integer.parseInt(startTok), Integer.parseInt(endTok)).get(0);
                    Map<String, Double> old_lts = oldc.getLabelsToScores();
                    old_lts.forEach(lts::putIfAbsent);
                }
                newc = new Constituent(lts, "CANDGEN", ta, Integer.parseInt(startTok), Integer.parseInt(endTok));
                candgen.addConstituent(newc);

                if(ner_conll.getConstituentsCoveringSpan(Integer.parseInt(startTok), Integer.parseInt(endTok)).isEmpty()){
                    conll_c = new Constituent("MENTION", ViewNames.NER_CONLL, ta, Integer.parseInt(startTok), Integer.parseInt(endTok));
                    ner_conll.addConstituent(conll_c);
                }

                if(!ta.hasView("CANDGEN")){ta.addView("CANDGEN", candgen);}
                if(!ta.hasView(ViewNames.NER_CONLL)){ta.addView(ViewNames.NER_CONLL, ner_conll);}
                ret.add(ta);

                if(!tasSeen.keySet().contains(entry[0].trim())){
                    tasSeen.put(entry[0].trim(), ta);
                }
            }
        }

        return ret;
    }

    private static File[] fileFinder( String dirName, String extension){
        File dir = new File(dirName);

        return dir.listFiles((dir1, filename) -> filename.endsWith(extension));

    }
}
