package edu.illinois.cs.cogcomp.lorelei.kb;

import edu.illinois.cs.cogcomp.lorelei.edl.KBEntity;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by mayhew2 on 7/28/17.
 */
@SuppressWarnings("ALL")
public class MapDBMatcher {

    private final String indexdir;
    DB cachedb;
    HTreeMap<Integer, String> namemap;
    HTreeMap<Integer, String> altnamemap;
    ConcurrentMap<String, Integer> cache;

    private static Logger logger = LoggerFactory.getLogger(MapDBMatcher.class);

    DB[] dbarray = new DB[5];
    public HashMap<Integer, ConcurrentMap<String, int[]>> maplist = new HashMap<>();;
    List<Integer> ngramorders;

    public MapDBMatcher(String ilcode) throws IOException {
        this(ilcode, Arrays.asList(0,1,2,3,4));
    }
    public MapDBMatcher(String ilcode, List<Integer> ngrams) throws IOException {
        this.indexdir = "/shared/preprocessed/upadhya3/il"+ilcode+"_index/";
        System.err.println("reading INDEX at "+this.indexdir);
        ngramorders = ngrams;
        cachedb = DBMaker.fileDB("cachemap.db")
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .make();

         cache = cachedb
                .hashMap("map")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.INTEGER)
                 .createOrOpen();

    }

    public List<String> getngrams(String s, int ngram){
        // THIS IS THE SECTION WHERE WE SET NGRAMS
        List<String> ngrams = new ArrayList<>();
        switch (ngram){
            case 0:
                ngrams.add(s);
                break;
            case 1:
                ngrams.addAll(Arrays.asList(s.split(" ")));
                break;
            case 4:
                ngrams.addAll(getngramsHelper(s, 4));
                break;
            case 3:
                ngrams.addAll(getngramsHelper(s, 3));
                break;
            case 2:
                ngrams.addAll(getngramsHelper(s, 2));
                break;
            case 5:
                String v = s.toLowerCase().replaceAll("[aeiou]", "");
                ngrams.add(v);
                ngrams.addAll(getngramsHelper(v,4));
                break;

        }
        return ngrams;
    }

    public LinkedHashMap<Integer, Double> retrieve(String query, List<Integer> ngramorder, String type, HashMap<Integer, ConcurrentMap<String, int[]>> maplist, int numcands) {
        return retrieve(query, ngramorder, type, maplist, numcands, altnamemap);
    }

    /**
     * This is the main function in the program.
     * @param query
     * @param type
     * @param maplist
     * @param numcands
     * @param altnamemap
     * @return
     */
    public LinkedHashMap<Integer, Double> retrieve(String query, List<Integer> ngramorder, String type, HashMap<Integer, ConcurrentMap<String, int[]>> maplist, int numcands, HTreeMap<Integer, String> altnamemap){

        HashMap<Integer, Double> ret = new HashMap<>();

        query = query.toLowerCase();

        HashMap<Integer, List<String>> int2ngrams = new HashMap<>();
        for(int i =0; i < 5; i++){
            List<String> mentionngrams = getngrams(query, i);
            int2ngrams.put(i, mentionngrams);
        }

        logger.info("Query : " + query);

        if(query.equals("finfinne")){
            System.out.println("pause here....");
        }

        for(Integer i : ngramorder){
            List<String> mentionngrams = int2ngrams.get(i);

            for(String ngram : mentionngrams) {
                int[] results = maplist.get(i).get(ngram);
                if (results == null) continue;

                // crucially, stop looking for more cands if we have reached the limit.
                if (ret.keySet().size() > numcands) {
                    break;
                }

                for (int entityid : results) {
                    //System.out.println(entityid);

//                    if(!namemap.get(entityid).contains("wikipedia.org")){
//                        continue;
//                    }


                    KBEntity bke = new KBEntity(namemap.get(entityid).split("\t"));
                    String candsurf = bke.getNameUTF8();
                    String candtype = bke.getType();

                    if(type!=null && !candtype.equals(type)) continue;

                    //String candsurf = getname(entityid + "");
                    if(candsurf == null) continue;
                    //List<String> candngrams = getngrams(candsurf, i);

                    double score = Jaccard.similarity(query, candsurf.toLowerCase());

                    // find the best matching alternate name.
                    if(altnamemap != null && altnamemap.get(entityid) != null) {
                        String[] altnames = altnamemap.get(entityid).split("\\|");

                        for (String altname : altnames) {
                            double currscore = Jaccard.similarity(query, altname.toLowerCase());
                            if (currscore > score) {
                                score = currscore;
                                candsurf = altname;
                            }
                        }
                    }

                    String featclass = bke.getFeatureClass();
                    String featcode = bke.getFeatureCode();
                    if(featclass != null && featclass.equals("A")){
                        score *= 1.5;
                    }
                    if(featcode != null && featcode.contains("PCL")){
                        score *= 2;
                    }
                    if(featcode != null && featcode.contains("PPLC")){
                        score *= 2;
                    }

                    logger.debug(candsurf + ":" + score);

                    double j = ret.getOrDefault(entityid, 0.);
                    ret.put(entityid, j + score);
                }
            }
        }

        LinkedHashMap<Integer, Double> result = ret.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(numcands)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        return result;
    }

    DB namedb, altnamedb;
    public void load_alldbs() {
        logger.info("opening namedb..");
//        namedb = DBMaker.fileDB(new File(this.indexdir+"/namemap.db"))
//                .fileMmapEnable()
//                .closeOnJvmShutdown()
//                .readOnly()
//                .make();
//
//        namemap = namedb.hashMap("map")
//                .keySerializer(Serializer.INTEGER)
//                .valueSerializer(Serializer.STRING)
//                .open();

        try {
            namedb = DBMaker.fileDB(new File(this.indexdir + "/namemap.db"))
                    .fileMmapEnable()
                    .closeOnJvmShutdown()
                    .readOnly()
                    .make();
        } catch(Exception e){
            namedb = DBMaker.tempFileDB()
                    .fileMmapEnable()
                    .closeOnJvmShutdown()
                    .make();
        }


        namemap = namedb.hashMap("map")
                .keySerializer(Serializer.INTEGER)
                .valueSerializer(Serializer.STRING)
                .createOrOpen();

//        List<Integer> ngramorders = Arrays.asList(0,1,4);

        for(Integer i : ngramorders){
            logger.info("opening ngrams-{}.db", i);
            File ngramfile = new File(this.indexdir + "ngrams-uncased-" + i + ".db");
            DB db;
            try {
                db = DBMaker.fileDB(ngramfile)
                        .fileMmapEnable()
                        .closeOnJvmShutdown()
                        .readOnly()
                        .make();
            } catch (Exception e){
                db = DBMaker.tempFileDB()
                        .fileMmapEnable()
                        .closeOnJvmShutdown()
                        .make();
            }

            dbarray[i] = db;

            ConcurrentMap<String, int[]> map = db
                    .hashMap("map")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.INT_ARRAY)
                    .createOrOpen();

            maplist.put(i, map);
        }

        logger.info("Loading altnamemap.db");
        try {
            altnamedb = DBMaker.fileDB(new File(this.indexdir + "altnamemap.db"))
                    .fileMmapEnable()
                    .closeOnJvmShutdown()
                    .readOnly()
                    .make();
        } catch (Exception e){
            altnamedb = DBMaker.tempFileDB()
                    .fileMmapEnable()
                    .closeOnJvmShutdown()
                    .make();
        }
        altnamemap = altnamedb.hashMap("map")
                .keySerializer(Serializer.INTEGER)
                .valueSerializer(Serializer.STRING)
                .createOrOpen();
    }

    public static ArrayList<String> getngramsHelper(String s, int n){
        ArrayList<String> ret = new ArrayList<>();
        for(int i = 0; i < s.length()- n+1; i++){
            ret.add(s.substring(i, i+n));
        }
        return ret;
    }

     static class Jaccard {

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
    }



}
