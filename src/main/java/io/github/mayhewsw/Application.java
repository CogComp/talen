package io.github.mayhewsw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import javax.servlet.http.HttpSessionListener;

@SpringBootApplication
public class Application extends WebMvcConfigurerAdapter {

    public static void main(String[] args) {

        SpringApplication app = new SpringApplication(Application.class);
        app.run(args);
    }

}
