package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.*;
import edu.illinois.cs.cogcomp.core.io.IOUtils;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.lbjava.parse.LinkedVector;
import edu.illinois.cs.cogcomp.ner.InferenceMethods.Decoder;
import edu.illinois.cs.cogcomp.ner.LbjFeatures.NETaggerLevel1;
import edu.illinois.cs.cogcomp.ner.LbjTagger.*;
import edu.illinois.cs.cogcomp.ner.NERAnnotator;
import edu.illinois.cs.cogcomp.ner.ParsingProcessingData.TaggedDataWriter;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.apache.commons.lang3.StringUtils;

import javax.xml.soap.Text;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by mayhew2 on 5/18/17.
 */
public class Sandbox {

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public static void Windowize(Data data){

        for (int docid = 0; docid < data.documents.size(); docid++) {
            ArrayList<LinkedVector> sentences = data.documents.get(docid).sentences;
            for (int i = 0; i < sentences.size(); i++)
                for (int j = 0; j < sentences.get(i).size(); j++) {
                    NEWord w = (NEWord) sentences.get(i).get(j);

                    w.weight = 0.0;

                    if(!w.neLabel.equals("O")){
                        w.weight = 1.0;
                    }

                    if(w.next != null && !((NEWord)w.next).neLabel.equals("O")){
                        w.weight = 1.0;
                    }

                    if(w.previous != null && !((NEWord)w.previous).neLabel.equals("O")){
                        w.weight = 1.0;
                    }
                }
        }

    }

    public static void annotate2(TextAnnotation ta, String modelPath){
        // convert this data structure into one the NER package can deal with.
        String viewName = "NER_SUGGESTION";

        ArrayList<LinkedVector> sentences = new ArrayList<>();
        String[] tokens = ta.getTokens();
        int[] tokenindices = new int[tokens.length];
        int tokenIndex = 0;
        int neWordIndex = 0;
        for (int i = 0; i < ta.getNumberOfSentences(); i++) {
            Sentence sentence = ta.getSentence(i);
            String[] wtoks = sentence.getTokens();
            LinkedVector words = new LinkedVector();
            for (String w : wtoks) {
                if (w.length() > 0) {
                    NEWord.addTokenToSentence(words, w, "unlabeled");
                    tokenindices[neWordIndex] = tokenIndex;
                    neWordIndex++;
                } else {
                    //logger.error("Bad (zero length) token.");
                }
                tokenIndex++;
            }
            if (words.size() > 0)
                sentences.add(words);
        }

        // Do the annotation.
        Data data = new Data(new NERDocument(sentences, "input"));
        try {

            NETaggerLevel1 tagger1 = new NETaggerLevel1(modelPath + ".level1", modelPath + ".level1.lex");

            Decoder.annotateDataBIO(data, tagger1, null);

        } catch (Exception e) {
            System.out.println("COULD NOT TAG");
            return;
        }

        // now we have the parsed entities, construct the view object.
        ArrayList<LinkedVector> nerSentences = data.documents.get(0).sentences;
        SpanLabelView nerView = new SpanLabelView(viewName, ta);

        // the data always has a single document
        // each LinkedVector in data corresponds to a sentence.
        int tokenoffset = 0;
        for (LinkedVector vector : nerSentences) {
            boolean open = false;

            // there should be a 1:1 mapping btw sentence tokens in record and words/predictions
            // from NER.
            int startIndex = -1;
            String label = null;
            for (int j = 0; j < vector.size(); j++, tokenoffset++) {
                NEWord neWord = (NEWord) (vector.get(j));
                String prediction = neWord.neTypeLevel2;

                // LAM-tlr this is not a great way to ascertain the entity type, it's a bit
                // convoluted, and very
                // inefficient, use enums, or nominalized indexes for this sort of thing.
                if (prediction.startsWith("B-")) {
                    startIndex = tokenoffset;
                    label = prediction.substring(2);
                    open = true;
                } else if (j > 0) {
                    String previous_prediction = ((NEWord) vector.get(j - 1)).neTypeLevel2;
                    if (prediction.startsWith("I-")
                            && (!previous_prediction.endsWith(prediction.substring(2)))) {
                        startIndex = tokenoffset;
                        label = prediction.substring(2);
                        open = true;
                    }
                }

                if (open) {
                    boolean close = false;
                    if (j == vector.size() - 1) {
                        close = true;
                    } else {
                        String next_prediction = ((NEWord) vector.get(j + 1)).neTypeLevel2;
                        if (next_prediction.startsWith("B-"))
                            close = true;
                        if (next_prediction.equals("O"))
                            close = true;
                        if (next_prediction.indexOf('-') > -1
                                && (!prediction.endsWith(next_prediction.substring(2))))
                            close = true;
                    }
                    if (close) {
                        int s = tokenindices[startIndex];

                        /**
                         * MS: fixed bug. Originally, e was set using tokenindices[tokenoffset], but
                         * tokenoffset can reach tokens.length) and this exceeds array length.
                         * Constituent constructor requires one-past-the-end token indexing,
                         * requiring e > s. Hence the complicated setting of endIndex/e below.
                         */

                        int endIndex = Math.min(tokenoffset + 1, tokens.length - 1);
                        int e = tokenindices[endIndex];
                        if (e <= s)
                            e = s + 1;

                        nerView.addSpanLabel(s, e, label, 1d);
                        open = false;
                    }
                }
            }
        }
        ta.addView(viewName, nerView);
    }


    public static String train(String config, String trainroot) throws Exception {

        Parameters.readConfigAndLoadExternalData(config, true);
        String modelpath = "tmp"; //ParametersForLbjCode.currentParameters.pathToModelFile;
        if(modelpath.startsWith("tmp") || modelpath.length() == 0){
            Random r = new Random();
            modelpath = "/tmp/nermodel" + r.nextInt();
        }



        int trainiter = 30;
        Data trainData = CODLRunner.loaddata(trainroot, "-c", true);

        Windowize(trainData);

        CODLRunner.config = config;
        CODLRunner.RunTraining(trainData, trainiter, modelpath);
        System.out.println("Trained on: " + trainroot);

        return modelpath;
    }

    public static void TrainAndAnnotate(String config, String trainroot, TreeMap<String, TextAnnotation> tas) throws Exception{
        //String config = "/home/mayhew/IdeaProjects/cogcomp-nlp/ner/config/tacl/bn.config";
        //String trainroot = "/shared/corpora/ner/lorelei/bn/Train-anno-urom-annotation-stephen";

        String modelpath = train(config, trainroot);
        for(TextAnnotation ta : tas.values()) {
            annotate2(ta, modelpath);
        }

    }


    public static void main(String[] args) throws Exception {
    }
}
