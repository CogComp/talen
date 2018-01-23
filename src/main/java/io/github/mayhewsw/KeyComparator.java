package io.github.mayhewsw;

import java.util.Comparator;

/**
 * Created by mayhew2 on 5/4/17.
 */
public class KeyComparator implements Comparator<String> {

    @Override
    public int compare(String o1, String o2) {
        int retval;
        try{
            retval = Integer.compare(Integer.parseInt(o1), Integer.parseInt(o2));
        }catch(NumberFormatException e){
            retval = o1.compareTo(o2);
        }
        return retval;
    }
}
