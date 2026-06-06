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
        return ip.startsWith("127.") || ip.startsWith("10.")
                || ip.startsWith("192.168.") || ip.equals("::1")
                || ip.equals("0:0:0:0:0:0:0:1");
    }

    private String formatRegion(String region) {
        if (region == null || region.isBlank()) return null;
        // ip2region 格式: 国家|区域|省份|城市|ISP
        String[] parts = region.split("\\|");
        if (parts.length < 4) return region;
        String province = "0".equals(parts[2]) ? "" : parts[2];
        String city = "0".equals(parts[3]) ? "" : parts[3];
        String result = (province + city).trim();
        return result.isEmpty() ? null : result;
    }
}
