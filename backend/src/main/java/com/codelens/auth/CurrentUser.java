package com.codelens.auth;

import com.codelens.common.UnauthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return Long.parseLong(jwt.getToken().getSubject());
        }
        throw new UnauthorizedException("not signed in");
    }
}
