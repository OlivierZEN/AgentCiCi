package com.codehouse.ciciassistant.memory.service;

import com.codehouse.ciciassistant.memory.domain.UserMemoryEntity;
import com.codehouse.ciciassistant.memory.domain.UserMemoryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserMemoryService {

    private static final int MAX_INJECTION_LIMIT = 30;
    private static final Set<String> VALID_CATEGORIES = Set.of("FACT", "PREFERENCE", "CONTEXT", "INSTRUCTION");

    private final UserMemoryRepository repository;

    public UserMemoryService(UserMemoryRepository repository) {
        this.repository = repository;
    }

    /** 获取用户全部记忆（含禁用），用于 UI 管理页 */
    @Transactional(readOnly = true)
    public List<UserMemoryEntity> listAll(String companyId, String userId, String agentId) {
        return repository.findByCompanyIdAndUserIdAndAgentIdOrderByPinnedDescUpdatedAtDesc(
                companyId, userId, agentId);
    }

    /** 获取 enabled 记忆，用于 system prompt 注入，最多 MAX_INJECTION_LIMIT 条 */
    @Transactional(readOnly = true)
    public List<UserMemoryEntity> listForInjection(String companyId, String userId, String agentId) {
        List<UserMemoryEntity> all = repository
                .findByCompanyIdAndUserIdAndAgentIdAndEnabledTrueOrderByPinnedDescUpdatedAtDesc(
                        companyId, userId, agentId);
        if (all.size() <= MAX_INJECTION_LIMIT) {
            return all;
        }
        return all.subList(0, MAX_INJECTION_LIMIT);
    }

    /** 手动新增记忆 */
    @Transactional
    public UserMemoryEntity create(String companyId, String userId, String agentId,
                                   String category, String content, String memoryKey) {
        validateCategory(category);
        if (memoryKey != null && !memoryKey.isBlank()) {
            // 若已有相同 key 的记忆则覆盖内容
            var existing = repository.findByCompanyIdAndUserIdAndAgentIdAndMemoryKey(
                    companyId, userId, agentId, memoryKey.trim());
            if (existing.isPresent()) {
                UserMemoryEntity e = existing.get();
                e.updateContent(content.trim(), category, e.isEnabled(), e.isPinned());
                return repository.save(e);
            }
        }
        var entity = new UserMemoryEntity(companyId, userId, agentId, category, "MANUAL",
                content.trim(), memoryKey != null ? memoryKey.trim() : null, BigDecimal.ONE);
        return repository.save(entity);
    }

    /** AI 通过工具写入记忆（source=EXTRACTED）*/
    @Transactional
    public UserMemoryEntity upsertExtracted(String companyId, String userId, String agentId,
                                             String category, String content, String memoryKey,
                                             BigDecimal confidence) {
        validateCategory(category);
        if (memoryKey != null && !memoryKey.isBlank()) {
            var existing = repository.findByCompanyIdAndUserIdAndAgentIdAndMemoryKey(
                    companyId, userId, agentId, memoryKey.trim());
            if (existing.isPresent()) {
                UserMemoryEntity e = existing.get();
                e.updateContent(content.trim(), category, e.isEnabled(), e.isPinned());
                return repository.save(e);
            }
        }
        var entity = new UserMemoryEntity(companyId, userId, agentId, category, "EXTRACTED",
                content.trim(), memoryKey != null ? memoryKey.trim() : null,
                confidence != null ? confidence : BigDecimal.ONE);
        return repository.save(entity);
    }

    /** 更新记忆内容 */
    @Transactional
    public UserMemoryEntity update(String companyId, String userId, Long id,
                                    String category, String content,
                                    boolean enabled, boolean pinned) {
        validateCategory(category);
        UserMemoryEntity entity = requireOwned(companyId, userId, id);
        entity.updateContent(content.trim(), category, enabled, pinned);
        return repository.save(entity);
    }

    /** 删除单条记忆 */
    @Transactional
    public void delete(String companyId, String userId, Long id) {
        UserMemoryEntity entity = requireOwned(companyId, userId, id);
        repository.delete(entity);
    }

    /** 清空指定 agent 的全部记忆 */
    @Transactional
    public void deleteAll(String companyId, String userId, String agentId) {
        repository.deleteAllByCompanyIdAndUserIdAndAgentId(companyId, userId, agentId);
    }

    /** 将记忆列表格式化为注入 system prompt 的文本块 */
    public String buildPromptBlock(List<UserMemoryEntity> memories) {
        if (memories.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        sb.append("## 关于当前用户的专属记忆\n\n");
        sb.append("以下是用户告知你或你在过往对话中主动记录的信息，请在回答时充分参考：\n\n");

        appendCategory(sb, memories, "INSTRUCTION", "【行为指令】");
        appendCategory(sb, memories, "FACT", "【用户事实】");
        appendCategory(sb, memories, "PREFERENCE", "【个人偏好】");
        appendCategory(sb, memories, "CONTEXT", "【工作上下文】");

        return sb.toString();
    }

    private void appendCategory(StringBuilder sb, List<UserMemoryEntity> memories,
                                  String category, String label) {
        List<UserMemoryEntity> filtered = memories.stream()
                .filter(m -> category.equals(m.getCategory()))
                .toList();
        if (filtered.isEmpty()) return;
        sb.append(label).append("\n");
        for (UserMemoryEntity m : filtered) {
            sb.append("- ").append(m.getContent()).append("\n");
        }
        sb.append("\n");
    }

    private UserMemoryEntity requireOwned(String companyId, String userId, Long id) {
        return repository.findByIdAndCompanyIdAndUserId(id, companyId, userId)
                .orElseThrow(() -> new IllegalArgumentException("记忆不存在或无权访问: " + id));
    }

    private void validateCategory(String category) {
        if (!VALID_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("无效的记忆类别: " + category);
        }
    }
}
