package com.mobilityos.common.util;

public final class EnumUtils {

    private EnumUtils() {
    }

    public static <E extends Enum<E>> E fromName(
            Class<E> enumClass,
            String value
    ) {

        if (value == null) {
            return null;
        }

        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value.trim())) {
                return constant;
            }
        }

        return null;
    }
}
