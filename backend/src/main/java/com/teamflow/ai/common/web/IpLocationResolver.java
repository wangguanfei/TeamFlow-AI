package com.teamflow.ai.common.web;

import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class IpLocationResolver {

    private static final Logger log = LoggerFactory.getLogger(IpLocationResolver.class);

    private final byte[] xdbBytes;

    public IpLocationResolver() {
        byte[] bytes = null;
        try {
            ClassPathResource resource = new ClassPathResource("ip2region.xdb");
            bytes = resource.getInputStream().readAllBytes();
        } catch (Exception e) {
            log.warn("ip2region.xdb 未找到，IP 地点解析已禁用: {}", e.getMessage());
        }
        this.xdbBytes = bytes;
    }

    public String resolve(String ip) {
        if (ip == null || xdbBytes == null) return null;
        if (isInternalIp(ip)) return "内网";
        try {
            Searcher searcher = Searcher.newWithBuffer(xdbBytes);
            String region = searcher.search(ip);
            searcher.close();
            return formatRegion(region);
        } catch (Exception e) {
            log.debug("IP 地点解析失败: ip={}, err={}", ip, e.getMessage());
            return null;
        }
    }

    private boolean isInternalIp(String ip) {
        if (ip.startsWith("127.") || ip.startsWith("10.")
                || ip.startsWith("192.168.") || ip.equals("::1")
                || ip.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        // 172.16.0.0/12（含 Docker 桥接网段 172.17-31.x.x）
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private String formatRegion(String region) {
        if (region == null || region.isBlank()) return null;
        // ip2region xdb v4 格式: 国家|省份|城市|ISP|国家代码
        String[] parts = region.split("\\|");
        if (parts.length < 3) return region;
        String province = "0".equals(parts[1]) ? "" : parts[1];
        String city = "0".equals(parts[2]) ? "" : parts[2];
        // ip2region 对保留/特殊段返回 "Reserved"，视为无法解析
        if ("Reserved".equalsIgnoreCase(province)) province = "";
        if ("Reserved".equalsIgnoreCase(city)) city = "";
        // 直辖市省份和城市重复时（如"天津"和"天津市"）只保留城市
        if (!city.isEmpty() && (city.equals(province) || city.startsWith(province))) {
            province = "";
        }
        String result = (province + city).trim();
        return result.isEmpty() ? null : result;
    }
}
