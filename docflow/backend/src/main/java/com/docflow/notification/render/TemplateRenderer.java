package com.docflow.notification.render;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    public String render(String template, Map<String, Object> context) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, Object> safeContext = context != null ? context : Map.of();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object replacement = safeContext.get(key);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement != null ? String.valueOf(replacement) : ""));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
