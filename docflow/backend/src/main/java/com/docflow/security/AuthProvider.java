package com.docflow.security;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthProvider {

    UserContext authenticate(HttpServletRequest request);
}
