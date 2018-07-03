package edu.illinois.cs.cogcomp.lorelei.kb;

import edu.illinois.cs.cogcomp.lorelei.edl.KBEntity;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;

/**
 * Created by Shyam on 7/3/18.
 */
public class GeonamesLoader {

    final static String[] fields = {"origin", "entity_type", "entityid", "name", "asciiname", "latitude", "longitude", "feature_class",
            "feature_class_name", "feature_code", "feature_code_name", "feature_code_description", "country_code",
            "country_code_name", "cc2", "admin1_code", "admin1_code_name", "admin2_code", "admin2_code_name",
            "admin3_code", "admin4_code", "population", "elevation", "dem", "timezone", "modification_date",
            "per_gpe_loc_of_association", "per_title_or_position", "per_org_of_association", "per_role_in_incident",
            "per_year_of_birth", "per_year_of_death", "per_gender", "per_family_member", "note", "aim",
            "org_date_established", "date_established_note", "org_website", "org_gpe_loc_of_association",
            "org_members_employees_per", "org_parent_org", "executive_board_members", "jurisdiction",
            "trusteeship_council", "national_societies", "external_link"};
    private final String indexdir;

    public GeonamesLoader(String ilcode)
    {

        this.indexdir = "/shared/preprocessed/upadhya3/il"+ilcode+"_index/";
        load_namemap();
//        kbmap = new HashMap<>();
//        String basepath = "/shared/corpora/corporaWeb/lorelei/evaluation-2018/";
//        String kbfile = basepath + "il"+ilcode+"/source/kb/IL"+ilcode+"_kb/data/entities.tab";
    }
    DB namedb;
    HTreeMap<Integer, String> namemap;

    private void load_namemap() {
        System.err.println("opening namedb..");
        namedb = DBMaker.fileDB(new File(this.indexdir+"/namemap.db"))
                .fileMmapEnable()
                .closeOnJvmShutdown()
                .readOnly()
                .make();

        namemap = namedb.hashMap("map")
                .keySerializer(Serializer.INTEGER)
                .valueSerializer(Serializer.STRING)
                .open();
    }

    public KBEntity get(int eid){
        if(!namemap.containsKey(eid))
        {
            System.err.println("key not found"+eid);
            System.exit(0);
        }
        String line = namemap.get(eid);
        KBEntity bke = new KBEntity(line.split("\t"));
        return bke;
    }


    public static void main(String[] args) {
        GeonamesLoader geonames = new GeonamesLoader("9");
        System.out.println(geonames.get(7659657));

    }
//    public void build_index(String indexpath){
//        DB namedb = DBMaker.fileDB(indexpath)
//                .fileMmapEnable()
//                .closeOnJvmShutdown()
//                .make();
//
//        namemap = namedb.hashMap("map")
//                .keySerializer(Serializer.INTEGER)
//                .valueSerializer(Serializer.STRING)
//                .create();
//    }

//    public void process_kb(String kbfile) throws IOException {
//        File file = new File(kbfile);
//        BufferedReader reader = new BufferedReader(new FileReader(file));
//
//        String line;
//        int c = 0;
//        while ((line = reader.readLine()) != null) {
//            if(c>0 && c % 1000000==0)
//                System.err.println("read "+c);
//            String[] parts = line.split("\\t");
//            if (parts.length != fields.length)
//            {
//                System.err.println("bad line");
//                continue;
//            }
//            Map<String,String> kbentry = new HashMap<>();
//            for(int fidx=0;fidx<fields.length;fidx++)
//            {
//                if(parts[fidx].length()>0)
//                    kbentry.put(fields[fidx],parts[fidx]);
//            }
//            String eid = kbentry.get("entityid");
//            kbmap.put(eid,kbentry);
//            c++;
//        }
//
////        for idx, line in enumerate(open(kbfile)):
////        if idx > 0 and idx % 1000000 == 0:
////        logging.info("read %d lines", idx)
////        parts = line.rstrip('\n').split('\t')
////        if len(parts) != len(fields):
////        logging.info("bad line %d", idx)
////        continue
////                endict = {}
////        for field, v in zip(fields, parts):
////        if len(v) != 0:
////        endict[field] = v
////        docbuffer.append(endict)
////        if idx > 0 and idx % self.insert_freq == 0:
////        try:
////        self.geonames_cll.insert_many(docbuffer)
////        logging.info("inserting %d lines", idx)
////        docbuffer = []
////        except BulkWriteError as bwe:
////        logging.info(bwe.details)
//    }
}
