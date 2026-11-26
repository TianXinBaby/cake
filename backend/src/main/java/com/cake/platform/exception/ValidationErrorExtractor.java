package com.cake.platform.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Resolves public JSON names from Jackson metadata; never exports raw binding/exception text. */
final class ValidationErrorExtractor {
    private static final Set<String> MESSAGES = Set.of("名称不能为空", "昵称不能为空", "昵称长度不能超过30个字符",
            "密码不能为空", "密码长度不符合要求", "手机号格式不正确", "参数错误");

    private ValidationErrorExtractor() {
    }

    static Map<String, List<String>> extract(BindingResult bindingResult) {
        return extract(bindingResult, bindingResult.getTarget() == null ? null
                : bindingResult.getTarget().getClass(), new ObjectMapper());
    }

    static Map<String, List<String>> extract(Errors errors, Class<?> targetType, ObjectMapper mapper) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        errors.getFieldErrors().forEach(error -> add(result, jsonPath(targetType, error.getField(), mapper),
                error.isBindingFailure() ? "参数类型不正确" : message(error.getDefaultMessage(), error.getCode())));
        return ParameterErrorData.copyErrors(result);
    }

    static String message(String defaultMessage, String constraint) {
        // Custom templates can interpolate rejected values. Only reviewed fixed text is public.
        if (MESSAGES.contains(defaultMessage == null ? "" : defaultMessage)) { return defaultMessage; }
        if (constraint == null) { return "参数错误"; }
        return switch (constraint) {
            case "NotBlank", "NotEmpty", "NotNull" -> "参数不能为空";
            case "Size", "Length" -> "参数长度不符合要求";
            case "Pattern", "Email" -> "参数格式不正确";
            case "Min", "DecimalMin", "Positive", "PositiveOrZero" -> "参数小于允许的最小值";
            case "Max", "DecimalMax", "Negative", "NegativeOrZero" -> "参数大于允许的最大值";
            case "Digits" -> "参数精度不符合要求";
            default -> "参数错误";
        };
    }

    static void add(Map<String, List<String>> errors, String field, String message) {
        if (ParameterErrorData.safePath(field)) {
            errors.computeIfAbsent(field, ignored -> new ArrayList<>()).add(message);
        }
    }

    static String jsonPath(Class<?> type, String path, ObjectMapper mapper) {
        return type == null ? null : jsonPath(mapper.constructType(type), path, mapper);
    }

    static String jsonPath(JavaType type, String path, ObjectMapper mapper) {
        if (type == null || path == null) { return null; }
        List<String> publicParts = new ArrayList<>();
        for (String part : path.split("\\.", -1)) {
            if (!part.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\[[0-9]+\\])?")) { return null; }
            String internal = part.replaceFirst("\\[.*", "");
            String index = part.substring(internal.length());
            var properties = mapper.getDeserializationConfig().introspect(type).findProperties();
            var property = properties.stream().filter(p -> p.couldDeserialize()
                    && (p.getInternalName().equals(internal) || p.getName().equals(internal))).findFirst();
            if (property.isEmpty() || !ParameterErrorData.safeField(property.get().getName())) { return null; }
            publicParts.add(property.get().getName() + index);
            var member = property.get().getPrimaryMember();
            type = member == null ? null : member.getType();
            if (!index.isEmpty()) {
                if (type == null || !type.isContainerType()) { return null; }
                type = type.getContentType();
            }
            if (type == null) { return null; }
        }
        return String.join(".", publicParts);
    }

    static String parameterName(MethodParameter parameter) {
        var query = parameter.getParameterAnnotation(RequestParam.class);
        var path = parameter.getParameterAnnotation(PathVariable.class);
        String name = query != null ? (!query.name().isEmpty() ? query.name() : query.value())
                : path != null ? (!path.name().isEmpty() ? path.name() : path.value()) : null;
        if (name != null && name.isEmpty()) { name = parameter.getParameterName(); }
        return ParameterErrorData.safeField(name) ? name : null;
    }

    static Map<String, List<String>> violations(Set<? extends ConstraintViolation<?>> violations,
                                               ObjectMapper mapper) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var violation : violations) {
            List<String> parts = new ArrayList<>();
            boolean executable = false;
            Integer parameterIndex = null;
            String methodName = null;
            for (var node : violation.getPropertyPath()) {
                if (node.getKind() == ElementKind.RETURN_VALUE) { return Map.of(); }
                if (node.getKind() == ElementKind.METHOD) { executable = true; methodName = node.getName(); }
                else if (node.getKind() == ElementKind.PARAMETER) {
                    parameterIndex = node.as(jakarta.validation.Path.ParameterNode.class).getParameterIndex();
                } else if (node.getKind() == ElementKind.PROPERTY) {
                    if (node.isInIterable() && node.getIndex() != null && !parts.isEmpty()) {
                        int last = parts.size() - 1;
                        parts.set(last, parts.get(last) + "[" + node.getIndex() + "]");
                    }
                    parts.add(node.getName());
                }
            }
            String field = null;
            if (!executable) {
                field = jsonPath(violation.getRootBeanClass(), String.join(".", parts), mapper);
            } else if (parameterIndex != null && violation.getRootBean() != null) {
                final String method = methodName;
                var methods = java.util.Arrays.stream(violation.getRootBeanClass().getMethods())
                        .filter(m -> m.getName().equals(method)).toList();
                if (methods.size() == 1 && parameterIndex < methods.get(0).getParameterCount()) {
                    var parameter = new MethodParameter(methods.get(0), parameterIndex);
                    parameter.initParameterNameDiscovery(new org.springframework.core.DefaultParameterNameDiscoverer());
                    field = parts.isEmpty() ? parameterName(parameter)
                            : jsonPath(parameter.getParameterType(), String.join(".", parts), mapper);
                }
            }
            add(result, field, message(violation.getMessage(),
                    violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()));
        }
        return ParameterErrorData.copyErrors(result);
    }
}
