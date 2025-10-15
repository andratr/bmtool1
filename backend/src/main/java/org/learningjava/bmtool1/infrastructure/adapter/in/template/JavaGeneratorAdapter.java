package org.learningjava.bmtool1.infrastructure.adapter.in.template;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.learningjava.bmtool1.domain.model.TargetJavaClassForConsumer;

import java.io.StringWriter;

public class JavaGeneratorAdapter {
    private final Configuration cfg;

    public JavaGeneratorAdapter() throws Exception {
        cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setClassLoaderForTemplateLoading(
                getClass().getClassLoader(),
                "/templates"
        );
    }

    public String generate(TargetJavaClassForConsumer model) throws Exception {
        Template template = cfg.getTemplate("JavaClass.ftl");
        try (StringWriter out = new StringWriter()) {
            template.process(model, out);
            return out.toString();
        }
    }
}
