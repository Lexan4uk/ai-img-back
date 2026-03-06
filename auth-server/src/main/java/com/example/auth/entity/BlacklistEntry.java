package com.example.auth.entity;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class BlacklistEntry {

    private String jti;
    private Instant expiresAt;
}
