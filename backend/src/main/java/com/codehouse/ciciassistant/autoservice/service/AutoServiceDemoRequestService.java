package com.codehouse.ciciassistant.autoservice.service;

import com.codehouse.ciciassistant.autoservice.domain.AutoServiceDemoRequestEntity;
import com.codehouse.ciciassistant.autoservice.domain.AutoServiceDemoRequestRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutoServiceDemoRequestService {

    private static final int MAX_LIMIT = 200;

    private final AutoServiceDemoRequestRepository repository;

    public AutoServiceDemoRequestService(AutoServiceDemoRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Map<String, Object> create(CreateCommand command) {
        String site = normalizeSite(command.site());
        String companyName = required(command.companyName(), "请输入公司名称");
        String contactName = required(command.contactName(), "请输入联系人");
        String mobile = required(command.mobile(), "请输入联系电话");
        if (!mobile.matches("^\\+?[0-9][0-9\\s-]{5,31}$")) {
            throw new IllegalArgumentException("联系电话格式不正确");
        }
        AutoServiceDemoRequestEntity saved = repository.save(new AutoServiceDemoRequestEntity(
                site,
                normalizeLocale(command.locale(), site),
                companyName,
                contactName,
                mobile,
                trimOrNull(command.email()),
                trimOrNull(command.roleTitle()),
                trimOrNull(command.scenario()),
                trimLength(command.sourcePath(), 256)
        ));
        return Map.of("id", saved.getId(), "status", saved.getStatus());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String status, String q, int limit) {
        String normalizedStatus = normalizeNullableStatus(status);
        String keyword = trimOrNull(q);
        if (keyword == null) {
            keyword = "";
        }
        int size = Math.max(1, Math.min(MAX_LIMIT, limit));
        List<Map<String, Object>> items = repository.search(normalizedStatus, keyword, PageRequest.of(0, size)).stream()
                .map(this::toRow)
                .toList();
        return Map.of("items", items);
    }

    @Transactional
    public Map<String, Object> updateStatus(Long id, String status, String handledBy, String handledNote) {
        AutoServiceDemoRequestEntity request = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("预约记录不存在"));
        request.updateStatus(normalizeStatus(status), trimOrNull(handledBy), trimOrNull(handledNote));
        repository.save(request);
        return toRow(request);
    }

    private Map<String, Object> toRow(AutoServiceDemoRequestEntity item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("site", item.getSite());
        row.put("locale", item.getLocale());
        row.put("companyName", item.getCompanyName());
        row.put("contactName", item.getContactName());
        row.put("mobile", item.getMobile());
        row.put("email", item.getEmail() == null ? "" : item.getEmail());
        row.put("roleTitle", item.getRoleTitle() == null ? "" : item.getRoleTitle());
        row.put("scenario", item.getScenario() == null ? "" : item.getScenario());
        row.put("sourcePath", item.getSourcePath() == null ? "" : item.getSourcePath());
        row.put("status", item.getStatus());
        row.put("handledBy", item.getHandledBy() == null ? "" : item.getHandledBy());
        row.put("handledNote", item.getHandledNote() == null ? "" : item.getHandledNote());
        row.put("handledAt", item.getHandledAt() == null ? "" : item.getHandledAt().toString());
        row.put("createdAt", item.getCreatedAt().toString());
        row.put("updatedAt", item.getUpdatedAt().toString());
        return row;
    }

    private String normalizeSite(String value) {
        String site = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("global".equals(site) || "china".equals(site)) {
            return site;
        }
        return "china";
    }

    private String normalizeLocale(String value, String site) {
        String locale = trimLength(value, 16);
        if (locale != null) {
            return locale;
        }
        return "global".equals(site) ? "en" : "zh-CN";
    }

    private String normalizeNullableStatus(String value) {
        String status = trimOrNull(value);
        if (status == null || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        return normalizeStatus(status);
    }

    private String normalizeStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (AutoServiceDemoRequestEntity.STATUS_NEW.equals(status)
                || AutoServiceDemoRequestEntity.STATUS_CONTACTED.equals(status)
                || AutoServiceDemoRequestEntity.STATUS_CLOSED.equals(status)) {
            return status;
        }
        throw new IllegalArgumentException("Invalid status");
    }

    private String required(String value, String message) {
        String trimmed = trimOrNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimLength(String value, int maxLength) {
        String trimmed = trimOrNull(value);
        if (trimmed == null) return null;
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public record CreateCommand(
            String site,
            String locale,
            String companyName,
            String contactName,
            String mobile,
            String email,
            String roleTitle,
            String scenario,
            String sourcePath
    ) {
    }
}
