package io.github.mayhewsw.controllers;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import io.github.mayhewsw.ConfigFile;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class Common {

    public static final String FOLDERTA = "ta";
    public static final String FOLDERTAJSON = "tajson";
    public static final String FOLDERCOLUMN = "column";
    public static final String FOLDERCONLL = "conll";


    public static HashMap<String, ConfigFile> loadConfig() {
        // hardcoded to look in config path
        File configfolder = new File("config");

        File[] configfiles = configfolder.listFiles();

        HashMap<String, ConfigFile> datasets = new HashMap<>();

        for(File f : configfiles){
            if(f.getName().endsWith("~")) continue;

            if(f.getName().startsWith("doc-") || f.getName().startsWith("sent-")) {

                System.out.println(f);
                ConfigFile c = new ConfigFile();

                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF8"));

                    // load a properties file
                    c.loadProperties(in);

                    datasets.put(f.getName(), c);

                } catch (IOException e) {

                }
            }
        }
        return datasets;
    }

}
