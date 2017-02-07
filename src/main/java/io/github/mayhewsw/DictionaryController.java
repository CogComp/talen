package io.github.mayhewsw;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

/**
 * Created by mayhew2 on 2/3/17.
 */

@Controller
@RequestMapping("/dict")
public class DictionaryController {

    private static Logger logger = LoggerFactory.getLogger(DictionaryController.class);

    private HashMap<String, String> dictpaths;

    public DictionaryController() throws FileNotFoundException {
        logger.info("Loading dictionary.txt");
        List<String> dictlines = LineIO.read("config/dictionary.txt");
        dictpaths = new HashMap<>();

        for(String line : dictlines){
            String[] sl = line.trim().split("\\s+");
            String dictname = sl[0];
            String dictpath = sl[1];
            dictpaths.put(dictname, dictpath);
        }
    }

    @RequestMapping(value = "load", method=RequestMethod.GET)
    public String loaddict(@RequestParam(value="dictname") String dictname, HttpSession hs) {
        String dictpath = dictpaths.get(dictname);
        Dictionary dict = new Dictionary(dictname, dictpath);

        hs.setAttribute("dict", dict);

        return "redirect:/dict";
    }

    @RequestMapping(value="", method=RequestMethod.GET)
    public String showdict(HttpSession hs, Model model) {

        model.addAttribute("dictnames", dictpaths.keySet());

        return "dict";
    }

    @RequestMapping(value="unload", method=RequestMethod.GET)
    public String unload(HttpSession hs, Model model) {

        model.addAttribute("dictnames", dictpaths.keySet());
        hs.setAttribute("dict", new Dictionary());

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


}
