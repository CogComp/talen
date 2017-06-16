package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import org.apache.commons.lang3.StringUtils;

import javax.xml.soap.Text;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by mayhew2 on 5/18/17.
 */
public class Sandbox {

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public static void main(String[] args) {

        String folder = "/tmp/sents/";

        CoNLLNerReader cnr = new CoNLLNerReader(folder);

        HashMap<String, Integer> surfacecounts = new HashMap<>();

        TextAnnotation ta;
        while(cnr.hasNext()){
            ta = cnr.next();

            View ner = ta.getView(ViewNames.NER_CONLL);
            for(Constituent cons : ner.getConstituents()){
                String surface = cons.getTokenizedSurfaceForm();
                String label = cons.getLabel();

                surfacecounts.merge(surface + ":::" + label, 1, (oldv,one) -> oldv + one);
            }
        }

        for(Map.Entry<String, Integer> e : sortByValue(surfacecounts).entrySet()){
            System.out.println(e.getKey() + "\t" + e.getValue());
        }

    }
}
