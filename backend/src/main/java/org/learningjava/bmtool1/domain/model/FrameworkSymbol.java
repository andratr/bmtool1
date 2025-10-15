package org.learningjava.bmtool1.domain.model;


//the building blocks your generated code must rely on

public record FrameworkSymbol(
        String className,          // e.g. "com.example.dq.foundation.data.MonetaryValue"
        String symbol,             // e.g. "MonetaryValue#parse"
        String methodSignature,    // e.g. "static MonetaryValue parse(String ccyAmountType)"
        String snippet,            // e.g. 'var m = MonetaryValue.parse("EUR-100.00");'
        String kind,               // "value-object" | "dto" | "service" | "context"
        java.util.List<String> tags // e.g. ["money","parse","value-object"]
) {}