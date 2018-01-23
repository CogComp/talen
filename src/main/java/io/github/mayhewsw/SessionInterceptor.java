package io.github.mayhewsw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Created by mayhew2 on 2/3/17.
 */
public class SessionInterceptor implements HandlerInterceptor {

    private static Logger logger = LoggerFactory.getLogger(SessionInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) throws Exception {
        // Don't create a new session if it doesn't exist.
        HttpSession session = request.getSession(false);

        logger.info("REQUESTURI: " + request.getRequestURI());

        if(!request.getRequestURI().startsWith("/setname") &&
                !request.getRequestURI().equals("/") && session != null &&
                session.getAttribute("username") == null) {
            logger.info("Username is null, redirecting to home page.");
            response.sendRedirect("/");
            return false;
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {
        //System.out.println("POSTHANDLE");

    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {

    }
}
