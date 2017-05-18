package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import io.github.mayhewsw.BootstrapController;
import io.github.mayhewsw.TempConllReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;

/**
 * Created by mayhew2 on 5/18/17.
 */
public class SentenceCache extends HashMap<String, Constituent> {

    private final String folderpath;

    public SentenceCache(String folderpath){
        this.folderpath = folderpath;
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

}
