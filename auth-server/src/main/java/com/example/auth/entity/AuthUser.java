package com.example.auth.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class AuthUser {

    private Long id;
    private String email;
    private String displayName;
    private String passwordHash;
    private String role;
}
