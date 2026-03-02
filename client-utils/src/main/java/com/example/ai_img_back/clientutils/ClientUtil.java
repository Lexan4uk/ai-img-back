package com.example.ai_img_back.clientutils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.*;

/**
 * UserId передаётся автоматически через OkHttp Interceptor —
 * не нужно указывать в каждом методе.
 */
@SuppressWarnings("unused")
public class ClientUtil {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** Кеш — чтобы не создавать прокси каждый раз для одного интерфейса */
    private final Map<Class<?>, Object> clients = new HashMap<>();
    public ClientUtil(String baseUrl, UUID userId) {
        this.baseUrl = baseUrl;

        /*
         * OkHttp Interceptor.
         * Перехватывает КАЖДЫЙ запрос и добавляет header.
         */
        this.httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request withHeader = original.newBuilder()
                            .header("UserId", userId.toString())
                            .build();
                    return chain.proceed(withHeader);
                })
                .build();

        /*
         * ObjectMapper — JSON парсер/сериализатор (Jackson).
         */
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Создаёт прокси-клиент для интерфейса контроллера.
     *
     * Использование:
     *   IImageTypeController ctrl = clientUtil.getClient(IImageTypeController.class);
     *   ctrl.getAll();        // → GET /image-types
     *   ctrl.delete(someId);  // → DELETE /image-types/{id}
     */
    @SuppressWarnings("unchecked")
    public <T> T getClient(Class<T> controller) {
        // Читаем базовый путь с @RequestMapping на интерфейсе
        // Например: @RequestMapping("/image-types") → "/image-types"
        String controllerUrl =
                controller.getAnnotation(RequestMapping.class).value()[0];

        if (!clients.containsKey(controller)) {
            clients.put(controller, Proxy.newProxyInstance(
                    controller.getClassLoader(),
                    new Class[]{controller},
                    (proxy, method, args) -> {

                        // === ШАГ 1: Определяем HTTP-метод и путь из аннотации ===

                        String httpMethod; // "GET", "POST", "DELETE"
                        String methodUrl;  // Часть пути после controllerUrl

                        GetMapping getMapping = method.getAnnotation(GetMapping.class);
                        PostMapping postMapping = method.getAnnotation(PostMapping.class);
                        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);

                        if (getMapping != null) {
                            httpMethod = "GET";
                            methodUrl = getMapping.value().length > 0
                                    ? getMapping.value()[0] : "";
                        } else if (postMapping != null) {
                            httpMethod = "POST";
                            methodUrl = postMapping.value().length > 0
                                    ? postMapping.value()[0] : "";
                        } else if (deleteMapping != null) {
                            httpMethod = "DELETE";
                            methodUrl = deleteMapping.value().length > 0
                                    ? deleteMapping.value()[0] : "";
                        } else {
                            throw new UnsupportedOperationException(
                                    "Метод " + method.getName()
                                            + " не имеет @GetMapping/@PostMapping/@DeleteMapping");
                        }

                        // === ШАГ 2: Разбираем параметры метода ===

                        String urlPath = controllerUrl + methodUrl;
                        Map<String, Object> pathParams = new HashMap<>();
                        Map<String, Object> queryParams = new HashMap<>();
                        Object payload = null; // тело запроса (для POST)

                        Parameter[] parameters = method.getParameters();

                        for (int i = 0; i < parameters.length; i++) {
                            Parameter parameter = parameters[i];
                            Object parameterValue =
                                    args != null ? args[i] : null;

                            if (parameterValue == null) continue;

                            Annotation[] annotations = parameter.getAnnotations();

                            // Без аннотаций — считаем query-параметром
                            if (annotations.length == 0) {
                                if (isPrimitiveOrString(parameterValue)
                                        || parameterValue instanceof Collection<?>
                                        || parameterValue instanceof Object[]) {
                                    addQueryParam(queryParams,
                                            parameter.getName(), parameterValue);
                                } else {
                                    handleModelValue(queryParams, parameterValue);
                                }
                                continue;
                            }

                            for (Annotation annotation : annotations) {
                                if (annotation instanceof RequestParam rp) {
                                    String paramName =
                                            !rp.value().isEmpty() ? rp.value() :
                                            !rp.name().isEmpty() ? rp.name() :
                                            parameter.getName();
                                    addQueryParam(queryParams,
                                            paramName, parameterValue);
                                    break;
                                }

                                if (annotation instanceof ModelAttribute) {
                                    handleModelValue(queryParams, parameterValue);
                                    break;
                                }

                                if (annotation instanceof PathVariable pv) {
                                    String varName =
                                            !pv.value().isEmpty() ? pv.value() :
                                            !pv.name().isEmpty() ? pv.name() :
                                            parameter.getName();
                                    pathParams.put(varName, parameterValue);
                                    break;
                                }

                                if (annotation instanceof
                                        org.springframework.web.bind.annotation
                                                .RequestBody) {
                                    payload = parameterValue;
                                    break;
                                }
                            }
                        }

                        // === ШАГ 3: Собираем URL ===

                        // Подставляем path variables: /image-types/{id} → /image-types/abc-123
                        String expandedPath =
                                expandPathVariables(urlPath, pathParams);

                        // Добавляем query params: ?imageTypeId=xxx&styleId=yyy
                        HttpUrl.Builder urlBuilder =
                                HttpUrl.parse(baseUrl + expandedPath).newBuilder();

                        for (Map.Entry<String, Object> entry
                                : queryParams.entrySet()) {
                            Object value = entry.getValue();
                            if (value instanceof Collection<?> collection) {
                                for (Object item : collection) {
                                    urlBuilder.addQueryParameter(
                                            entry.getKey(), String.valueOf(item));
                                }
                            } else if (value instanceof Object[] array) {
                                for (Object item : array) {
                                    urlBuilder.addQueryParameter(
                                            entry.getKey(), String.valueOf(item));
                                }
                            } else {
                                urlBuilder.addQueryParameter(
                                        entry.getKey(), String.valueOf(value));
                            }
                        }

                        HttpUrl url = urlBuilder.build();

                        // === ШАГ 4: Создаём и выполняем HTTP-запрос ===

                        Request request;
                        switch (httpMethod) {
                            case "GET" -> request = new Request.Builder()
                                    .url(url)
                                    .get()
                                    .build();
                            case "DELETE" -> request = new Request.Builder()
                                    .url(url)
                                    .delete()
                                    .build();
                            default -> { // POST
                                RequestBody body = payload != null
                                        ? RequestBody.create(
                                        objectMapper.writeValueAsString(payload),
                                        JSON_MEDIA_TYPE)
                                        : RequestBody.create("", JSON_MEDIA_TYPE);
                                request = new Request.Builder()
                                        .url(url)
                                        .post(body)
                                        .build();
                            }
                        }

                        String jsonResponse;
                        try (okhttp3.Response response =
                                     httpClient.newCall(request).execute()) {
                            ResponseBody responseBody = response.body();
                            String bodyString = responseBody != null
                                    ? responseBody.string() : "";

                            if (!response.isSuccessful()) {
                                throw new RuntimeException(
                                        bodyString.isEmpty()
                                                ? "HTTP " + response.code()
                                                : bodyString);
                            }
                            jsonResponse = bodyString;
                        }

                        // === ШАГ 5: Десериализуем ответ ===

                        // void методы (delete, addFavorite) — ничего не возвращают
                        if (jsonResponse.isEmpty()
                                || method.getReturnType() == void.class) {
                            return null;
                        }

                        // Для остальных — парсим JSON в нужный тип.
                        // method.getGenericReturnType() сохраняет дженерики:
                        // List<ImageTypeDTO> → не просто List, а List<ImageTypeDTO>
                        Type genericReturnType = method.getGenericReturnType();
                        JavaType javaType = objectMapper.getTypeFactory()
                                .constructType(genericReturnType);
                        return objectMapper.readValue(jsonResponse, javaType);
                    })
            );
        }

        return (T) clients.get(controller);
    }

    // --- Вспомогательные методы ---

    /** Подставляет {id} → реальное значение в URL */
    private String expandPathVariables(
            String path, Map<String, Object> pathParams) {
        String result = path;
        for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            result = result.replace(placeholder,
                    String.valueOf(entry.getValue()));
        }
        return result;
    }

    /** Проверяет: это примитив/строка/число/boolean? */
    private boolean isPrimitiveOrString(Object value) {
        return value.getClass().isPrimitive()
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    /** Добавляет query-параметр */
    private void addQueryParam(
            Map<String, Object> queryParams,
            String parameterName, Object value) {
        queryParams.put(parameterName, value);
    }

    /**
     * Раскладывает объект в query-параметры по полям.
     * Для @ModelAttribute — каждое поле объекта = отдельный query param.
     */
    private void handleModelValue(
            Map<String, Object> queryParams, Object parameterValue) {
        try {
            for (Field field
                    : parameterValue.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object fieldValue = field.get(parameterValue);
                if (fieldValue != null) {
                    queryParams.put(field.getName(), fieldValue);
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "Ошибка при обработке параметров запроса", e);
        }
    }
}
