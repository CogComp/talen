package io.github.mayhewsw;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * This class represents the exact names of each element in a config file.
 */
public class ConfigFile extends Properties{
    private String folderpath;
    private String name;
    private String[] labels;
    private String dict;
    private String format;
    private String mode;
    private String indexpath;
    private String[] terms;

    public List<String> allowableentries;

    public ConfigFile(){
        // do something with properties?
    }

    public String getIndexpath() {
        return indexpath;
    }

    public String[] getTerms() {
        return terms;
    }

    public void setIndexpath(String indexpath) {
        this.indexpath = indexpath;
    }

    public void setTerms(String[] terms) {
        this.terms = terms;
    }

    public String getFname() {
        String fname;
        if (mode.equals("document")) {
            fname = "doc-" + name + ".txt";
        } else {
            fname = "sent-" + name + ".txt";
        }
        return fname;
    }

    public String getFolderpath() {
        return folderpath;
    }

    public String getName() {
        return name;
    }

    public String[] getLabels() {
        return labels;
    }

    public String getDict() {
        return dict;
    }

    public String getFormat() {
        return format;
    }

    public String getMode() {
        return mode;
    }

    public void setFolderpath(String folderpath) {
        this.folderpath = folderpath;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
    }

    public void setDict(String dict) {
        this.dict = dict;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    @Override
    public String toString() {

        HashMap<String, String> entries = new HashMap<>();

        entries.put("folderpath", folderpath);
        entries.put("name", name);
        entries.put("labels", StringUtils.join(labels, " "));
        entries.put("dict", dict);
        entries.put("format", format);
        entries.put("mode", mode);
        entries.put("indexpath", indexpath);
        entries.put("terms",  StringUtils.join(terms, ","));

        StringJoiner sj = new StringJoiner("\n");
        for(String k : entries.keySet()){
            if(entries.get(k) != null && entries.get(k).trim().length() > 0){
                sj.add(k + "\t" + entries.get(k));
            }
        }

        return sj.toString();
    }
}
