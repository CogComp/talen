package io.github.mayhewsw.utils;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.IOUtils;
import edu.illinois.cs.cogcomp.core.utilities.SerializationHelper;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.CoNLLNerReader;
import io.github.mayhewsw.controllers.Common;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class IO {
    private static Logger logger = LoggerFactory.getLogger(IO.class);

    /**
     * This loads a group of textannotations from
     * @param foldertype
     * @param folder
     * @param ret
     * @throws Exception
     */
    public static void read(String foldertype, String folder, Map<String, TextAnnotation> ret) throws Exception {

        if (foldertype.equals(Common.FOLDERTA)) {
            File outf = new File(folder);

            if(!outf.exists()){
                throw new FileNotFoundException("Folder " + folder + " does not exist.");
            }

            File[] files = outf.listFiles();
            int limit = Math.min(files.length, 500);

            for (int i = 0; i < limit; i++) {
                File file = files[i];
                TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(file.getAbsolutePath());
                // NOTE: ideally file.getName() == ta.getID(), but this is not always the case.
                //ret.put(file.getName(), ta);
                ret.put(ta.getId(), ta);
            }
        }else if(foldertype.equals(Common.FOLDERTAJSON)){

            File outf = new File(folder);

            if(!outf.exists()){
                throw new FileNotFoundException("Folder " + folder + " does not exist.");
            }

            File[] files = outf.listFiles();

            int limit = Math.min(files.length, 500);

            for (int i = 0; i < limit; i++) {
                File file = files[i];
                TextAnnotation ta = SerializationHelper.deserializeTextAnnotationFromFile(file.getAbsolutePath(), true);
                ret.put(file.getName(), ta);
            }
        } else if (foldertype.equals(Common.FOLDERCONLL)) {
            CoNLLNerReader cnl = new CoNLLNerReader(folder);
            while (cnl.hasNext()) {
                TextAnnotation ta = cnl.next();
                logger.info("Loading: " + ta.getId());

                ret.put(ta.getId(), ta);
            }
        }else if (foldertype.equals(Common.FOLDERCOLUMN)) {
            ColumnReader cnl = new ColumnReader(folder);
            while (cnl.hasNext()) {
                TextAnnotation ta = cnl.next();
                logger.info("Loading: " + ta.getId());

                ret.put(ta.getId(), ta);
            }
        }

    }

    /**
     * This saves an individual TextAnnotation to the desired output folder.
     * @param foldertype
     * @param path
     * @param ta
     * @throws IOException
     */
    public static void save(String foldertype, String path, TextAnnotation ta) throws IOException {
        if(!IOUtils.exists(path)) {
            IOUtils.mkdir(path);
        }

        if(foldertype.equals(Common.FOLDERTA)) {
            SerializationHelper.serializeTextAnnotationToFile(ta, path + "/" + ta.getId(), true);
        }else if(foldertype.equals(Common.FOLDERTAJSON)) {
            SerializationHelper.serializeTextAnnotationToFile(ta, path + "/" + ta.getId(), true, true);
        }else if(foldertype.equals(Common.FOLDERCONLL)) {
            CoNLLNerReader.TaToConll(Collections.singletonList(ta), path);
        }else if(foldertype.equals(Common.FOLDERCOLUMN)) {
            ColumnReader.TaToColumn(Collections.singletonList(ta), path);
        }

    }

}
