package io.github.mayhewsw;

import javax.servlet.http.*;

/**
 * This knows when sessions have been created or destroyed.
 * Created by mayhew2 on 2/3/17.
 */
public class SessionListener implements HttpSessionListener {


    @Override
    public void sessionCreated(HttpSessionEvent httpSessionEvent) {
        // this is running, just not doing anything at the moment...
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {

    }
}
