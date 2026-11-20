package com.smartops.application.query;

import java.util.List;
import java.util.function.Function;

/** Página de resultados independiente de Spring Data. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(content.stream().map(mapper).toList(), page, size, totalElements);
    }

    public record PageRequest(int page, int size) {
        public static final int MAX_SIZE = 200;

        public PageRequest {
            page = Math.max(page, 0);
            size = size <= 0 ? 20 : Math.min(size, MAX_SIZE);
        }

        public long offset() {
            return (long) page * size;
        }
    }
}
