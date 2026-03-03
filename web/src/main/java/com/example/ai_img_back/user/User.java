package com.example.ai_img_back.user;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class User {

    private Long id;
    private String email;
    private String displayName;
}
