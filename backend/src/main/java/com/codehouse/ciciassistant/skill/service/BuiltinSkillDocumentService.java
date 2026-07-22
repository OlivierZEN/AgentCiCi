package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.skill.service.FileBackedBuiltinSkillCatalog.FileBackedBuiltinSkillBundle;
import com.codehouse.ciciassistant.skill.service.FileBackedBuiltinSkillCatalog.FileBackedBuiltinSkillManifest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class BuiltinSkillDocumentService {

    private static final int MAX_MODULES_PER_TURN = 3;
    private static final int MAX_CHARS_PER_FILE = 7000;
    private static final Pattern API_INTENT = Pattern.compile(
            "接口|参数|API|api|请求体|返回|联调|实现|代码|创建|更新|删除|新增|调用",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DEVGUIDE_INTENT = Pattern.compile(
            "开发|规范|限制|最佳实践|触发器|类|代码|实现|调试",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final FileBackedBuiltinSkillCatalog catalog;

    public BuiltinSkillDocumentService(FileBackedBuiltinSkillCatalog catalog) {
        this.catalog = catalog;
    }

    public ResolvedBuiltinSkillDocs resolveDocs(SkillResolverService.ResolvedSkillContext context,
                                                String userMessage) {
        if (context == null || context.skills().isEmpty()) {
            return ResolvedBuiltinSkillDocs.empty();
        }
        List<DocSection> sections = new ArrayList<>();
        List<DocRef> refs = new ArrayList<>();
        List<SkillResolverService.ResolvedSkill> skills = context.skills();
        if (context.activeSkillCode() != null && !context.activeSkillCode().isBlank()) {
            skills = context.skills().stream()
                    .filter(skill -> skill.skillCode().equalsIgnoreCase(context.activeSkillCode().trim()))
                    .toList();
        }
        for (SkillResolverService.ResolvedSkill skill : skills) {
            Optional<FileBackedBuiltinSkillBundle> bundle = catalog.findBundle(skill.skillCode());
            if (bundle.isEmpty()) {
                continue;
            }
            ResolvedBuiltinSkillDocs docs = resolveDocs(bundle.get(), userMessage);
            sections.addAll(docs.sections());
            refs.addAll(docs.refs());
        }
        return new ResolvedBuiltinSkillDocs(List.copyOf(sections), List.copyOf(refs));
    }

    public ResolvedBuiltinSkillDocs resolveDocs(String skillCode, String userMessage) {
        return catalog.findBundle(skillCode)
                .map(bundle -> resolveDocs(bundle, userMessage))
                .orElseGet(ResolvedBuiltinSkillDocs::empty);
    }

    private ResolvedBuiltinSkillDocs resolveDocs(FileBackedBuiltinSkillBundle bundle, String userMessage) {
        String text = userMessage == null ? "" : userMessage;
        List<FileBackedBuiltinSkillManifest.Module> modules = resolveModules(bundle, text);
        if (modules.isEmpty()) {
            String summary = bundle.manifest().modules().stream()
                    .map(module -> module.code() + "(" + module.name() + ")")
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            return new ResolvedBuiltinSkillDocs(
                    List.of(new DocSection(
                            bundle.skillCode(),
                            bundle.manifest().version(),
                            "",
                            "module-summary",
                            "",
                            "Available modules: " + summary
                    )),
                    List.of()
            );
        }

        boolean needApi = API_INTENT.matcher(text).find();
        boolean needDevguide = DEVGUIDE_INTENT.matcher(text).find();
        List<DocSection> sections = new ArrayList<>();
        List<DocRef> refs = new ArrayList<>();
        for (FileBackedBuiltinSkillManifest.Module module : modules) {
            List<String> files = filesForTurn(module, needApi, needDevguide);
            for (String file : files) {
                Optional<String> content = catalog.readModuleDocument(bundle.skillCode(), module.code(), file);
                if (content.isEmpty()) {
                    continue;
                }
                FileBackedBuiltinSkillCatalog.FileBackedModuleFile moduleFile =
                        bundle.moduleFiles().get(module.code() + "/" + file);
                String checksum = moduleFile == null ? "" : moduleFile.checksum();
                sections.add(new DocSection(
                        bundle.skillCode(),
                        bundle.manifest().version(),
                        module.code(),
                        file,
                        checksum,
                        clip(content.get(), MAX_CHARS_PER_FILE)
                ));
                refs.add(new DocRef(
                        bundle.skillCode(),
                        bundle.manifest().version(),
                        module.code(),
                        file,
                        checksum
                ));
            }
        }
        return new ResolvedBuiltinSkillDocs(List.copyOf(sections), List.copyOf(refs));
    }

    private List<FileBackedBuiltinSkillManifest.Module> resolveModules(FileBackedBuiltinSkillBundle bundle, String userMessage) {
        String normalizedMessage = normalize(userMessage);
        LinkedHashSet<FileBackedBuiltinSkillManifest.Module> matched = new LinkedHashSet<>();
        for (FileBackedBuiltinSkillManifest.Module module : bundle.manifest().modules()) {
            if (matchesModule(module, normalizedMessage)) {
                matched.add(module);
            }
            if (matched.size() >= MAX_MODULES_PER_TURN) {
                break;
            }
        }
        return List.copyOf(matched);
    }

    private boolean matchesModule(FileBackedBuiltinSkillManifest.Module module, String normalizedMessage) {
        if (normalizedMessage.isBlank()) {
            return false;
        }
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(module.code());
        candidates.add(module.name());
        if (module.triggerHints() != null) {
            candidates.addAll(module.triggerHints());
        }
        return candidates.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(BuiltinSkillDocumentService::normalize)
                .anyMatch(normalizedMessage::contains);
    }

    private List<String> filesForTurn(FileBackedBuiltinSkillManifest.Module module, boolean needApi, boolean needDevguide) {
        List<String> files = new ArrayList<>();
        if (module.files().contains("introduction.md")) {
            files.add("introduction.md");
        }
        if (needDevguide && module.files().contains("devguide.md")) {
            files.add("devguide.md");
        }
        if (needApi && module.files().contains("api.md")) {
            files.add("api.md");
        }
        if (files.isEmpty() && module.files().contains("api.md")) {
            files.add("api.md");
        }
        return files;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String clip(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    public record ResolvedBuiltinSkillDocs(
            List<DocSection> sections,
            List<DocRef> refs
    ) {
        static ResolvedBuiltinSkillDocs empty() {
            return new ResolvedBuiltinSkillDocs(List.of(), List.of());
        }

        public boolean hasContent() {
            return sections != null && !sections.isEmpty();
        }
    }

    public record DocSection(
            String skillCode,
            Integer version,
            String moduleCode,
            String filename,
            String checksum,
            String content
    ) {
        String sourceLabel() {
            String path = moduleCode == null || moduleCode.isBlank()
                    ? filename
                    : moduleCode + "/" + filename;
            return skillCode + "/" + path + "@v" + version;
        }
    }

    public record DocRef(
            String skillCode,
            Integer version,
            String module,
            String file,
            String checksum
    ) {
    }
}
