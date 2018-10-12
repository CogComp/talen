package io.github.mayhewsw.controllers;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import io.github.mayhewsw.Dictionary;
import io.github.mayhewsw.SessionData;
import io.github.mayhewsw.utils.HtmlGenerator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.File;
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
    public String showdict(Model model, HttpSession hs) {
        SessionData sd = new SessionData(hs);
        // this is called if we go to /dict

        if(sd.dict == null){
            Dictionary dict = new Dictionary(sd.dataname, sd.username);
            hs.setAttribute("dict", dict);
            sd.dict = dict;
        }

        List<Pair<String, String>> entries = new ArrayList<>();
        int i = 0;
        for (String key : sd.dict.keySet()) {
            String valstring = StringUtils.join(sd.dict.get(key), ", ");
            Pair<String, String> p = new Pair<>(key, valstring);
            entries.add(p);
            i++;

            // hard break at i == 1000
            if (i > 1000) {
                break;
            }
        }
        model.addAttribute("entries", entries);

        return "dict";
    }

    @RequestMapping(value="lookup", method=RequestMethod.GET)
    @ResponseBody
    public String getdict(@RequestParam(value="word") String word, HttpSession hs, Model model) {

        // pass a dict list to this?
        io.github.mayhewsw.Dictionary dict = (Dictionary) hs.getAttribute("dict");

        List<String> defs = dict.get(word);

        String ret = "No definition found";
        if(defs != null){
            ret = defs.toString();
        }

        return ret;
    }


    @RequestMapping(value="add", method=RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String adddef(@RequestParam(value="key") String key, @RequestParam(value="val") String val, HttpSession hs) throws IOException {

        SessionData sd = new SessionData(hs);

        logger.info("Adddef: add " + key + " -> " + val);

        if(key.length() > 0 && val.length() > 0) {
            sd.dict.add(key, val);
            sd.dict.save(sd.dataname, sd.username);
        }
        return "Success";
    }




}
