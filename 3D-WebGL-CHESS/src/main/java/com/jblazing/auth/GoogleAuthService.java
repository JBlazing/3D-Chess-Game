package com.jblazing.auth;

import org.springframework.context.annotation.Profile;

@Profile("Auth")
public class GoogleAuthService implements AuthService {


    @Override
    public String grantCode(String code) {
        return "";
    }
}
