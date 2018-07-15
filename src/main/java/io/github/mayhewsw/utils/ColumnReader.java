package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.annotation.BasicTextAnnotationBuilder;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.*;
import edu.illinois.cs.cogcomp.core.io.IOUtils;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.AnnotationReader;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CorpusReaderConfigurator;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * A local clone of CoNLLNerReader that reads column files. This format is: word tag (with other entries possibly
 * allowed in the middle). Tag can be BIO format.
 */
public class ColumnReader extends AnnotationReader<TextAnnotation> {

    private static Logger logger = LoggerFactory.getLogger(ColumnReader.class);
    private List<TextAnnotation> textAnnotations;
    private int taCounter;

    /**
     * This expects a directory that contains conll format files.
     *
     * @param conlldirectory
     */
    public ColumnReader(String conlldirectory) {
        super(CorpusReaderConfigurator.buildResourceManager("NER_CONLL", conlldirectory, conlldirectory, ".conll", ".conll"));
        this.taCounter = 0;
    }

    /**
     * Convenience function to avoid having to think about this format all the time...
     *
     * @param tag
     * @param word
     * @return
     */
    public static String columnline(String tag, String word) {
        return String.format("%s %s", word, tag);
    }

    /**
     * Converts a list of annotated TextAnnotation into CoNLL format. Must have NER_CONLL view. This
     * writes each TextAnnotation to it's own file, using the sentence view as a guide for
     * sentences. This will requires that each TextAnnotation have a non-null ID, which will be the
     * name of the conll file for that TextAnnotation.
     *
     * @param tas
     * @param outpath
     * @throws IOException
     */
    public static void TaToColumn(List<TextAnnotation> tas, String outpath) throws IOException {

        for (TextAnnotation ta : tas) {
            List<String> talines = new ArrayList<>();

            View sentview = ta.getView(ViewNames.SENTENCE);
            View nerview = ta.getView(ViewNames.NER_CONLL);
            for (int i = 0; i < ta.getTokens().length; i++) {

                // Default "outside" label in NER_CONLL
                String label = "O";

                List<Constituent> constituents = nerview.getConstituentsCoveringToken(i);

                // should be just one constituent
                if (constituents.size() > 0) {
                    Constituent c = constituents.get(0);
                    if (c.getStartSpan() == i) {
                        label = "B-" + c.getLabel();
                    } else {
                        label = "I-" + c.getLabel();
                    }

                    if (constituents.size() > 1) {
                        logger.error("More than one label -- selecting the first.");
                        logger.error("Constituents: " + constituents);
                    }
                }
                talines.add(columnline(label, ta.getToken(i)));
                List<Constituent> sents = sentview.getConstituentsCoveringToken(i);
                if (sents.size() > 0) {
                    Constituent sent = sents.get(0);

                    int end = sent.getEndSpan();
                    if (i == end - 1) {
                        talines.add("");
                    }

                    if (sents.size() > 1) {
                        logger.error("More than one sentence constituent for this token -- selecting the first.");
                        logger.error("Sentences: " + sents);
                    }
                }
            }

            FileUtils.writeLines(Paths.get(outpath, ta.getId()).toFile(), talines);
        }

    }

    /**
     * This loads filename into a textannotation.
     *
     * @param filename
     * @return
     * @throws FileNotFoundException
     */
    public static TextAnnotation loadColumnfile(String filename) throws FileNotFoundException {
        logger.info("Reading: " + filename);
        List<String> lines = LineIO.read(filename);

        List<IntPair> spans = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> sentenceEndPositions = new ArrayList<>();

        StringBuilder text = new StringBuilder();

        int start = -1;
        String label = "";

        int i = 0;
        for (String line : lines) {
            String[] sline = line.split(" ");
            String word = sline[0];
            String tag = sline[sline.length-1];

            if (tag.startsWith("B-") || tag.startsWith("U-")) {
                // two consecutive entities.
                if (start > -1) {
                    // peel off a constituent if it exists.
                    spans.add(new IntPair(start, i));
                    labels.add(label);
                }

                start = i;
                label = tag.split("-")[1];

            } else if (tag.startsWith("I-")) {
                // don't do anything....
            } else {
                // this is a sentence boundary.
                if (line.trim().length() == 0) {
                    // perhaps this is i+1?
                    // in case there are multiple empty lines at the end.
                    if (!sentenceEndPositions.contains(i) && i > 0) {
                        sentenceEndPositions.add(i);
                    }
                }

                // it's O or it's empty
                if (start > -1) {
                    // peel off a constituent if it exists.
                    spans.add(new IntPair(start, i));
                    labels.add(label);
                }

                label = "";
                start = -1;
            }

            // add the word form to the sentence.
            if (sline.length > 0 && !word.equals("-DOCSTART-") && word.trim().length() > 0) {
                text.append(word + " ");
                i++;
            }
        }

        // in case the very last line is an NE.
        if (start > -1) {
            spans.add(new IntPair(start, i));
            labels.add(label);
        }

        // in case there are no empty lines.
        if (!sentenceEndPositions.contains(i)) {
            sentenceEndPositions.add(i);
        }

        // we jump through these hoops so we can give the TA an id.
        String filenameonly = IOUtils.getFileName(filename);
        List<String[]> tokenizedSentences = Collections.singletonList(text.toString().split(" "));
        TextAnnotation ta =
                BasicTextAnnotationBuilder.createTextAnnotationFromTokens("", filenameonly,
                        tokenizedSentences);

        SpanLabelView sentview = new SpanLabelView(ViewNames.SENTENCE, "UserSpecified", ta, 1d);
        ta.addView(ViewNames.SENTENCE, sentview);

        int sentstart = 0;
        for (int s : sentenceEndPositions) {
            sentview.addSpanLabel(sentstart, s, ViewNames.SENTENCE, 1d);
            sentstart = s;
        }

        SpanLabelView emptyview = new SpanLabelView(ViewNames.NER_CONLL, "UserSpecified", ta, 1d);
        ta.addView(ViewNames.NER_CONLL, emptyview);

        for (int k = 0; k < labels.size(); k++) {
            label = labels.get(k);
            IntPair span = spans.get(k);
            Constituent c =
                    new Constituent(label, ViewNames.NER_CONLL, ta, span.getFirst(),
                            span.getSecond());
            emptyview.addConstituent(c);
        }

        return ta;
    }

    @Override
    protected void initializeReader() {
        String[] files = new String[0];
        this.textAnnotations = new ArrayList<>();

        String corpusdirectory =
                this.resourceManager.getString(CorpusReaderConfigurator.SOURCE_DIRECTORY.key);

        // In case the input argument is a single file
        if (!IOUtils.isDirectory(corpusdirectory)) {
            files = new String[] {corpusdirectory};
        } else {
            try {
                files = IOUtils.ls(corpusdirectory);
                Arrays.sort(files);
                for (int i = 0; i < files.length; i++) {
                    files[i] = Paths.get(corpusdirectory, files[i]).toString();
                }
            } catch (IOException e) {
                logger.error("Error listing directory.");
                logger.error(e.getMessage());
            }
        }
        try {
            for (String file : files) {
                textAnnotations.add(loadColumnfile(file));
            }
        } catch (IOException e) {
            logger.error("Error reading file.");
            logger.error(e.getMessage());
        }
    }

    @Override
    public boolean hasNext() {
        return textAnnotations.size() > taCounter;
    }

    /**
     * return the next annotation object. Don't forget to increment currentAnnotationId.
     *
     * @return an annotation object.
     */
    @Override
    public TextAnnotation next() {
        return textAnnotations.get(taCounter++);
    }

    /**
     * TODO: generate a human-readable report of annotations read from the source file (plus whatever
     * other relevant statistics the user should know about).
     */

    public String generateReport() {
        throw new UnsupportedOperationException("ERROR: generateReport() Not yet implemented.");
    }

}
