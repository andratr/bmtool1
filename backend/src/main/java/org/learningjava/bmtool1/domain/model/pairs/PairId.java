package org.learningjava.bmtool1.domain.model.pairs;

import java.util.UUID;

public record PairId(String value) {
    public static PairId newId() {
        return new PairId(UUID.randomUUID().toString());
    }
}