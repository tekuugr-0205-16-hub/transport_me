package com.mobilityos.common.pagination;

public record PageRequest(
        int page,
        int size,
        String sortBy,
        SortDirection direction
) {

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("Page cannot be negative");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }

        if (direction == null) {
            direction = SortDirection.ASC;
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(
                page,
                size,
                null,
                SortDirection.ASC
        );
    }
}