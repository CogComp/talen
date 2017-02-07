package io.github.mayhewsw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Created by stephen on 2/4/17.
 */

@Controller
@RequestMapping("/rules")
public class RuleController {

    private static Logger logger = LoggerFactory.getLogger(RuleController.class);

    public RuleController() {

        // What do do here?
        logger.error("LOADING THE RULE CONTROLLER!!!");

    }






}
