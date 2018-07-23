package edu.illinois.cs.cogcomp.lorelei.kb;

import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.SpanLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.View;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.lorelei.edl.KBEntity;
import edu.illinois.cs.cogcomp.lorelei.edl.candgen.Jaccard;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.lang3.StringUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

//import edu.illinois.cs.cogcomp.xlwikifier.research.transliteration.TitleTranslator;

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

    public void closeCacheDb(){
        cachedb.close();
    }

    /**
     * Returns all broken down terms for the mention (ngrams, as well as tokens, as well the full string)
     * @param mention
     * @return
     */
    private List<String> getallterms(String mention){
        HashSet<String> ngrams = new HashSet<>();

        int startngram = 2;
        int endngram = 5;

        // add exact match
        ngrams.add(mention);
        // add all terms
        ngrams.addAll(Arrays.asList(mention.split(" ")));

        for(int i = startngram; i < endngram+1; i++){
            ngrams.addAll(KBStringMatcher.getngrams(mention, i));
        }

        List<String> ret = ngrams.stream()
                .sorted(Comparator.comparing(String::length).reversed())
                .collect(Collectors.toList());

        return ret;
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
                ngrams.addAll(KBStringMatcher.getngrams(s, 4));
                break;
            case 3:
                ngrams.addAll(KBStringMatcher.getngrams(s, 3));
                break;
            case 2:
                ngrams.addAll(KBStringMatcher.getngrams(s, 2));
                break;
            case 5:
                String v = s.toLowerCase().replaceAll("[aeiou]", "");
                ngrams.add(v);
                ngrams.addAll(KBStringMatcher.getngrams(v,4));
                break;

        }
        return ngrams;
    }

    public void buildindex(String kbdir, String indexpath, int ngram) throws IOException {
        // takes around 45 minutes for indexing everything.
        File ngramfile = new File(indexpath + "ngrams-uncased-" + ngram + ".db");

        if(ngramfile.exists()){
            logger.error("DB exists! "+ngramfile);
            System.exit(-1);
        }

        DB db = DBMaker.fileDB(ngramfile)
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .make();


        ConcurrentMap<String, int[]> map = db
                .hashMap("map")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.INT_ARRAY)
                .create();


        // hard coded to match the schema from LORELEI.
        String kbpath = kbdir + "entities.tab";
        String altnamespath = kbdir + "alternate_names.tab";

        long startTime = System.nanoTime();
        long currTime = startTime;
        long prevTime;

        int j = 0;

        HashMap<String, TIntHashSet> localmap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(kbpath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // process the line.
                if(j == 0){
                    j++;
                    continue;
                }

                if (j % 1000 == 0) {
                    prevTime = currTime;
                    currTime = System.nanoTime();
                    long last = (currTime - prevTime) / 1000000;
                    long elapsed = (currTime - startTime)/ 1000000;
                    double average = elapsed / (j);
                    System.out.println("Progress: " + j + "\t" + last + "\t" + elapsed + "\t" + average);
                }
                j++;

                String[] sline = line.split("\t");

                int entityid = Integer.parseInt(sline[2]);
                //String name = sline[1];
                String asciiname = sline[3].toLowerCase();
                //String[] altnames = sline[3].split(",");

                List<String> ngrams= getngrams(asciiname, ngram);

//                for(String altname : altnames){
//                    ngrams.addAll(getallterms(altname));
//                }

                // remove duplicates
                HashSet<String> ngramset = new HashSet<>(ngrams);

                // ngrams is sorted
                for(String ngramstring : ngramset){
                    TIntHashSet ids = localmap.getOrDefault(ngramstring, new TIntHashSet());

                    ids.add(entityid);
                    localmap.put(ngramstring, ids);
                }

                //if(j > 100000){break;}
            }
        }

        // add the alternate names too.
        try (BufferedReader br = new BufferedReader(new FileReader(altnamespath))) {
            String line;
            int k = 0;
            while ((line = br.readLine()) != null) {
                // skip
                if(k == 0){
                    k++;
                    continue;
                }
                String[] sline = line.split("\t");
                if(sline.length != 2) continue;

                int entityid = Integer.parseInt(sline[0]);
                String name= sline[1].toLowerCase();

                List<String> ngrams= getngrams(name, ngram);

                // remove duplicates
                HashSet<String> ngramset = new HashSet<>(ngrams);

                // ngrams is sorted
                for(String ngramstring : ngramset){
                    TIntHashSet ids = localmap.getOrDefault(ngramstring, new TIntHashSet());

                    ids.add(entityid);
                    localmap.put(ngramstring, ids);
                }

            }
        }


        float total = (float) localmap.size();
        j = 0;
        for(String ng : localmap.keySet()){
            if(j%1000 == 0){
                System.out.println("Progress: " + j / total);
            }

            TIntHashSet ints = localmap.get(ng);
            int[] intarray = ints.toArray(new int[ints.size()]);

            map.put(ng, intarray);
            j++;
        }


        db.close();
    }


    public void makealtnamemap(String kbpath, String indexpath) throws FileNotFoundException {
        DB namedb = DBMaker.fileDB(indexpath)
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .make();

        namemap = namedb.hashMap("map")
                .keySerializer(Serializer.INTEGER)
                .valueSerializer(Serializer.STRING)
                .create();

        HashMap<Integer, HashSet<String>> localnamemap = new HashMap<>();

        float total;
        int j = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(kbpath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // process the line.
                if (j == 0) {
                    j++;
                    continue;
                }
                String[] sline = line.split("\t");
                //System.out.println(line.trim());
                if(sline.length != 2){
                    logger.error("Skipping (bad length): {}", line);
                    continue;
                }
                int entityid = Integer.parseInt(sline[0]);
                String altname = sline[1];

                HashSet<String> curr = localnamemap.getOrDefault(entityid, new HashSet<>());
                curr.add(altname);
                localnamemap.put(entityid, curr);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        total = (float) localnamemap.size();
        j = 0;
        for(Integer id : localnamemap.keySet()){
            if(j%10000 == 0){
                System.out.println("Progress: " + j / total);
            }

            String asciiname = StringUtils.join(localnamemap.get(id), "|");
            namemap.put(id, asciiname);
            j++;
        }

        namedb.close();
    }

    /**
     * This function builds the namemap. Can be run completely separately from building the ngrams.
     * The namemap is actually a map from entity id to KB line. This way all information from the line can be retrieved quickly later.
     */
    public void makenamemap(String kbpath, String indexpath) throws IOException {

        DB namedb = DBMaker.fileDB(indexpath)
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .make();

        namemap = namedb.hashMap("map")
                .keySerializer(Serializer.INTEGER)
                .valueSerializer(Serializer.STRING)
                .create();

        HashMap<Integer, String> localnamemap = new HashMap<>();

        float total;
        int j = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(kbpath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // process the line.
                if (j == 0) {
                    j++;
                    continue;
                }
                String[] sline = line.split("\t");
                //System.out.println(line.trim());
                int entityid = Integer.parseInt(sline[2]);
                localnamemap.put(entityid, line.trim());
            }
        }

        total = (float) localnamemap.size();
        j = 0;
        for(Integer id : localnamemap.keySet()){
            if(j%1000 == 0){
                System.out.println("Progress: " + j / total);
            }

            String asciiname = localnamemap.get(id);
            namemap.put(id, asciiname);
            j++;
        }

        namedb.close();
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
        //List<String> ngrams = getallterms(query);

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

    public void testindex() throws IOException {

        BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in));

        String s = "";
        while (!s.equalsIgnoreCase("q")) {
            try {
                System.out.println("Enter the search query (q=quit): ");
                s = br.readLine();
                if (s.equalsIgnoreCase("q")) {
                    break;
                }

                // do retrieval here...
                // 0 means exact match
                LinkedHashMap<Integer,Double> ret = retrieve(s, ngramorders, "GPE", maplist, 20);

                for(Integer i : ret.keySet()){
                    System.out.println(i + "\t" + getname(i + "") + "\t" + ret.get(i));
                }


            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error searching " + s + " : " + e.getMessage());
            }
        }
    }


    public void testindex_on_file(List<Integer> ngramorders, String infile, String transfile) throws IOException {

        Map<String,List<String>> translits = load_translits(transfile);

        BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in));

        List<String> lines = LineIO.read(infile);
        int total = 0;
        int total_hits = 0;
        int nils = 0;
        for(String line:lines)
        {
            String[] parts = line.split("\t");
            String surface = parts[2].toLowerCase();
            Integer gold_eid;
            total+=1;

            if(parts[4].startsWith("NIL"))
            {
                nils++;
                continue;
            }
            if(!parts[4].contains("|"))
            {
                gold_eid = Integer.parseInt(parts[4]);
            }
            else
            {
                gold_eid = Integer.parseInt(parts[4].split("|")[0]);
            }
            System.out.println("surf:"+surface+" eid:"+gold_eid);
            LinkedHashMap<Integer,Double> ret = retrieve(surface, ngramorders, null, maplist, 20);
            boolean found = false;
            for(Integer i : ret.keySet()){
                System.out.println(i + "\t" + gold_eid + "\t" + getname(i + "") + "\t" + ret.get(i));
                if (i.equals(gold_eid))
                {
                    System.out.println("yay!");
                    found = true;
                    total_hits+=1;
                }
            }
            if (!found && translits.containsKey(surface))
            {
                System.out.println("trying with transliteration for "+surface);
                List<String> trans = translits.get(surface);
                System.out.println("trans:"+trans);
                for(String tran:trans)
                {
                    ret = retrieve(tran, ngramorders, null, maplist, 20);
                    for(Integer i : ret.keySet()){
                        System.out.println(i + "\t" + gold_eid + "\t" + getname(i + "") + "\t" + ret.get(i));
                        if (i.equals(gold_eid))
                        {
                            System.out.println("yay!");
                            found = true;
                            total_hits+=1;
                        }
                    }
                }
            }
            if (!found)
            {
                System.out.println("missed:"+surface+" eid:"+gold_eid+" name:"+getname(gold_eid+""));
            }
        }
    }

    int total = 0;
    int total_hits = 0;
    int nils = 0;

    public void testindex_on_ta(List<Integer> ngramorders, TextAnnotation ta, String transfile) throws IOException {

        Map<String,List<String>> translits = load_translits(transfile);

        BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in));

        View nerview =ta.getView(ViewNames.NER_CONLL);
        View romview =ta.getView(ViewNames.TRANSLITERATION);
        SpanLabelView candgen = new SpanLabelView("CANDGEN",ta);
        List<Constituent> gold_conslist = null;
        if(ta.hasView("WIKIFIER"))
        {
            View gold_view = ta.getView("WIKIFIER");
            gold_conslist = gold_view.getConstituents();
        }

        List<Constituent> nercons = nerview.getConstituents();
        for(int idx =0;idx< nercons.size();idx++)
        {
            Constituent ne = nercons.get(idx);
            List<Constituent> romcons = romview.getConstituentsCovering(ne);
            String surface;
            if(romcons.size()==1)
            {
//                surface = romcons.get(0).getSurfaceForm();
                surface = romcons.get(0).getLabel().toLowerCase();
                surface = surface.replace("[?]","");
                System.err.println("using romanized:"+surface+" original:"+ne.getSurfaceForm());
            }
            else
            {
                surface = ne.getSurfaceForm().toLowerCase();
            }
            total+=1;

            LinkedHashMap<Integer,Double> ret = retrieve(surface, ngramorders, null, maplist, 20);
            Map<String, Double> label2scores = new HashMap<String, Double>();
            for(Integer i : ret.keySet()){
                label2scores.put(getname(i + ""),ret.get(i));
            }
            Constituent cons;
            if(label2scores.size()>0) {
                cons = new Constituent(label2scores, "CANDGEN", ta, ne.getStartSpan(), ne.getEndSpan());
            }
            else
            {
                cons = new Constituent("NIL","CANDGEN",ta,ne.getStartSpan(),ne.getEndSpan());
            }
            candgen.addConstituent(cons);
            // gold is available
            if (gold_conslist!=null)
            {
                String gold_eid_str = gold_conslist.get(idx).getLabel();
                Integer gold_eid;

                if(gold_eid_str.startsWith("NIL"))
                {
                    nils++;
                    continue;
                }
                if(!gold_eid_str.contains("|"))
                {
                    gold_eid = Integer.parseInt(gold_eid_str);
                }
                else
                {
                    gold_eid = Integer.parseInt(gold_eid_str.split("|")[0]);
                }
                System.out.println("surf:"+surface+" eid:"+gold_eid);
                boolean found = false;
                for(Integer i : ret.keySet()){
                    System.out.println(i + "\t" + gold_eid + "\t" + getname(i + "") + "\t" + ret.get(i));
                    if (i.equals(gold_eid))
                    {
                        System.out.println("yay!");
                        found = true;
                        total_hits+=1;
                    }
                }
                if (!found && translits.containsKey(surface))
                {
                    System.out.println("trying with transliteration for "+surface);
                    List<String> trans = translits.get(surface);
                    System.out.println("trans:"+trans);
                    for(String tran:trans)
                    {
                        ret = retrieve(tran, ngramorders, null, maplist, 20);
                        for(Integer i : ret.keySet()){
                            System.out.println(i + "\t" + gold_eid + "\t" + getname(i + "") + "\t" + ret.get(i));
                            if (i.equals(gold_eid))
                            {
                                System.out.println("yay!");
                                found = true;
                                total_hits+=1;
                            }
                        }
                    }
                }
                if (!found)
                {
                    System.out.println("missed:"+surface+" eid:"+gold_eid+" name:"+getname(gold_eid+""));
                }
            }
        }
        ta.addView("CANDGEN",candgen);

        //System.out.println(map.keySet());

    }

    private Map<String,List<String>> load_translits(String transfile) throws FileNotFoundException {
        Map<String,List<String>> translits = new HashMap<>();
        if(transfile!=null)
        {
            logger.info("loading transliterations ...");
            List<String> lines = LineIO.read(transfile);
            for (String line:lines)
            {
                String[] parts = line.split("\t");
                String key = parts[0];
                List<String> lst = new ArrayList<>();
                for(int idx=1;idx<parts.length;idx++)
                    lst.add(parts[idx]);
                translits.put(key,lst);
            }
            logger.info("transliterations loaded.");
        }
        return translits;
    }


    public String getname(int id){
        String line = namemap.get(id);
        if(line == null) return "NULL0";
        String[] sline = line.split("\t");
        if(sline.length < 5) return "[NONAME]";
        return sline[3];
    }

    public String getname(String id){
        return getname(Integer.parseInt(id));
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

        try{
            namemap = namedb.hashMap("map")
                    .keySerializer(Serializer.INTEGER)
                    .valueSerializer(Serializer.STRING)
                    .open();
        } catch (Exception e){
            namemap = namedb.hashMap("map")
                    .keySerializer(Serializer.INTEGER)
                    .valueSerializer(Serializer.STRING)
                    .create();
        }

//        List<Integer> ngramorders = Arrays.asList(0,1,4);

        for(Integer i : ngramorders){
            logger.info("opening ngrams-{}.db", i);
            File ngramfile = new File(this.indexdir + "ngrams-uncased-" + i + ".db");
            DB db = DBMaker.fileDB(ngramfile)
                    .fileMmapEnable()
		.closeOnJvmShutdown().readOnly()
                    .make();

            dbarray[i] = db;

            ConcurrentMap<String, int[]> map = db
                    .hashMap("map")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.INT_ARRAY)
                    .open();

            maplist.put(i, map);
        }

        logger.info("Loading altnamemap.db");
        altnamedb = DBMaker.fileDB(new File(this.indexdir + "altnamemap.db"))
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .readOnly()
                .make();

        altnamemap = altnamedb.hashMap("map")
                .keySerializer(Serializer.INTEGER)
                .valueSerializer(Serializer.STRING)
                .open();
    }



    /**
     * This takes a submission file and links all the entries.
     * @param subfile
     */
    public void stringmatcher(String subfile, List<Integer> ngramorder) throws IOException {

        List<String> lines = LineIO.read(subfile);
        ArrayList<String> outlines = new ArrayList<>();
        ArrayList<String> outlines2 = new ArrayList<>();

        int numcands = 20;
        double i = 0;
        int coverage = 0;
        int nils = 0;

        double mrr = 0;
        double acc = 0;
        int numgolds = 0;

        for(String line : lines){
            if(i % 10 == 0){
                System.out.println("Progress: " + (i / lines.size()));
            }

            i++;

            String[] sline = line.split("\t");

            int goldid = -1;
//            try{
//                // the last element...
////                goldid = Integer.parseInt(sline[10].trim());
//                goldid = Integer.parseInt(sline[4].trim());
//                System.out.println("goldid:"+goldid);
//            }catch(NumberFormatException e){
//                goldid = -1;
//                continue;
//            }

            String mention = sline[2];
            String type = sline[5];
            String link = sline[4];
            System.out.println("mention:"+mention+" type:"+type+" link:"+link);


            // Only link null entries.
            // if(!link.equals("NULL")){
            //     outlines.add(StringUtils.join(sline, "\t"));
            //     System.err.println("Skipping ... only link null entries.");
            //     continue;
            // }

            //List<String> transmention = tt.translateName("loc", mention);
            //System.out.print(mention + ", ");
            //if(transmention.size() > 0){
            //    mention = transmention.get(0);
            //}
            //System.out.println(mention);

            //ArrayList<String> mentionngrams = KBStringMatcher.getngrams(mention, 2);

            // do a little caching to speed things up!
            String key = mention + "|||" + type;
            int best = -1;
            if(cache.containsKey(key)){
                best = cache.get(key);
                //outlines2.add(key + " is cached!");
            }else {

                // this maps index to frequency.
                LinkedHashMap<Integer, Double> ret = retrieve(mention, ngramorder, type, maplist, numcands, altnamemap);

                List<Integer> result = ret.entrySet().stream()
                        .map((e) -> e.getKey())
                        .collect(Collectors.toList());

                double nullthreshold = 0.4;
                HashMap<String, Double> typethresholds = new HashMap<>();
                typethresholds.put("GPE", 0.75);
                typethresholds.put("PER", 0.4);
                typethresholds.put("ORG", 0.4);
                typethresholds.put("LOC", 0.4);
                System.out.println("candids:"+result);
                if (result.size() > 0 && ret.get(result.get(0)) > typethresholds.get(type)) {
                    best = result.get(0);
                } else {
                    result.add(0, -1);
                    best = -1;
                }
                cache.put(key, best);

                String[] candsline = line.split("\t");

                List<String> cands = result.stream()
                        .map((r) -> getname(r+"") + ":" + r + ":" + ret.get(r))
                        .collect(Collectors.toList());

                candsline[4] = StringUtils.join(cands, "|");
                candsline[5] = "NAM";

                if(goldid > -1) {
                    int ind = result.indexOf(goldid);

                    if (ind > -1) {
                        coverage++;
                        candsline[0] = "ind:" + ind;
                        mrr += 1. / (ind + 1.);
                        if (ind == 0) {
                            acc += 1;
                        }
                    } else {
                        mrr += 1. / numcands;
                    }
                    numgolds++;
                }
                outlines2.add(StringUtils.join(candsline, "\t"));
            }

            if(best > -1) {
                sline[4] = best + "";
            }else{
                sline[4] = "NULL";
            }

            outlines.add(StringUtils.join(sline, "\t"));
        }

        float numlines = (float) lines.size();
        logger.info("Coverage: " + coverage/(numlines-nils));
        logger.info("MRR: " + mrr / numgolds);
        logger.info("Accuracy: " + acc / numgolds);

        LineIO.write(subfile + ".linked", outlines);
        LineIO.write(subfile + ".cands" + numcands, outlines2);
    }


    public void finish() {
        namedb.close();
        altnamedb.close();
        for(Integer i : ngramorders){
            dbarray[i].close();
        }
        closeCacheDb();
    }

    public void report() {
        System.out.println("hits: "+total_hits+" total:"+total+" frac="+1.0*total_hits/total);
        System.out.println("nils: "+nils+" total: "+total+" frac="+1.0*nils/total);
    }




}
