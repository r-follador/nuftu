package com.genewarrior.nuftu;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.web.cors.CorsUtils;

@Configuration
@EnableWebSecurity(debug = false)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    public void configure(WebSecurity web) throws Exception {
        //Websecurity takes precedence to HttpSecurity
        web.ignoring().requestMatchers(CorsUtils::isPreFlightRequest)
                .antMatchers("/webjars/**", "/css/**", "/assets/**");


    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .headers().frameOptions().sameOrigin().and()
                .csrf().disable()
                .authorizeRequests()
                .antMatchers("/", "/api/*", "/api/file/*", "/api/thumbnail/*", "/eth/*", "/assets/*", "/create", "/create2_file", "/index", "/how_to_sell", "/how_it_works", "/pricing", "/tos", "/nft/*", "/gasprice", "/mintingcost", "/find", "/payment_hook", "/mint/*").permitAll()
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
                .and()
                .logout()
                .permitAll();
    }

}