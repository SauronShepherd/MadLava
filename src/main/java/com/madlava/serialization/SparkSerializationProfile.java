package com.madlava.serialization;

import java.util.Locale;

public enum SparkSerializationProfile {
    BOUNDARY,
    STREAM,
    ALL;

    public static SparkSerializationProfile parse(String value) {
        if (value == null) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Unknown Spark serialization profile: " + value, failure);
        }
    }

    public boolean accepts(SparkSerializationLayer layer) {
        return this == ALL
                || (this == BOUNDARY && layer == SparkSerializationLayer.BOUNDARY)
                || (this == STREAM && layer == SparkSerializationLayer.STREAM);
    }
}
