package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;

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


    public static void main(String[] args) throws IOException {
        String indexdir = "/tmp/nytindex";
        String filedir = "/shared/corpora/ner/human/ug/conll-uly/";
//        buildindex(filedir, indexdir);

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
                Query q = new QueryParser("body", analyzer).parse(s);

                System.out.println(q);
                TopScoreDocCollector collector = TopScoreDocCollector.create(5);
                searcher.search(q, collector);
                ScoreDoc[] hits = collector.topDocs().scoreDocs;

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
