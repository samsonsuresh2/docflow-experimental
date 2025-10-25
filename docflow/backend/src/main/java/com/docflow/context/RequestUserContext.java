package com.docflow.context;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RequestUserContext {

    private final ThreadLocal<RequestUser> currentUser = new ThreadLocal<>();

    public void setCurrentUser(RequestUser user) {
        currentUser.set(user);
    }

    public Optional<RequestUser> getCurrentUser() {
        return Optional.ofNullable(currentUser.get());
    }

    public RequestUser requireUser() {
        return getCurrentUser().orElseThrow(() -> new IllegalStateException("Request user context not initialized"));
    }

    public void clear() {
        currentUser.remove();
    }
}
