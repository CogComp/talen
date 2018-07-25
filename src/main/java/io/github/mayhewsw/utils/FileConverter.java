package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.annotation.TextAnnotationBuilder;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.SpanLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.nlp.tokenizer.IllinoisTokenizer;
import edu.illinois.cs.cogcomp.nlp.utility.TokenizerTextAnnotationBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.tomcat.util.bcel.Const;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.nio.file.Paths;

public class FileConverter{
    public static List<TextAnnotation> fromTabSeparated(String folderurl) throws IOException {
        return fromTabSeparated(folderurl, folderurl);
    }

    public static List<TextAnnotation> fromTabSeparated(String corpusfolderurl, String tabfolderurl) throws IOException {
        File[] tabFiles = fileFinder(tabfolderurl, ".tab");
        TextAnnotationBuilder taBuilder = new TokenizerTextAnnotationBuilder(new IllinoisTokenizer());
        HashMap<String, TextAnnotation> docsSeen = new HashMap<>();

        List<TextAnnotation> ret = new ArrayList<>();
        for(File tabfile : tabFiles){
            BufferedReader br = new BufferedReader(new FileReader(tabfile));
            String line;

            while((line = br.readLine()) != null){
                String[] entry = line.split("\t");
                String docName = entry[0];
                String startTok = entry[1];
                String endTok = entry[2];
                String[] candidates;
                String token = null;
                if(!(StringUtils.isNumeric(startTok) && StringUtils.isNumeric(endTok))){
                    candidates = Arrays.copyOfRange(entry, 2, entry.length);
                    token = entry[1];
                } else {
                    candidates = Arrays.copyOfRange(entry, 3, entry.length);
                }

                TextAnnotation ta;
                View candgen, ner_conll;
                if(docsSeen.keySet().contains(docName.trim())){
                    ta = docsSeen.get(docName.trim());
                    candgen = ta.getView("CANDGEN");
                    ner_conll = ta.getView(ViewNames.NER_CONLL);
                } else {
                    ta = taBuilder.createTextAnnotation(
                            corpusfolderurl,
                            docName,
                            String.join(
                                    " ",
                                    Files.readAllLines(
                                            Paths.get(
                                                    corpusfolderurl + docName
                                            )
                                    )
                            )
                    );
                    candgen = new SpanLabelView("CANDGEN", ta);
                    ner_conll = new SpanLabelView(ViewNames.NER_CONLL, ta);
                }

                Map<String, Double> lts = new TreeMap<>();

                for(String cand: candidates){
                    lts.put("-1|" + cand, 0.0);
                }

                if(StringUtils.isNumeric(startTok) && StringUtils.isNumeric(endTok)) {
                    Constituent newc = new Constituent(lts, "CANDGEN", ta, Integer.parseInt(startTok), Integer.parseInt(endTok));
                    Constituent conll_c = new Constituent("MISC", ViewNames.NER_CONLL, ta, Integer.parseInt(startTok), Integer.parseInt(endTok));
                    candgen.addConstituent(newc);
                    ner_conll.addConstituent(conll_c);
                } else {
                    List<IntPair> spans = ta.getSpansMatching(token.trim());

                    for(IntPair span : spans){
                        Constituent newc = new Constituent(lts, "CANDGEN", ta, span.getFirst(), span.getSecond());
                        Constituent conll_c = new Constituent("MISC", ViewNames.NER_CONLL, ta, span.getFirst(), span.getSecond());
                        candgen.addConstituent(newc);
                        ner_conll.addConstituent(conll_c);
                    }
                }

                ta.addView("CANDGEN", candgen);
                ta.addView(ViewNames.NER_CONLL, ner_conll);
                ret.add(ta);

                if(!docsSeen.keySet().contains(docName)){
                    docsSeen.put(docName.trim(), ta);
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
