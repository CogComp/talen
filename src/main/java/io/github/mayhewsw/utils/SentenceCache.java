package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import io.github.mayhewsw.BootstrapController;
import io.github.mayhewsw.TempConllReader;
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

import static com.sun.corba.se.impl.util.RepositoryId.cache;

/**
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
            TextAnnotation ta = TempConllReader.loadCoNLLfile(new File(folderpath, fileid).getAbsolutePath());

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
     * @param k
     * @return
     * @throws IOException
     */
    public HashSet<Constituent> gatherTopK(String term, int k) throws IOException {
        // at the very least, we need to have all the results from this term.

        // the set of already loaded sentences is the keys of the cache.
        HashSet<String> loadedsents = new HashSet<>(this.keySet());

        HashSet<Constituent> displaylist = new HashSet<>();



        HashSet<String> fulllist = this.getAllResults(term);

        loadedsents.retainAll(fulllist);

        // now loadedsents contains only those sentences which contain term, and which are already loaded.

        //  TODO: prioritize by sentences in DISPLAY LISTS already (which is a subset of all loaded sentences).

        for(String sentid : loadedsents){
            if(displaylist.size() >= k) break;
            displaylist.add(this.getSentence(sentid));
        }
        
        // put a limit on the top num of loadedsents.
        if(this.keySet().size() > 5000){
            logger.info("Cache loaded sentences has reached the limit. Not reading any more...");
            return displaylist;
        }

        for(String sentid : fulllist){
            if(displaylist.size() >= k) break;
            displaylist.add(this.getSentence(sentid));
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
            queryids.add(sentid);
        }

        this.putQueryResult(term, queryids);
    }
}
