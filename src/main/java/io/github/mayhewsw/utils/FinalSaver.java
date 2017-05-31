package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader.conllline;
import static io.github.mayhewsw.BootstrapController.getSentId;

/**
 * This class is necessary because of the fact that most data we work with is non-Latin script, and we
 * work only with sentences. This takes a folder of Romanized annotation documents, an analogous folder
 * with original script documents, and a list of sentences that must be printed.
 *
 * This writes sentences out to file with the original script. This is a matter of matching up annotations
 * with sentences. Nothing too fancy.
 *
 * Created by stephen on 5/29/17.
 */
public class FinalSaver {

    public static void save(String origfolder, String romanfolder, String sentencesfname, String outfolder) throws IOException {

        String sentidsfname = sentencesfname;
        HashSet<String> annosentids = new HashSet<>();
        if(new File(sentidsfname).exists()){
            List<String> annolines = LineIO.read(sentidsfname);

            for(String annoline : annolines){
                String sentidline = annoline.split("\t")[1];
                annosentids.addAll(Arrays.asList(sentidline.split(",")));
            }
        }else{
            System.err.println("No annotated sentences... exiting...");
            return;
        }

        System.out.println(annosentids);

        if((new File(outfolder)).exists()) {
            CoNLLNerReader cnl = new CoNLLNerReader(romanfolder);

            while (cnl.hasNext()) {
                TextAnnotation ta = cnl.next();
                View ner = ta.getView(ViewNames.NER_CONLL);

                TextAnnotation taorig = new CoNLLNerReader(origfolder + "/" + ta.getId()).next();
                // this should overwrite the (empty) NER_CONLL view.
                taorig.addView(ViewNames.NER_CONLL, ner);
                View sents = taorig.getView(ViewNames.SENTENCE);

                for (Constituent sent : sents.getConstituents()) {
                    String sentid = getSentId(sent);

                    // only keep those sentences that we have annotated.
                    if(!annosentids.contains(sentid)) continue;

                    // how to rewrite this sentence as a textannotation.
                    SentToConll(sent, outfolder);
                }
            }
        }

    }


    /**
     * Write this sentence out to conll format. Each individual sentence becomes a file.
     * @param sent
     * @param outpath
     * @throws IOException
     */
    public static void SentToConll(Constituent sent, String outpath) throws IOException {

        TextAnnotation ta = sent.getTextAnnotation();
        ArrayList talines = new ArrayList();
        View nerview = ta.getView("NER_CONLL");

        for(int i = sent.getStartSpan(); i < sent.getEndSpan(); ++i) {
            String label = "O";
            List constituents = nerview.getConstituentsCoveringToken(i);
            if(constituents.size() > 0) {
                Constituent sents = (Constituent)constituents.get(0);
                if(sents.getStartSpan() == i) {
                    label = "B-" + sents.getLabel();
                } else {
                    label = "I-" + sents.getLabel();
                }
            }

            talines.add(conllline(label, i, ta.getToken(i)));
        }

        FileUtils.writeLines(Paths.get(outpath, new String[]{getSentId(sent)}).toFile(), talines);
    }

    public static void main(String[] args) throws IOException {
        String origfolder = "data/tmp";
        String romanfolder = "data/train-short-sentanno-test/";
        String sentencesfname = "data/annosents-test.txt";
        String outpath = "/tmp/sents/";

        save(origfolder, romanfolder, sentencesfname, outpath);

    }

}
