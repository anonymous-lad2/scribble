package com.scribble.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthenticatedUser {
    private final String userId;
    private final String username;
    private final String role;
}
