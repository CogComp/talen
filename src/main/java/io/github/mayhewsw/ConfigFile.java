package io.github.mayhewsw;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public class ConfigFile {
    private String folderpath;
    private String name;
    private String[] labels;
    private String dict;
    private String format;
    private String mode;

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
        return "folderpath\t" + folderpath + '\n' +
                "name\t" + name + '\n' +
                "labels\t" + StringUtils.join(labels, " ") + '\n' +
                "dict\t" + dict + '\n' +
                "format\t" + format + '\n' +
                "mode\t" + mode;
    }
}
