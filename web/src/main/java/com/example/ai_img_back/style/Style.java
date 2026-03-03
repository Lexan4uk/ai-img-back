package com.example.ai_img_back.style;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Style {

    private Long id;
    private Long createdByUserId;
    private String name;
    private String stylePrompt;
}
