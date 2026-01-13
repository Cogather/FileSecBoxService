package com.example.filesecbox.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable() // 彻底禁用 CSRF 校验
            .authorizeRequests()
            .anyRequest().permitAll(); // 测试期间允许所有请求，不再校验 Basic Auth
    }
}
