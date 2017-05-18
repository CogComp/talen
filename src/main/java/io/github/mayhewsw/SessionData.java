package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import io.github.mayhewsw.utils.SentenceCache;
import org.apache.lucene.store.RAMDirectory;

import javax.servlet.http.HttpSession;
import java.util.*;

/**
 *
 * This is a way to collect all the data that is typically found in a session.
 *
 * Created by mayhew2 on 2/23/17.
 */
public class SessionData {

    Dictionary dict;
    String username;
    Boolean showdefs;
    TreeMap<String, TextAnnotation> tas;
    String dataname;
    HashMap<Pair<String, String>, Double> patterns;
    RAMDirectory ramDirectory;

    ArrayList<String> suffixes;
    Properties prop;

    SentenceCache cache;
    HashMap<String, Constituent> annosents;

    HashSet<String> terms;
//    String folderpath;
    String indexpath;

    HashMap<String, HashSet<Constituent>> groups;

    public SessionData(HttpSession hs){

        tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
        dict = (Dictionary) hs.getAttribute("dict");
        patterns = (HashMap<Pair<String, String>, Double>) hs.getAttribute("patterns");

        username = (String) hs.getAttribute("username");
        dataname = (String) hs.getAttribute("dataname");
//        folderpath = (String) hs.getAttribute("folderpath");
        indexpath = (String) hs.getAttribute("indexpath");

        showdefs = (Boolean) hs.getAttribute("showdefs");
        if(showdefs == null){
            showdefs = false;
        }

        suffixes = (ArrayList<String>) hs.getAttribute("suffixes");

        prop = (Properties) hs.getAttribute("prop");

        ramDirectory = (RAMDirectory) hs.getAttribute("ramdirectory");
        terms = (HashSet<String>) hs.getAttribute("terms");

        cache = (SentenceCache) hs.getAttribute("cache");
        annosents = (HashMap<String, Constituent>) hs.getAttribute("annosents");

        groups = (HashMap<String, HashSet<Constituent>>) hs.getAttribute("groups");

    }


}
