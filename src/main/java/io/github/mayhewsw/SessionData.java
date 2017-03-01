package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.TextAnnotationReader;

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
    HashMap<String, Integer> rules;
    HashMap<String, Integer> patterns;

    ArrayList<String> suffixes;
    Properties prop;


    public SessionData(HttpSession hs){

        tas = (TreeMap<String, TextAnnotation>) hs.getAttribute("tas");
        dict = (Dictionary)hs.getAttribute("dict");
        rules = (HashMap<String, Integer>) hs.getAttribute("rules");
        patterns = (HashMap<String, Integer>) hs.getAttribute("patterns");

        username = (String) hs.getAttribute("username");
        dataname = (String) hs.getAttribute("dataname");

        showdefs = (Boolean) hs.getAttribute("showdefs");
        if(showdefs == null){
            showdefs = false;
        }

        suffixes = (ArrayList<String>) hs.getAttribute("suffixes");

        prop = (Properties) hs.getAttribute("prop");

    }


}
