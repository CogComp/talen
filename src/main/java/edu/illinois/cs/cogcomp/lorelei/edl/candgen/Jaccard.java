package edu.illinois.cs.cogcomp.lorelei.edl.candgen;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by mayhew2 on 8/16/17.
 */
public class Jaccard {

    private static final int DEFAULT_K = 3;

    private int k;

    /**
     * The strings are first transformed into sets of k-shingles (sequences of k
     * characters), then Jaccard index is computed as |A inter B| / |A union B|.
     * The default value of k is 3.
     *
     * @param k
     */
    public Jaccard(final int k) {
        this.k = k;
    }

    /**
     * The strings are first transformed into sets of k-shingles (sequences of k
     * characters), then Jaccard index is computed as |A inter B| / |A union B|.
     * The default value of k is 3.
     */
    public Jaccard() {
        this.k = DEFAULT_K;
    }

    /**
     * Compute Jaccard index: |A inter B| / |A union B|.
     * @param s1 The first string to compare.
     * @param s2 The second string to compare.
     * @return The Jaccard index in the range [0, 1]
     * @throws NullPointerException if s1 or s2 is null.
     */
    public static double similarity(String s1, String s2) {
        if (s1 == null) {
            throw new NullPointerException("s1 must not be null");
        }

        if (s2 == null) {
            throw new NullPointerException("s2 must not be null");
        }

        if (s1.equals(s2)) {
            return 1;
        }

        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int k = 2;
        Map<String, Integer> profile1 = getProfile(s1, k);
        Map<String, Integer> profile2 = getProfile(s2, k);

        Set<String> union = new HashSet<>();
        union.addAll(profile1.keySet());
        union.addAll(profile2.keySet());

        int inter = profile1.keySet().size() + profile2.keySet().size()
                - union.size();

        return 1.0 * inter / union.size();
    }


    /**
     * Distance is computed as 1 - similarity.
     * @param s1 The first string to compare.
     * @param s2 The second string to compare.
     * @return 1 - the Jaccard similarity.
     * @throws NullPointerException if s1 or s2 is null.
     */
    public final double distance(final String s1, final String s2) {
        return 1.0 - similarity(s1, s2);
    }


    /**
     * Pattern for finding multiple following spaces.
     */
    private static final Pattern SPACE_REG = Pattern.compile("\\s+");

    public static Map<String, Integer> getProfile(final String string, int k) {

        HashMap<String, Integer> shingles = new HashMap<>();

        String string_no_space = SPACE_REG.matcher(string).replaceAll(" ");
        for (int i = 0; i < (string_no_space.length() - k + 1); i++) {
            String shingle = string_no_space.substring(i, i + k);
            Integer old = shingles.get(shingle);
            if (old != null) {
                shingles.put(shingle, old + 1);
            } else {
                shingles.put(shingle, 1);
            }
        }

        return Collections.unmodifiableMap(shingles);
    }

    public static void main(String[] args){
        String a = "niwyork";

        List<String> nn = Arrays.asList("Republic of Niger","Novosibirsk","Napier","Nitra","Kenilworth","New York");

        for(String n : nn) {
            System.out.println(n + " : " + Jaccard.similarity(a, n));
        }
    }

}
