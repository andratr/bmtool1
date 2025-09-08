package org.learningjava.bmtool1.domain.model.template;

import java.util.List;

public record TargetJavaClassForConsumer(
        String packageName,
        String className,
        String inputType,
        List<String> methods
) {}
