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

    public  HashMap<String, ConfigFile> datasets;
    public Dictionary dict;
    public String username;
    public Boolean showdefs;
    public Boolean showroman;
    public Boolean allowcopy;


    public TreeMap<String, TextAnnotation> tas;
    public String dataname;
    public HashMap<Pair<String, String>, Double> patterns;
    public RAMDirectory ramDirectory;

    public ArrayList<String> suffixes;
    public Properties prop;

    public SentenceCache cache;
    //HashMap<String, Group> annosents;

    public HashMap<String, Set<String>> contexts;
    public List<String> labels;
    public String indexpath;

    public HashMap<String, Group> groups;
    public String logfile;

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

        showroman = (Boolean) hs.getAttribute("showroman");
        if(showroman == null){
            showroman = false;
        }

        allowcopy = (Boolean) hs.getAttribute("allowcopy");
        if(allowcopy == null){
            allowcopy = false;
        }

        suffixes = (ArrayList<String>) hs.getAttribute("suffixes");

        prop = (Properties) hs.getAttribute("prop");
        labels = (List<String>) hs.getAttribute("labels");
        ramDirectory = (RAMDirectory) hs.getAttribute("ramdirectory");

        contexts = (HashMap<String, Set<String>>) hs.getAttribute("contexts");

        cache = (SentenceCache) hs.getAttribute("cache");
        //annosents = (HashMap<String, HashSet<String>>) hs.getAttribute("annosents");

        groups = (HashMap<String, Group>) hs.getAttribute("groups");

        logfile = (String) hs.getAttribute("logfile");

        datasets = (HashMap<String, ConfigFile>) hs.getAttribute("datasets");

    }


}
