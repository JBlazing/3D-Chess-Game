package com.jblazing.auth;


import org.springframework.context.annotation.Profile;

@Profile("Auth")
public interface AuthService {
    String grantCode(String code);
}
