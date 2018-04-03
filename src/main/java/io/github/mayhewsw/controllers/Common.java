package io.github.mayhewsw.controllers;

import java.io.*;
import java.util.HashMap;
import java.util.Properties;

public class Common {

    public static final String FOLDERTA = "ta";
    public static final String FOLDERCONLL = "conll";


    public static HashMap<String, Properties> loadConfig(){
        File configfolder = new File("config");

        File[] configfiles = configfolder.listFiles();

        HashMap<String, Properties> datasets = new HashMap<>();

        for(File f : configfiles){
            if(f.getName().endsWith("~")) continue;

            if(f.getName().startsWith("doc-") || f.getName().startsWith("sent-")) {

                System.out.println(f);
                Properties prop = new Properties();
                // there's probably a better way to set defaults...
                prop.setProperty("type", FOLDERCONLL);

                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF8"));

                    // load a properties file
                    prop.load(in);

                    datasets.put(f.getName(), prop);

                } catch (IOException e) {

                }
            }
        }
        return datasets;
    }

}
