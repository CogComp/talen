package io.github.mayhewsw;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mayhew2 on 5/18/17.
 */
public class Sandbox {

    public static void main(String[] args) {

        String surf = "last year when barak obamaning was obama and obamagha had barak obama over for obama's lunch.";
        String[] tok = surf.split(" ");

        String text = "barak obama";


        Pattern pattern = Pattern.compile("\\b"+text+"[^ ]*\\b", Pattern.CASE_INSENSITIVE);
        // in case you would like to ignore case sensitivity,
        // you could use this statement:
        // Pattern pattern = Pattern.compile("\\s+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(surf);
        // check all occurance
        while (matcher.find()) {
            // these are character offsets.
            int startind = StringUtils.countMatches(surf.substring(0, matcher.start()), " ");
            int endind = startind + text.split(" ").length;

            System.out.println(startind + ":" + endind);

            for(int i = startind; i < endind; i++){
                System.out.println(tok[i]);
            }
        }


    }
}
