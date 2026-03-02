package com.example.ai_img_back.imagetype;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Entity типа изображения (иконка, фотография, картина и т.д.).
 *
 * Справочник — общий для всех пользователей.
 * Нередактируемый: только создание и удаление.
 *
 * type_prompt — фрагмент промпта, который добавляется к user_prompt
 * при генерации. Например: "a photograph of" или "an icon of".
 */
@Getter
@Setter
@Accessors(chain = true)
public class ImageType {

    private UUID id;

    /** Кто создал. NULL если пользователь удалён (ON DELETE SET NULL). */
    private UUID createdByUserId;

    /** Название типа (уникальное, case-insensitive) */
    private String name;

    /** Фрагмент промпта для AI. Может быть NULL — тогда не добавляется. */
    private String typePrompt;

    private OffsetDateTime createdAt;
}
