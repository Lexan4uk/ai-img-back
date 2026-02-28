package com.example.ai_img_back.style;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Entity стиля (Айвазовский, Warcraft, помпейская фреска и т.д.).
 *
 * Зеркало ImageType, но с полем stylePrompt вместо typePrompt.
 * Оба — общие справочники, нередактируемые, только create/delete.
 */
@Getter
@Setter
@Accessors(chain = true)
public class Style {

    private UUID id;
    private UUID createdByUserId;
    private String name;

    /** Фрагмент промпта для AI. Например: "in the style of Aivazovsky" */
    private String stylePrompt;

    private OffsetDateTime createdAt;
}
