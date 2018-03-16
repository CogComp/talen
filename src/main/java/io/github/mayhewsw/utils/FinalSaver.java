package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader.conllline;
import static io.github.mayhewsw.controllers.SentenceController.getSentId;

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

    public static void save(String origfolder, String romanfolder, String outfolder) throws IOException {


        if((new File(outfolder)).exists()) {
            CoNLLNerReader cnl = new CoNLLNerReader(romanfolder);

            while (cnl.hasNext()) {
                TextAnnotation ta = cnl.next();
                View ner = ta.getView(ViewNames.NER_CONLL);

                TextAnnotation taorig = new CoNLLNerReader(origfolder + "/" + ta.getId()).next();
                // this should overwrite the (empty) NER_CONLL view.
                taorig.addView(ViewNames.NER_CONLL, ner);
                View nerorig = taorig.getView(ViewNames.NER_CONLL);
                View sents = taorig.getView(ViewNames.SENTENCE);

                for (Constituent sent : sents.getConstituents()) {
                    String sentid = getSentId(sent);

                    if(nerorig.getConstituentsCovering(sent).size() == 0) continue;

                    // how to rewrite this sentence as a textannotation.
                    SentToConll(sent, outfolder);
                }
            }
        }else{
            System.out.println(outfolder + " does not exist. Not doing anything.");
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
        String username = "steve";

        String dir = "/shared/corpora/corporaWeb/lorelei/evaluation-20170804/LDC2017E29_LORELEI_IL6_Incident_Language_Pack_for_Year_2_Eval_V1.1/";

        String origfolder = dir + "conll-set0-sentanno-" + username;
        String romanfolder = dir + "conll-set0-sentanno-" + username;
        String outpath = dir + "final-" + username + "/";


        //String dir = "/shared/corpora/corporaWeb/lorelei/data/LDC2016E86_LORELEI_Amharic_Representative_Language_Pack_Monolingual_Text_V1.1/data/monolingual_text/zipped/";
        //String origfolder = dir + "conll/";
        //String romanfolder = dir + "conll-pyrom-sentanno-"+username +"/";
        //String outpath = dir + "final-"+username +"/";

        save(origfolder, romanfolder, outpath);

    }

}
