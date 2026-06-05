package com.teamflow.ai.common.api;

import com.teamflow.ai.common.web.TraceIdContext;

public class ApiResult<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResult<T> success(T data) {
        return of(0, "success", data);
    }

    public static <T> ApiResult<T> success() {
        return of(0, "success", null);
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return of(code, message, null);
    }

    public static <T> ApiResult<T> of(int code, String message, T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        result.setTraceId(TraceIdContext.getTraceId());
        return result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
