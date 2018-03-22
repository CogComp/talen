package io.github.mayhewsw.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpSession;

/**
 * Created by stephen on 8/2/17.
 */
@SuppressWarnings("ALL")
@Controller
public class LoginController {

    private static Logger logger = LoggerFactory.getLogger(LoginController.class);

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String adduser(HttpSession hs) {
        // really... nothing happens here.

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName(); //get logged in username

        logger.info("Username is " + name);

        hs.setAttribute("username", name);
        return "index";
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login(HttpSession hs) {

        return "login";
    }

    @RequestMapping(value = "/logout")
    public void logout(HttpSession hs) {
        logger.info("Logging out...");

        // I think this is preferable.
        hs.invalidate();
    }
}
