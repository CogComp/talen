package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import io.github.mayhewsw.BootstrapController;
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
 * The sentence cache is a hashmap that maps from sentence ids to actual sentence constituents. All editiing
 * of sentence constituents must be done through this map. All other data structures only store references
 * via sentence ids.
 *
 * Created by mayhew2 on 5/18/17.
 */
public class SentenceCache extends HashMap<String, Constituent> {

    private static Logger logger = LoggerFactory.getLogger(SentenceCache.class);
    private final String folderpath;

    HashMap<String, HashSet<String>> allresults;
    private IndexSearcher searcher;

    public SentenceCache(String folderpath, String indexdir) throws IOException {
        this.folderpath = folderpath;
        this.allresults = new HashMap<>();

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexdir)));
        searcher = new IndexSearcher(reader);
    }

    /**
     * Given a sentence ID, this will check a local cache before going to disk. This reads
     * the entire document and caches all sentences in that document before returning just
     * the requested sentence.
     *
     * This expects sentence ID to be of the form: docname:sentenceindex (see {@link BootstrapController#getSentId(Constituent) getSentId}).
     *
     * @param sentid
     * @return
     * @throws FileNotFoundException
     */
    public Constituent getSentence(String sentid) throws FileNotFoundException {
        if(!this.containsKey(sentid)){
            String fileid = sentid.split(":")[0];

            TextAnnotation ta = new CoNLLNerReader(new File(folderpath, fileid).getAbsolutePath()).next();

            View sentview = ta.getView(ViewNames.SENTENCE);
            for(Constituent sent : sentview.getConstituents()){
                this.put(BootstrapController.getSentId(sent), sent);
            }
        }

        return this.get(sentid);
    }


    private void putQueryResult(String term, HashSet<String> queryids) {
        this.allresults.put(term, queryids);
    }

    /**
     * Get all sentence ids in the corpus which contain this term. If they are not loaded in the cache,
     * this will lazily load them.
     * @param term
     * @return
     * @throws IOException
     */
    public HashSet<String> getAllResults(String term) throws IOException {
        if(this.allresults.get(term) == null){
            load(term);
        }
        return this.allresults.get(term);
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
    public HashSet<String> gatherTopK(String term, HashSet<String> allgroups, int k) throws IOException {
        // at the very least, we need to have all the results from this term.

        // this is what we will return.
        HashSet<String> displaylist = new HashSet<>();

        // this is the full set of sentences containing this term.
        HashSet<String> fulllist = this.getAllResults(term);

        // allgroups will contain only those sentences that contain term, and which are already in groups.
        allgroups.retainAll(fulllist);

        for(String sentid : allgroups){
            if(displaylist.size() >= k) break;
            displaylist.add(sentid);
        }

        // put a limit on the top num of sentences in groups.
        if(allgroups.size() > 500){
            logger.info("Num sentences in groups has reached the limit. Not reading any more...");
            return displaylist;
        }

        // the set of already loaded sentences is the keys of the cache.
        HashSet<String> loadedsents = new HashSet<>(this.keySet());

        // loadedsents will contain only those sentences which contain term, and which are already loaded.
        loadedsents.retainAll(fulllist);

        for(String sentid : loadedsents){
            if(displaylist.size() >= k) break;
            displaylist.add(sentid);
        }

        //  TODO: put a limit here also??

        for(String sentid : fulllist){
            if(displaylist.size() >= k) break;
            displaylist.add(sentid);
        }

        return displaylist;
    }

    /**
     * This loads a term from the index, and puts it into the query result datastructure.
     * @param term
     * @throws IOException
     */
    private void load(String term) throws IOException {

        //Query q = new QueryParser("body", analyzer).parse("\"" + query + "\"*");
        Query query = new PrefixQuery(new Term("body", term));

        // Assume a large text collection. We want to store EVERY SINGLE INSTANCE.
        int k = Integer.MAX_VALUE;
        TopDocs searchresults = searcher.search(query, k);
        ScoreDoc[] hits = searchresults.scoreDocs;

        HashSet<String> queryids = new HashSet<>();

        for (int i = 0; i < hits.length; ++i) {
            int luceneId = hits[i].doc;
            Document d = searcher.doc(luceneId);

            String sentid = d.get("filename");

            // This avoids have discussion forum results (which can be noisy) and huge files.
            int sentind = Integer.parseInt(sentid.split(":")[1]);
            if(sentid.contains("_DF_") || sentind > 200){
                continue;
            }

            queryids.add(sentid);
        }

        this.putQueryResult(term, queryids);
    }
}
