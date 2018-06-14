package io.github.mayhewsw.controllers;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import io.github.mayhewsw.ConfigFile;
import io.github.mayhewsw.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;

import org.springframework.ui.Model;

/**
 * Created by stephen on 8/2/17.
 */
@SuppressWarnings("ALL")
@Controller
public class LoginController {

    private static Logger logger = LoggerFactory.getLogger(LoginController.class);

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String adduser(Model model,  HttpSession hs) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName(); //get logged in username

        logger.info("Username is " + name);
        hs.setAttribute("username", name);

        // This will also add datasets...
        HashMap<String, ConfigFile> datasets = Common.loadConfig();
        hs.setAttribute("datasets", datasets);

        // in case you want to add a new one!
        model.addAttribute("config", new ConfigFile());

        return "index";
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login(HttpSession hs) {

        return "login";
    }

    @PostMapping(value = "/config")
    public String config(@ModelAttribute ConfigFile c, HttpSession hs) throws IOException {

        System.out.println("Writing to: config/" + c.getFname());


        LineIO.write("config/" + c.getFname(), Collections.singletonList(c.toString()));

        return "redirect:/";

    }

    @PostMapping(value = "/loadconfig")
    @ResponseBody
    public String config(@RequestParam(value="config") String configname, Model model, HttpSession hs) throws IOException {
        SessionData sd = new SessionData(hs);
        ConfigFile c = sd.datasets.get(configname);
        model.addAttribute("config", c);

        return "success";
    }


    @RequestMapping(value = "/logout")
    public void logout(HttpSession hs) {
        logger.info("Logging out...");

        // I think this is preferable.
        hs.invalidate();
    }
}
