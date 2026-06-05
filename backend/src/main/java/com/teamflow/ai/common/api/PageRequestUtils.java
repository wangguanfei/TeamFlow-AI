package com.teamflow.ai.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public final class PageRequestUtils {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_SIZE = 20L;
    public static final long MAX_SIZE = 100L;

    private PageRequestUtils() {
    }

    public static <T> Page<T> of(long page, long size) {
        return Page.of(normalizePage(page), normalizeSize(size));
    }

    public static long normalizePage(long page) {
        return page < DEFAULT_PAGE ? DEFAULT_PAGE : page;
    }

    public static long normalizeSize(long size) {
        if (size < 1L) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
