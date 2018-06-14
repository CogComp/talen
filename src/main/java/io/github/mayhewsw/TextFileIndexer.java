package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import io.github.mayhewsw.controllers.SentenceController;
import org.apache.commons.cli.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

import java.io.*;
import java.nio.file.Paths;
import java.util.List;
import java.util.StringJoiner;

/**
 * This terminal application creates an Apache Lucene index in a folder and adds files into this index
 * based on the input of the user.
 */
public class TextFileIndexer {

    private static Analyzer analyzer = new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new WhitespaceTokenizer();
            TokenStream filter = new ShingleFilter(source, 6);
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


    /**
     * This reads documents one at a time, and builds an index of sentences.
     * @param inpath
     * @param indexDir
     * @throws IOException
     */
    public static void buildsentenceindex(String inpath, String indexDir) throws Exception {

        FSDirectory dir = FSDirectory.open(Paths.get(indexDir));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(dir, config);

        File tapath = new File(inpath);
        File[] filelist = tapath.listFiles();
        for(File f : filelist){
            TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(f.getAbsolutePath(), true);



            View sentview = ta.getView(ViewNames.SENTENCE);
            List<Constituent> sentences = sentview.getConstituents();

            View translit = null;
            if(ta.hasView(ViewNames.TRANSLITERATION)){
                translit = ta.getView(ViewNames.TRANSLITERATION);
            }

            for(int i = 0; i < sentences.size(); i++){
                Constituent sent = sentences.get(i);

                Document d = new Document();

                String rom = sent.getTokenizedSurfaceForm();

                if(translit != null) {
                    StringJoiner sb = new StringJoiner(" ");
                    for(Constituent c : translit.getConstituentsCoveringSpan(sent.getStartSpan(), sent.getEndSpan())){
                        sb.add(c.getLabel());
                    }
                    rom = sb.toString();
                }

                TextField tf = new TextField("body", rom, Field.Store.YES);
                //TextField tf = new TextField("body", sr, Field.Store.YES);
                d.add(tf);

                d.add(new StringField("filename", SentenceController.getSentId(sent), Field.Store.YES));


                TextField origtf = new TextField("origbody", sent.getTokenizedSurfaceForm(), Field.Store.YES);
                d.add(origtf);


                writer.addDocument(d);
            }
        }

        writer.close();
    }

    /**
     * A simple function to test the index that you built using buildsentenceindex.
     * @param indexdir
     */
    public static void testindex(String indexdir) throws IOException {
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
                QueryParser parser = new QueryParser("body", analyzer);
                parser.setAllowLeadingWildcard(true);

                //Query q = parser.parse("*" + s + "*");

                Query q = new PrefixQuery(new Term("body", s));

                System.out.println(q);
                TopScoreDocCollector collector = TopScoreDocCollector.create(40);
                searcher.search(q, collector);
                ScoreDoc[] hits = collector.topDocs().scoreDocs;

                //System.out.println("There are total of: " + searcher.count(q) + " hits.");

                // 4. display results
                System.out.println("Found " + hits.length + " hits.");
                for(int i=0; i<hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d = searcher.doc(docId);

                    String[] b = d.get("body").split(" ");
                    String[] ob = d.get("origbody").split(" ");

                    for(int j = 0; j < b.length; j++){
                        if(b[j].contains(s)){
                            System.out.println(b[j] + " " + ob[j]);
                        }
                    }

                    //System.out.println((i + 1) + ". " + d.get("body") + " score=" + hits[i].score);
                    //System.out.println((i + 1) + ". " + d.get("origbody") + " score=" + hits[i].score);
                    //System.out.println((i + 1) + ". " + d.get("filename") + " score=" + hits[i].score);

                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error searching " + s + " : " + e.getMessage());
            }
        }

        reader.close();
    }

    public static void main(String[] args) throws Exception {

        Options options = new Options();
        Option help = new Option( "help", "print this message" );


        Option infolder = Option.builder("infolder")
                .hasArg()
                .required()
                .build();

        Option indexfolder = Option.builder("indexfolder")
                .hasArg()
                .required()
                .build();

        Option testopt = Option.builder("test")
                .desc("Whether or not to test the index after creation")
                .build();

        options.addOption(help);
        options.addOption(infolder);
        options.addOption(indexfolder);
        options.addOption(testopt);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        buildsentenceindex(cmd.getOptionValue("infolder"), cmd.getOptionValue("indexfolder"));

        if(cmd.hasOption("test")) {
            testindex(cmd.getOptionValue("indexfolder"));
        }
    }


}



