package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.lbjava.learn.Lexicon;
import edu.illinois.cs.cogcomp.lbjava.learn.SparseNetworkLearner;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by mayhew2 on 6/20/17.
 */
public class FeatureWeights {

    public static void getFeatureWeights(SparseNetworkLearner c) throws Exception {
        ByteArrayOutputStream sout = new ByteArrayOutputStream();
        PrintStream out;
        out = new PrintStream(sout);
        c.write(out);
        String s = sout.toString();

        List<String> labels = new ArrayList<>();
        List<HashMap<String, Double>> featlist = new ArrayList<>();

        String[] lines = s.split("\n");
        Lexicon lexicon = c.getLexicon();


        // this is the label line.
        int i = 4;

        while(true) {
            if(lines[i].startsWith("End")){
                // then it's really the end;
                break;
            }

            String label = lines[i].split(" ")[1];
            labels.add(label);
            System.out.println("Reading " + label);
            i += 3;

            HashMap<String, Double> feats = new HashMap<String, Double>();
            String line;
            int startval = i;
            while (!(line = lines[i]).startsWith("End")) {
                //System.out.println(line);
                String featid = lexicon.lookupKey(i - startval).toStringNoPackage(); // .getStringIdentifier();
                feats.put(featid, Double.parseDouble(line));
                i++;
            }
            featlist.add(feats);

            // will get you to next label, or last line.
            i++;
        }

        System.out.println("DONE");
        List<String> outlines = new ArrayList<>();
        double thresh = 1;
        for(int j = 0; j < labels.size(); j++){
            String label = labels.get(j);
            System.out.println(label);
            outlines.add(label);
            HashMap<String, Double> feats = featlist.get(j);
            for(String key : feats.keySet()){
                if(Math.abs(feats.get(key)) > thresh){
                    //System.out.println(key + " : " + feats.get(key));
//                    if(key.startsWith("Forms")){
                    System.out.println(key + " : " + feats.get(key));
                    outlines.add(label + "\t" + key + " : " + feats.get(key));
//                    }
                }
            }
            outlines.add("\n");
            System.out.println();
        }

        LineIO.write("feats.txt", outlines);


    }



    public static void main(String[] args) throws Exception {
        CandClassifier c = new CandClassifier();

        getFeatureWeights(c);

        int f = 34;
        double ff = 34.1;

        System.out.println(f / ff);


    }

}
