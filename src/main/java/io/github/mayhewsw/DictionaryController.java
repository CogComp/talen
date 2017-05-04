package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * Created by mayhew2 on 2/3/17.
 */

@Controller
@RequestMapping("/dict")
public class DictionaryController {

    private static Logger logger = LoggerFactory.getLogger(DictionaryController.class);

    @RequestMapping(value="", method=RequestMethod.GET)
    public String showdict() {
        return "dict";
    }

    @RequestMapping(value="lookup", method=RequestMethod.GET)
    @ResponseBody
    public String getdict(@RequestParam(value="word") String word, HttpSession hs, Model model) {

        // pass a dict list to this?
        Dictionary dict = (Dictionary) hs.getAttribute("dict");

        List<String> defs = dict.get(word);

        String ret = "No definition found";
        if(defs != null){
            ret = defs.toString();
        }

        return ret;
    }


    @RequestMapping(value="add", method=RequestMethod.GET)
    @ResponseBody
    public String adddef(@RequestParam(value="key") String key, @RequestParam(value="val") String val, @RequestParam(value="taid") String taid, HttpSession hs, Model model) {

        SessionData sd = new SessionData(hs);

        TreeMap<String, TextAnnotation> tas = sd.tas;
        TextAnnotation ta = tas.get(taid);

        String folderpath = sd.prop.getProperty("path");

        // write it out to file. Don't care if the file is clobbered...
        String folderparent = (new File(folderpath)).getParent();
        File dictfile = new File(folderparent, "dict-" + sd.dataname + "-" + sd.username + ".txt");

        if(val.length() > 0) {
            try {
                if (dictfile.exists()) {
                    LineIO.append(dictfile.getAbsolutePath(), Collections.singletonList(key + "\t" + val));
                } else {
                    LineIO.write(dictfile.getAbsolutePath(), Collections.singletonList(key + "\t" + val));
                }
            } catch (IOException e) {
                logger.error("Could not save dict file: " + dictfile.getAbsolutePath());
            }
            sd.dict.add(key, val);
        }

        return AnnotationController.getHTMLfromTA(ta, sd);
    }




}
