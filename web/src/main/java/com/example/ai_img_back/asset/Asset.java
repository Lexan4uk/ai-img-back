package com.example.ai_img_back.asset;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Asset {

    private Long id;
    private Long imageTypeId;
    private Long styleId;
    private String fileUri;
    private OffsetDateTime createdAt;
}
