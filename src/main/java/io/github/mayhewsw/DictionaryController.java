package io.github.mayhewsw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Enumeration;

/**
 * Created by mayhew2 on 2/3/17.
 */

@Controller
@RequestMapping("/dict")
public class DictionaryController {

    private static Logger logger = LoggerFactory.getLogger(DictionaryController.class);

    public DictionaryController(){
        for(int i= 0; i < 40; i++) {
            logger.debug("THIS IS THE DICTONARY CONTROLLER!!!");
        }
    }

    @RequestMapping(value = "/addword", method= RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String addword(@RequestParam(value="key") String key, @RequestParam(value="def") String def, @RequestParam(value="id", required=true) String taid, HttpSession hs) throws Exception {
        logger.info("Adding to dict: " + key + " -> " + def);

        Enumeration<String> atts = hs.getAttributeNames();

        while(atts.hasMoreElements()){
            System.out.println(atts.nextElement());
        }

        return "WOOOOOOOOT";
    }


}
