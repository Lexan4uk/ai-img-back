package com.example.ai_img_back.imagetype;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImageType {

    private Long id;
    private Long createdByUserId;
    private String name;
    private String typePrompt;
}
