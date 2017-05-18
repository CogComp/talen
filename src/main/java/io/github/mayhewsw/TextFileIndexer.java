package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * This terminal application creates an Apache Lucene index in a folder and adds files into this index
 * based on the input of the user.
 */
public class TextFileIndexer {

    private static Analyzer analyzer = new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new WhitespaceTokenizer();
            TokenStream filter = new ShingleFilter(source);
            return new TokenStreamComponents(source, filter);
        }

    };

    /**
     * Filedir holds the conll files that are to be indexed.
     * @param filedir
     * @param indexDir
     * @throws IOException
     */
    public static void buildindex(String filedir, String indexDir) throws IOException {

        FSDirectory dir = FSDirectory.open(Paths.get(indexDir));

        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        IndexWriter writer = new IndexWriter(dir, config);

        TextAnnotation ta;
        File file = new File(filedir);

        for(File fname : file.listFiles()){
            // read each file separately...
            CoNLLNerReader cnr = new CoNLLNerReader(fname.getAbsolutePath());
            ta = cnr.next();

            StringReader sr = new StringReader(ta.getTokenizedText());

            Document d = new Document();
            d.add(new TextField("body", sr));
            d.add(new StringField("filename", ta.getId(), Field.Store.YES));
            writer.addDocument(d);
        }


        writer.close();

    }


    public static void buildsentenceindex(String conlldir, String indexDir) throws IOException {
        // we write to this open file object.

        FSDirectory dir = FSDirectory.open(Paths.get(indexDir));

        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        IndexWriter writer = new IndexWriter(dir, config);

        TextAnnotation ta;
        File file = new File(conlldir);

        for(File fname : file.listFiles()){
            CoNLLNerReader cnr = new CoNLLNerReader(fname.getAbsolutePath());

            ta = cnr.next();
            View sentview = ta.getView(ViewNames.SENTENCE);
            List<Constituent> sentences = sentview.getConstituents();

            for(Constituent sent : sentences){
                StringReader sr = new StringReader(sent.getTokenizedSurfaceForm());

                Document d = new Document();
                TextField tf = new TextField("body", sr);
                d.add(tf);
                d.add(new StringField("filename", BootstrapController.getSentId(sent), Field.Store.YES));
                writer.addDocument(d);
            }
        }

        writer.close();
    }


    public static void main(String[] args) throws IOException {
        String filedir = "/shared/corpora/ner/eval/column/mono-all-uly/";
        String indexdir = "/shared/corpora/ner/eval/column/mono-all-uly-indexsent/";

//        buildsentenceindex(filedir, indexdir);
//
//        if(true) return;

        //=========================================================
        // Now search
        //=========================================================
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexdir)));
        IndexSearcher searcher = new IndexSearcher(reader);


        BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in));

        String s = "";
        while (!s.equalsIgnoreCase("q")) {
            try {
                System.out.println("Enter the search query (q=quit): ");
                s = br.readLine();
                if (s.equalsIgnoreCase("q")) {
                    break;
                }
//                Query q = new QueryParser("body", analyzer).parse(s);

                Query q = new PrefixQuery(new Term("body", s));

                System.out.println(q);
                TopScoreDocCollector collector = TopScoreDocCollector.create(5);
                searcher.search(q, collector);
                ScoreDoc[] hits = collector.topDocs().scoreDocs;

                System.out.println("There are total of: " + searcher.count(q) + " hits.");

                // 4. display results
                System.out.println("Found " + hits.length + " hits.");
                for(int i=0; i<hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d = searcher.doc(docId);
                    System.out.println((i + 1) + ". " + d.get("filename") + " score=" + hits[i].score);
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error searching " + s + " : " + e.getMessage());
            }
        }

        reader.close();

    }



}
