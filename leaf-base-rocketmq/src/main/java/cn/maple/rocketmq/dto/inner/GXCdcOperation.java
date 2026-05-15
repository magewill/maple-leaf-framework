package cn.maple.rocketmq.dto.inner;

import cn.hutool.core.text.CharSequenceUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Debezium CDC operation codes.
 */
public enum GXCdcOperation {
    CREATE("c"),
    UPDATE("u"),
    DELETE("d"),
    READ("r");

    private final String code;

    GXCdcOperation(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static @Nullable GXCdcOperation fromCode(@Nullable String code) {
        if (CharSequenceUtil.isBlank(code)) {
            return null;
        }

        String normalizedCode = code.trim();
        return Arrays.stream(values())
                .filter(operation -> operation.code.equals(normalizedCode))
                .findFirst()
                .orElse(null);
    }
}
