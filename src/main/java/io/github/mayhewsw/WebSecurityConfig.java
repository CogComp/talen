package io.github.mayhewsw;


import edu.illinois.cs.cogcomp.core.io.LineIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import java.util.ArrayList;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/css/**", "/js/**", "/resources/**").permitAll()
                   .anyRequest().authenticated()
                   .and()
                .formLogin()
                   .loginPage("/login")
                   .defaultSuccessUrl("/", true)
                   .permitAll()
                   .and()
                .logout()
                   .permitAll();
        http.csrf().disable();
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {

        ArrayList<String> users = LineIO.read("config/users.txt");

        for(String up : users){
            String[] userpass = up.split("\t");
            auth.inMemoryAuthentication()
                    .withUser(userpass[0]).password(userpass[1]).roles("USER");
        }
    }
}