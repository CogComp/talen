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
        // ... customize app settings here

        app.run(args);

        //SpringApplication.run(Application.class, args);
    }

//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(new SessionInterceptor());
//    }


//    @Bean
//    public HttpSessionListener httpSessionListener(){
//        return new SessionListener();
//    }

}
