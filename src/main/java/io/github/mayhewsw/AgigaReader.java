package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import edu.illinois.cs.cogcomp.nlp.tokenizer.Tokenizer;
import edu.jhu.agiga.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mayhew2 on 4/11/17.
 */
public class AgigaReader {

    private static Analyzer analyzer = new SimpleAnalyzer();

    public static void buildindex(String indexDir) throws IOException {

        FSDirectory dir = FSDirectory.open(Paths.get(indexDir));

        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        IndexWriter writer = new IndexWriter(dir, config);

        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setWord(true);
        //prefs.setNer(true);

        File nytdir = new File("/shared/preprocessed/upadhya3/anno_eng_gigaword_5/data/xml/");

        int i =0;

        File[] files = nytdir.listFiles(
                (d, name) -> {
                    return name.contains("nyt_eng");
                }
        );

        System.out.println("There are "+ files.length  + " in this folder.");

        for(File file : files) {
            if (!file.getName().contains("nyt_eng")) {
                continue;
            }

            String path = file.getAbsolutePath();

            StreamingDocumentReader reader = new StreamingDocumentReader(path, prefs);

            for (AgigaDocument doc : reader) {
                if(i %100== 0){
                    System.out.println(i);
                }
                i++;
                //System.out.println(doc.getDocId());
                String text = "";
                for (AgigaSentence sent : doc.getSents()) {
                    for (AgigaToken tok : sent.getTokens()) {
                        text += tok.getWord() + " ";

                    }
                    text = text.trim();
                    text += "\n";

                }
                text = text.trim();

                Document d = new Document();
                StringReader sr = new StringReader(text);
                d.add(new TextField("body", sr));
                d.add(new StringField("filename", doc.getDocId(), Field.Store.YES));
                writer.addDocument(d);

            }
            System.out.println("zip had " + reader.getNumDocs() + " docs.");

            if(i > 1000000){
                break;
            }
        }
        writer.close();

    }

    public static void getnerdocs() throws IOException {
        AgigaPrefs prefs = new AgigaPrefs();
        prefs.setAll(false);
        prefs.setWord(true);
        prefs.setNer(true);

        File nytdir = new File("/shared/preprocessed/upadhya3/anno_eng_gigaword_5/data/xml/");

        int i =0;

        File[] files = nytdir.listFiles(
                (d, name) -> {
                    return name.contains("nyt_eng_201");
                }
        );

        System.out.println("There are "+ files.length  + " in this folder.");

        for(File file : files) {

            String path = file.getAbsolutePath();

            StreamingDocumentReader reader = new StreamingDocumentReader(path, prefs);

            for (AgigaDocument doc : reader) {
                if(i %100== 0){
                    System.out.println(i);
                }
                i++;
                List<String> outlines = new ArrayList<>();

                for (AgigaSentence sent : doc.getSents()) {
                    int tokid = 1;
                    String prevtag = "O";
                    for (AgigaToken tok : sent.getTokens()) {
                        String tag = tok.getNerTag();

                        if(tag.equals("PERSON")){
                            if(prevtag.contains("PER"))
                                tag ="I-PER";
                            else
                                tag = "B-PER";
                        }else if(tag.equals("ORGANIZATION")){
                            if(prevtag.contains("ORG"))
                                tag ="I-ORG";
                            else
                                tag = "B-ORG";
                        }else if(tag.equals("LOCATION")){
                            if(prevtag.contains("LOC"))
                                tag ="I-LOC";
                            else
                                tag = "B-LOC";
                        }else{
                            tag = "O";
                        }

                        if(tag.equals("x")){
                            continue;
                        }

                        outlines.add(String.format("%s\t0\t%d\tx\tx\t%s\tx\tx\t0", tag, tokid++, tok.getWord()));
                        prevtag = tag;
                    }
                    outlines.add("");
                }
                LineIO.write("/shared/corpora/ner/gigaword/" + doc.getDocId(), outlines);
                if(i > 1500){
                    break;
                }
            }

            System.out.println("zip had " + reader.getNumDocs() + " docs.");

            if(i > 1500){
                break;
            }
        }
    }



    public static void main(String[] args) throws IOException {

        getnerdocs();

    }


}
