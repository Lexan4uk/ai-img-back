package com.example.ai_img_back.asset;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Asset — сгенерированное изображение.
 *
 * Хранит результат одной генерации:
 * - ссылки на тип, стиль, промпт
 * - снапшоты всех промптов (на момент генерации)
 * - файл на диске (file_uri)
 * - мета-информация от AI (provider, model, meta)
 *
 * Снапшоты нужны потому что тип/стиль могут быть удалены
 * (asset переедет на "Неопределённый"), а промпт —
 * одноразовый. Снапшоты сохраняют историю.
 */
@Getter
@Setter
@Accessors(chain = true)
public class Asset {

    private UUID id;
    private UUID ownerUserId;

    private UUID imageTypeId;
    private UUID styleId;
    private UUID promptId;

    /** Текст, который пользователь ввёл */
    private String userPromptSnapshot;
    /** type_prompt на момент генерации */
    private String typePromptSnapshot;
    /** style_prompt на момент генерации */
    private String stylePromptSnapshot;
    /** Итоговый промпт, отправленный в AI */
    private String finalPromptSnapshot;
    /** sha256(final_prompt + generation_params) — для дедупликации */
    private String finalPromptHash;

    /** Путь к файлу на диске */
    private String fileUri;
    private String provider;
    private String model;
    /** JSON с доп. информацией от AI */
    private String meta;

    private OffsetDateTime createdAt;
}
