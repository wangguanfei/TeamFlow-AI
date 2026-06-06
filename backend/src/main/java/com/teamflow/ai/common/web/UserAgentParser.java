package com.teamflow.ai.common.web;

public final class UserAgentParser {

    private UserAgentParser() {}

    public static String parseBrowser(String ua) {
        if (ua == null || ua.isBlank()) return null;
        if (ua.contains("Edg/") || ua.contains("Edge/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera/")) return "Opera";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/") && ua.contains("Version/")) return "Safari";
        if (ua.contains("MSIE") || ua.contains("Trident/")) return "IE";
        return "Other";
    }

    public static String parseOs(String ua) {
        if (ua == null || ua.isBlank()) return null;
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Windows NT")) return "Windows";
        if (ua.contains("Mac OS X") || ua.contains("macOS")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        return "Other";
    }
}
