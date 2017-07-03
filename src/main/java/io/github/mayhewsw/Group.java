package io.github.mayhewsw;

import java.util.HashMap;
import java.util.HashSet;

/**
 * This is meant to add functionality to the hashset. 
 * 
 * Created by stephen on 6/29/17.
 */
public class Group extends HashSet<String> {
    
    // is all annotated?
    // label dictionary
    // most common label
    private HashSet<String> anno = new HashSet<>();

    private HashMap<String, Integer> typemap = new HashMap<>();

    /**
     * Add an annotated sentence.
     * @param sent
     */
    public void addAnno(String sent, String type){
        this.add(sent);
        this.anno.add(sent);
        this.addType(type);
    }

    public HashSet<String> getAnno() {
        return anno;
    }

    public void addType(String tag){
        int num = typemap.getOrDefault(tag, 0);
        typemap.put(tag, num+1);
    }

    public String maxType(){
        return typemap.keySet().stream().max((a,b) -> typemap.get(a) - typemap.get(b)).orElse("null");
    }

}
