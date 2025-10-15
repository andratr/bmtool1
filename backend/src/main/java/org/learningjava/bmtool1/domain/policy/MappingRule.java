package org.learningjava.bmtool1.domain.policy;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MappingRule {

    @JsonProperty("plsqlType")
    private String plsqlType;

    @JsonProperty("plsqlContains")
    private String plsqlContains; // <-- NEW

    @JsonProperty("javaType")
    private String javaType;

    @JsonProperty("javaContains")
    private String javaContains;

    public String getPlsqlType() {
        return plsqlType;
    }

    public void setPlsqlType(String plsqlType) {
        this.plsqlType = plsqlType;
    }

    public String getPlsqlContains() {
        return plsqlContains;
    }

    public void setPlsqlContains(String plsqlContains) {
        this.plsqlContains = plsqlContains;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public String getJavaContains() {
        return javaContains;
    }

    public void setJavaContains(String javaContains) {
        this.javaContains = javaContains;
    }
}
