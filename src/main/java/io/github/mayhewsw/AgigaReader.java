package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import edu.jhu.agiga.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;

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

    public static void lucenetest() throws IOException {
        // we write to this open file object.
        RAMDirectory rd = new RAMDirectory();
        Analyzer analyzer =
                new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer source = new WhitespaceTokenizer();
                TokenStream filter = new ShingleFilter(source);
                //TokenStream filter2 = new NGramTokenFilter(filter, 1, 4);
                return new TokenStreamComponents(source, filter);
            }
        };


        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);

        IndexWriter writer = new IndexWriter(rd, cfg);

        List<String> strings = new ArrayList<>();
        strings.add("i am a monster from venezuela");
        strings.add("Britain is my hobby i'm victoria");
        strings.add("unconcerned and unashamed");
        strings.add("does anybody hear me?");
        strings.add("i like sugar and other things related to John Smith");

        for(String s : strings){

            StringReader sr = new StringReader(s);

            Document d = new Document();
            TextField tf = new TextField("body", sr);

            d.add(tf);
            d.add(new StringField("filename", s.substring(0,4), Field.Store.YES));
            writer.addDocument(d);
        }
        writer.close();

        IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(rd));

        String query = "like sug";

        //Query q = new QueryParser("body", analyzer).parse("\"" + query + "\"*");
        Query q = new PrefixQuery(new Term("body", query));
        System.out.println("query is " + q);

        TopScoreDocCollector collector = TopScoreDocCollector.create(20);
        searcher.search(q, collector);

        ScoreDoc[] hits = collector.topDocs().scoreDocs;

        System.out.println("Found " + hits.length + " hits.");
        for (int i = 0; i < hits.length; ++i) {
            int luceneId = hits[i].doc;
            Document d = searcher.doc(luceneId);

            System.out.println(d);

        }

    }


    public static void main(String[] args) throws IOException {

        //getnerdocs();

        lucenetest();
    }


}
