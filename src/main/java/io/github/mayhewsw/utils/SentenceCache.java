package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import io.github.mayhewsw.controllers.SentenceController;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


/**
 *
 * The sentence cache is a hashmap that maps from sentence ids to actual sentence constituents. All editing
 * of sentence constituents must be done through this map. All other data structures only store references
 * via sentence ids.
 *
 * Created by mayhew2 on 5/18/17.
 */
public class SentenceCache extends HashMap<String, Constituent> {

    private static Logger logger = LoggerFactory.getLogger(SentenceCache.class);
    private final String folderpath;

    HashMap<String, HashSet<String>> allresults;
    HashMap<String, HashSet<String>> alltexts;
    public HashMap<String, String> sentid2text;
    public HashMap<String, String> sentid2origtext;
    private IndexSearcher searcher;

    public SentenceCache(String folderpath, String indexdir) throws IOException {
        this.folderpath = folderpath;
        this.allresults = new HashMap<>();
        this.alltexts = new HashMap<>();
        this.sentid2text = new HashMap<>();
        this.sentid2origtext = new HashMap<>();


        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexdir)));
        searcher = new IndexSearcher(reader);
    }

    /**
     * Given a sentence ID, this will check a local cache before going to disk. This reads
     * the entire document and caches all sentences in that document before returning just
     * the requested sentence.
     *
     * This expects sentence ID to be of the form: docname:sentenceindex (see {@link SentenceController#getSentId(Constituent) getSentId}).
     *
     * @param sentid
     * @return
     * @throws FileNotFoundException
     */
    public Constituent getSentence(String sentid) throws Exception {
        if(!this.containsKey(sentid)){
            String fileid = sentid.split(":")[0];

            //TextAnnotation ta = new CoNLLNerReader(new File(folderpath, fileid).getAbsolutePath()).next();
            TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(new File(folderpath, fileid).getAbsolutePath(), true);

            // I got tired of checking every TA to see if it has the NER_CONLL view...
            if(!ta.hasView(ViewNames.NER_CONLL)){
                View ner = new View(ViewNames.NER_CONLL, "SentenceController",ta,1.0);
                ta.addView(ViewNames.NER_CONLL, ner);
            }

            View sentview = ta.getView(ViewNames.SENTENCE);
            for(Constituent sent : sentview.getConstituents()){
                String id = SentenceController.getSentId(sent);
                if(!this.containsKey(id)) {
                    this.put(id, sent);
                }
            }
        }

        return this.get(sentid);
    }


    private void putQueryResult(String term, HashSet<String> queryids) {
        this.allresults.put(term, queryids);
    }


    public HashSet<String> getAllResults(String term) throws IOException {
        return getAllResults(term, false);
    }

    /**
     * Get all sentence ids in the corpus which contain this term. If they are not loaded in the cache,
     * this will lazily load them.
     * @param term
     * @return
     * @throws IOException
     */
    public HashSet<String> getAllResults(String term, boolean exact) throws IOException {
        if(this.allresults.get(term) == null){
            load(term, exact);
        }
        return this.allresults.get(term);
    }

    public HashSet<String> getAllTexts(String term, boolean exact) throws IOException {
        if(this.alltexts.get(term) == null){
            load(term, exact);
        }
        return this.alltexts.get(term);
    }

    
    public Set<String> getAllKeys(){
        return this.allresults.keySet();
    }

    /**
     * Gather the display list for term, prioritized according to already loaded sentences
     * and sentences already in groups.
     * @param term
     * @param allgroups
     * @param k  @return
     * @throws IOException
     */
    public HashSet<String> gatherTopK(String term, HashSet<String> allgroups, int k) throws Exception {
        // at the very least, we need to have all the results from this term.

        // copy allgroups so we never modify it.
        HashSet<String> allgroupsintersection = new HashSet<>(allgroups);

        // this is what we will return.
        HashSet<String> displaylist = new HashSet<>();

        // this is the full set of sentences containing this term.
        HashSet<String> fulllist = this.getAllResults(term);

        // allgroups will contain only those sentences that contain term, and which are already in groups.
        allgroupsintersection.retainAll(fulllist);

        for(String sentid : allgroupsintersection){
            if(displaylist.size() >= k) break;
            displaylist.add(sentid);
        }

        // put a limit on the top num of sentences in groups.
        if(allgroups.size() > 300){
            logger.info("Num sentences in groups has reached the limit before term: {}. Not reading any more...", term);
            return displaylist;
        }

        // the set of already loaded sentences is the keys of the cache.
        HashSet<String> loadedsents = new HashSet<>(this.keySet());

        // loadedsents will contain only those sentences which contain term, and which are already loaded.
        loadedsents.retainAll(fulllist);

        int maxsentsize = 50;

        for(String sentid : loadedsents){
            if(displaylist.size() >= k) break;

            if(this.getSentence(sentid).size() > maxsentsize){
                //logger.debug("Skipping {} because size is {}", sentid, this.getSentence(sentid).size());
                continue;
            }


            displaylist.add(sentid);
        }

        //  TODO: put a limit here also??

        for(String sentid : fulllist){
            if(displaylist.size() >= k) break;

            // don't include sentences with more than maxsentsize tokens
            if(this.getSentence(sentid).size() > maxsentsize){
                //logger.debug("Skipping {} because size is {}", sentid, this.getSentence(sentid).size());
                continue;
            }

            logger.debug("Adding {} with size {}", sentid, this.getSentence(sentid).size());
            displaylist.add(sentid);
        }

        return displaylist;
    }


    private void load(String term) throws IOException {
        load(term, false);
    }

    /**
     * This searches for exact phrases.
     * @param term
     * @return
     */
    public int count(String term) throws IOException {
        Query query = new TermQuery(new Term("body", term));
        return searcher.count(query);
    }

    /**
     * This loads a term from the index, and puts it into the query result datastructure.
     * @param term
     * @throws IOException
     */
    private void load(String term, boolean exact) throws IOException {

        // if it's not exact, it searches for prefix queries.
        Query query;
        if(exact) {
            query = new TermQuery(new Term("body", term));
        }else {
            query = new PrefixQuery(new Term("body", term));
        }

        // Assume a large text collection. We want to store EVERY SINGLE INSTANCE.
        int k = Integer.MAX_VALUE;
        TopDocs searchresults = searcher.search(query, k);
        ScoreDoc[] hits = searchresults.scoreDocs;

        HashSet<String> queryids = new HashSet<>();
        //HashSet<String> querytexts = new HashSet<>();

        for (int i = 0; i < hits.length; ++i) {
            int luceneId = hits[i].doc;
            Document d = searcher.doc(luceneId);

            String sentid = d.get("filename");

            // This avoids have discussion forum results (which can be noisy) and huge files.
            int sentind = Integer.parseInt(sentid.split(":")[1]);
            if(sentid.contains("_DF_") || sentind > 200){
                continue;
            }

            //querytexts.add(d.get("body"));
            queryids.add(sentid);
            //this.sentid2text.put(sentid, d.get("body"));
            //this.sentid2origtext.put(sentid, d.get("origbody"));
        }

        //logger.debug("Found {} results for term {}", queryids.size(), term);

        this.putQueryResult(term, queryids);
        this.alltexts.put(term, queryids);
    }
}
