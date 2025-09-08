package org.learningjava.bmtool1.domain.model;

import java.util.UUID;
public record PairId(String value) {
    public static PairId newId(){ return new PairId(UUID.randomUUID().toString()); }
}