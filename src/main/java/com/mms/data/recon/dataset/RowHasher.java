package com.mms.data.recon.dataset;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.List;

public final class RowHasher {

    private RowHasher() {}

    public static String hash(List<Object> values, HashingStrategy strategy) {
        Hasher hasher = Hashing.sha256().newHasher();

        for (Object value : values) {
            String normalized = normalize(value, strategy);
            hasher.putString(normalized, StandardCharsets.UTF_8);
            hasher.putByte((byte) 0);
        }

        return hasher.hash().toString();
    }

    static String normalize(Object value, HashingStrategy strategy) {
        if (value == null) {
            return "<NULL>";
        }

        if (strategy == HashingStrategy.TypeLenient) {
            if (value instanceof Number number) {
                return new BigDecimal(number.toString())
                        .stripTrailingZeros()
                        .toPlainString();
            }
            if (value instanceof Boolean b) {
                return b ? "1" : "0";
            }
            if (value instanceof java.util.Date date) {
                return date.toInstant().toString();
            }
            if (value instanceof java.time.OffsetDateTime offsetDateTime) {
                return offsetDateTime.toInstant().toString();
            }
            if (value instanceof java.time.ZonedDateTime zonedDateTime) {
                return zonedDateTime.toInstant().toString();
            }
            if (value instanceof TemporalAccessor) {
                return value.toString();
            }
            return String.valueOf(value);
        }

        return value.getClass().getName() + ":" + value;
    }
}
