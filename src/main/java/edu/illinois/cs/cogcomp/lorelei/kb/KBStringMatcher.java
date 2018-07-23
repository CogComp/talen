package edu.illinois.cs.cogcomp.lorelei.kb;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This is Stephen Doing Stuff.
 * Created by mayhew2 on 6/8/17.
 * updated by Mark Sammons
 */
public class KBStringMatcher {
    public static void main(String[] args) throws IOException, ParseException {
        //String dir = "/shared/corpora/corporaWeb/lorelei/data/kb/LDC2017E19_LORELEI_EDL_Knowledge_Base_V0.1/data/";
        //String fname = "entities.tab";
        String kbpath = "/shared/corpora/cddunca2/allCountriesHeader.txt";
        String indexpath = "/shared/experiments/mayhew2/indices/allcountries-lucene/";

        //buildindex(kbpath, indexpath);
        //testindex(indexpath);

        //stringmatcher("/shared/corpora/ner/eval/submission/ner/cp3/ensemble3.tab.uly.short", indexpath);
        stringmatcher("/shared/corpora/edl/lorelei/amh-anno-all.txt", indexpath);
    }

    private static Analyzer analyzer = new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new NGramTokenizer(2,2);
            return new TokenStreamComponents(source);
        }
    };


    public static Document populateDoc(String[] headerfields, String[] sline){
        Document d = new Document();

        for (int i = 0; i < sline.length; i++) {
            String field = headerfields[i];
            String value = sline[i];

            //                    StringReader sr = new StringReader(value);
            StringField sf = new StringField(field, value, Field.Store.YES);
            d.add(sf);

            Reader reader = new StringReader(value);
            TextField tf = new TextField(field, reader);
            d.add(tf);

            try {
                if (field.equals("latitude")) {
                    double lat = Double.parseDouble(value);
                    double lon = Double.parseDouble(sline[i + 1]);
                    LatLonPoint llp = new LatLonPoint("latlon", lat, lon);
                    d.add(llp);
                }
            } catch (NumberFormatException e) {
                // just don't do anything...
            }
        }

        return d;
    }

    public static void buildindex(String kbpath, String indexDir) throws IOException {

        FSDirectory dir = FSDirectory.open(Paths.get(indexDir));

        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        IndexWriter writer = new IndexWriter(dir, config);

        int j = 0;
        String[] headerfields = new String[10]; // just pick a random num...
        try (BufferedReader br = new BufferedReader(new FileReader(kbpath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // process the line.
                if(j == 0){
                    headerfields = line.split("\t");
                    j++;
                    continue;
                }

                if (j % 1000 == 0) {
                    System.out.println("Progress: " + j / 11000000.);
                }
                j++;

                String[] sline = line.split("\t");

                Document d = populateDoc(headerfields,sline);
                writer.addDocument(d);

                String[] altnames = sline[3].split(",");
                for(String alt : altnames){
                    sline[1] = alt;
                    sline[2] = alt;

                    Document d2 = populateDoc(headerfields,sline);
                    writer.addDocument(d2);
                }


                //if(j > 10000){break;}
            }
        }

        writer.close();
    }


    public static void stringmatcherconll(String conllfolder){

        CoNLLNerReader cnr;
        TextAnnotation ta;
        for(String fname : (new File(conllfolder)).list()){
            cnr = new CoNLLNerReader(fname);
            ta = cnr.next();

            View ner = ta.getView(ViewNames.NER_CONLL);
            for(Constituent c : ner.getConstituents()){
                // now we will string match all constituents against a KB.
            }

        }
    }

    /**
     * This takes a submission file and links all the entries.
     * @param subfile
     */
    public static void stringmatcher(String subfile, String indexdir) throws IOException, ParseException {

        List<String> lines = LineIO.read(subfile);
        ArrayList<String> outlines = new ArrayList<>();
        ArrayList<String> outlines2 = new ArrayList<>();

        int numcands = 10;
        double i = 0;
        int coverage = 0;
        int nils = 0;

        for(String line : lines){
            if(i % 10 == 0){
                System.out.println("Progress: " + (i / lines.size()));
            }

            i++;

            String[] sline = line.split("\t");

            String mention = sline[2];

            Document[] cands = getcands(mention, indexdir, numcands);

            List<String> candids = new ArrayList<>();
            List<String> candnames = new ArrayList<>();
            for(Document cand : cands){
                candids.add(cand.get("entityid"));
                candnames.add(cand.get("asciiname") + ":" + cand.get("entityid"));
            }
            String candstring = StringUtils.join(candnames, ",");

            String[] sline2 = line.split("\t");
            sline2[4] = candstring;
            outlines2.add(StringUtils.join(sline2, "\t"));

            String goldscore = sline[4];
            if(goldscore.equals("NIL_MENTION_ID")){
                System.out.println("NIL_MENTION_ID gold");
                nils++;
            }else if(candids.contains(goldscore)){
                coverage++;
            }else{
                System.out.println("No cands for: " + mention);
            }

            if(cands.length > 0){
                // this should really be an ID, but for now, it is just this!
                List<Document> reordered = getbest(mention, cands);
                Document best = reordered.get(reordered.size()-1);
                sline[4] = best.get("entityid");
                sline[5] = best.get("asciiname");
            }else{
                sline[4] = "null";
            }

            outlines.add(StringUtils.join(sline, "\t"));

        }

        System.out.println("Coverage: " + coverage/((float)lines.size()-nils));


        LineIO.write(subfile + ".linked", outlines);
        LineIO.write(subfile + ".cands" + numcands, outlines2);

    }

    public static ArrayList<String> getngrams(String s, int n){
        ArrayList<String> ret = new ArrayList<>();
        for(int i = 0; i < s.length()- n+1; i++){
            ret.add(s.substring(i, i+n));
        }
        return ret;
    }

    public static float jaccard(Set<String> a, Set<String> b){
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return inter.size() / (float) union.size();

    }

    private static List<Document> getbest(String mention, Document[] cands) {
        List<String> mentionngrams = getngrams(mention, 2);

        HashMap<Document, Double> docscores = new HashMap<>();

        double mxjaccard = -1;
        double mxalts = -1;
        Document best = cands[0];
        for(Document d : cands){
            String candsurf = d.get("asciiname");

            List<String> candngrams = getngrams(candsurf, 2);
            double jaccard = jaccard(new HashSet<>(mentionngrams), new HashSet<>(candngrams));
            double alts = d.get("name2").split(",").length;
            double score = jaccard; // * alts;

            if(jaccard > mxjaccard){
                mxjaccard = jaccard;
            }
            if(alts > mxalts){
                mxalts = alts;
            }

            docscores.put(d, score);
        }

        double denom = mxjaccard; //mxalts * mxjaccard;

        for(Document d : cands){
            double score = docscores.get(d);
            score = score / denom;
            docscores.put(d, score);
        }

        List<Document> result = docscores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map((e -> e.getKey()))
                .collect(Collectors.toList());

        return result;
    }


    public static void getnearest(IndexSearcher searcher, double lat, double lon) throws IOException {
        // How to query with a lat/long.
        TopFieldDocs tfd = LatLonPoint.nearest(searcher, "latlon", lat, lon, 10);
        ScoreDoc[] hits = tfd.scoreDocs;

        for(int i=0; i<hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);

            FieldDoc fd = (FieldDoc) hits[i];
            double dist = (double)fd.fields[0];

            System.out.println((i + 1) + ". " + d.get("entityid") + ", " + d.get("asciiname") + ", dist (m)=" + dist);
        }
    }

    public static Document[] getcands(String mention, String indexdir, int n) throws IOException {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexdir)));
        IndexSearcher searcher = new IndexSearcher(reader);

        searcher.setSimilarity(new NameSimilarity());

        Query q;
        try {
            q = new QueryParser("asciiname", analyzer).parse(mention);
        } catch (ParseException e) {
            Document[] results = new Document[0];
            return results;
        }

        TopScoreDocCollector collector = TopScoreDocCollector.create(n);
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;

        Document[] results = new Document[hits.length];

        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            results[i] = d;
        }

        return results;
    }


    public static void testindex(String indexdir) throws IOException {
        //=========================================================
        // Now search
        //=========================================================
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexdir)));
        IndexSearcher searcher = new IndexSearcher(reader);

        //searcher.setSimilarity(new NameSimilarity());

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
                if(s.startsWith("p ")) {
                    String[] parts = s.split(" ");
                    getnearest(searcher, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                }else if(s.startsWith("id")){
                    s = s.substring(3);

                    Query q = new QueryParser("entityid", new StandardAnalyzer()).parse(s);

                    TopScoreDocCollector collector = TopScoreDocCollector.create(5);
                    searcher.search(q, collector);
                    ScoreDoc[] hits = collector.topDocs().scoreDocs;

                    if(hits.length > 0) {
                        ScoreDoc best = hits[0];
                        Document d = searcher.doc(best.doc);

                        for(IndexableField f : d.getFields()){
                            System.out.println(f.name() + "\t" + d.get(f.name()));
                        }
                    }

                }else {

                    Query q = new QueryParser("asciiname", analyzer).parse(s);

                    System.out.println(q);
                    TopScoreDocCollector collector = TopScoreDocCollector.create(15);
                    searcher.search(q, collector);
                    ScoreDoc[] hits = collector.topDocs().scoreDocs;

                    System.out.println("There are total of: " + searcher.count(q) + " hits.");

                    // 4. display results
                    System.out.println("Found " + hits.length + " hits.");
                    for (int i = 0; i < hits.length; ++i) {
                        int docId = hits[i].doc;
                        Document d = searcher.doc(docId);

//                    System.out.println(d.getFields());
                        System.out.println((i + 1) + ". " + d.get("entityid") + ", " + d.get("asciiname") + " score=" + hits[i].score);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error searching " + s + " : " + e.getMessage());
            }
        }
        reader.close();
    }
}
