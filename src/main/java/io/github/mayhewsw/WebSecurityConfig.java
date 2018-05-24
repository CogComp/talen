package io.github.mayhewsw;


import edu.illinois.cs.cogcomp.core.io.LineIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.io.FileNotFoundException;
import java.util.ArrayList;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/css/**", "/js/**", "/img/**", "/resources/**").permitAll()
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

    @Bean
    @Override
    public UserDetailsService userDetailsService() {

        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();

        try {
            ArrayList<String> users = LineIO.read("config/users.txt");

            for(String up : users){
                String[] userpass = up.split("\\s+");
                if(userpass.length == 2) {
                    UserDetails user = User.withDefaultPasswordEncoder()
                            .username(userpass[0])
                            .password(userpass[1])
                            .roles("USER")
                            .build();
                    manager.createUser(user);
                }
            }

        } catch (FileNotFoundException e) {
            System.err.println("config/users.txt file not found. Using default user/password instead.");
            UserDetails user = User.withDefaultPasswordEncoder()
                    .username("user")
                    .password("password")
                    .roles("USER")
                    .build();
            manager.createUser(user);
        }
        return manager;
    }
}