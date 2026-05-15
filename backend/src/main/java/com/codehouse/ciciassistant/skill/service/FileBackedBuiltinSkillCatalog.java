package com.codehouse.ciciassistant.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class FileBackedBuiltinSkillCatalog {

    private static final Pattern SKILL_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern MANIFEST_PATH_PATTERN = Pattern.compile("/builtin-skills/([^/]+)/manifest\\.json$");
    private static final Set<String> ALLOWED_DOC_FILES = Set.of(
            "introduction.md",
            "devguide.md",
            "api.md",
            "examples.md"
    );
    private static final String ROOT = "builtin-skills";

    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Set<String> disabledCodes;
    private final AtomicReference<List<FileBackedBuiltinSkillBundle>> cache = new AtomicReference<>();

    public FileBackedBuiltinSkillCatalog(ResourcePatternResolver resourcePatternResolver,
                                         ObjectMapper objectMapper,
                                         @Value("${cici.skills.file-backed.enabled:true}") boolean enabled,
                                         @Value("${cici.skills.file-backed.disabled-codes:}") String disabledCodes) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.disabledCodes = Arrays.stream((disabledCodes == null ? "" : disabledCodes).split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<FileBackedBuiltinSkillBundle> listBundles() {
        if (!enabled) {
            return List.of();
        }
        List<FileBackedBuiltinSkillBundle> current = cache.get();
        if (current != null) {
            return current;
        }
        List<FileBackedBuiltinSkillBundle> loaded = scanBundles();
        cache.compareAndSet(null, loaded);
        return cache.get();
    }

    public Optional<FileBackedBuiltinSkillBundle> findBundle(String skillCode) {
        String normalized = normalizeKey(skillCode);
        return listBundles().stream()
                .filter(bundle -> normalizeKey(bundle.skillCode()).equals(normalized))
                .findFirst();
    }

    public String readEntrypoint(FileBackedBuiltinSkillBundle bundle) {
        return readText(bundle.skillCode(), bundle.manifest().entrypoint());
    }

    public Optional<String> readModuleDocument(String skillCode, String moduleCode, String filename) {
        if (!isSafeSegment(moduleCode) || !ALLOWED_DOC_FILES.contains(filename)) {
            return Optional.empty();
        }
        Optional<FileBackedBuiltinSkillBundle> bundle = findBundle(skillCode);
        if (bundle.isEmpty() || bundle.get().module(moduleCode).isEmpty()) {
            return Optional.empty();
        }
        FileBackedBuiltinSkillManifest.Module module = bundle.get().module(moduleCode).orElseThrow();
        if (!module.files().contains(filename)) {
            return Optional.empty();
        }
        return Optional.of(readText(bundle.get().skillCode(), module.code() + "/" + filename));
    }

    private List<FileBackedBuiltinSkillBundle> scanBundles() {
        try {
            Resource[] manifests = resourcePatternResolver.getResources("classpath*:" + ROOT + "/*/manifest.json");
            List<FileBackedBuiltinSkillBundle> bundles = new ArrayList<>();
            for (Resource manifestResource : manifests) {
                FileBackedBuiltinSkillManifest manifest = objectMapper.readValue(
                        manifestResource.getInputStream(),
                        FileBackedBuiltinSkillManifest.class
                );
                String directorySkillCode = inferSkillCodeFromResource(manifestResource);
                validateManifest(directorySkillCode, manifest, manifestResource);
                if (disabledCodes.contains(manifest.skillCode().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                bundles.add(buildBundle(manifest, manifestResource));
            }
            bundles.sort(Comparator.comparing(FileBackedBuiltinSkillBundle::skillCode));
            return List.copyOf(bundles);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan file-backed builtin skills", ex);
        }
    }

    private FileBackedBuiltinSkillBundle buildBundle(FileBackedBuiltinSkillManifest manifest, Resource manifestResource) {
        Map<String, Resource> resourcesByPath = new LinkedHashMap<>();
        resourcesByPath.put("manifest.json", manifestResource);
        String entrypoint = manifest.entrypoint();
        Resource entrypointResource = siblingResource(manifestResource, manifest.skillCode(), entrypoint);
        resourcesByPath.put(entrypoint, entrypointResource);
        byte[] entrypointBytes = readBytes(manifest.skillCode(), entrypoint, entrypointResource);
        Map<String, FileBackedModuleFile> moduleFiles = new LinkedHashMap<>();
        MessageDigest bundleDigest = sha256();
        updateDigest(bundleDigest, "manifest.json", readBytes(manifest.skillCode(), "manifest.json", manifestResource));
        updateDigest(bundleDigest, entrypoint, entrypointBytes);
        for (FileBackedBuiltinSkillManifest.Module module : manifest.modules()) {
            for (String file : module.files()) {
                String resourcePath = module.code() + "/" + file;
                Resource documentResource = siblingResource(manifestResource, manifest.skillCode(), resourcePath);
                resourcesByPath.put(resourcePath, documentResource);
                byte[] bytes = readBytes(manifest.skillCode(), resourcePath, documentResource);
                String checksum = sha256Hex(bytes);
                moduleFiles.put(module.code() + "/" + file, new FileBackedModuleFile(module.code(), file, checksum));
                updateDigest(bundleDigest, resourcePath, bytes);
            }
        }
        return new FileBackedBuiltinSkillBundle(
                manifest.skillCode(),
                manifest,
                "classpath:/" + ROOT + "/" + manifest.skillCode(),
                sha256Hex(entrypointBytes),
                HexFormat.of().formatHex(bundleDigest.digest()),
                Map.copyOf(moduleFiles),
                Map.copyOf(resourcesByPath)
        );
    }

    private void validateManifest(String directorySkillCode, FileBackedBuiltinSkillManifest manifest, Resource manifestResource) {
        if (manifest.schemaVersion() != 1) {
            throw new IllegalStateException("Unsupported builtin skill manifest schemaVersion: " + manifest.schemaVersion());
        }
        if (!isSafeSegment(manifest.skillCode())) {
            throw new IllegalStateException("Invalid builtin skillCode: " + manifest.skillCode());
        }
        if (!manifest.skillCode().equals(directorySkillCode)) {
            throw new IllegalStateException("Builtin skill directory must equal skillCode: " + directorySkillCode);
        }
        if (!"PLATFORM_STANDARD".equalsIgnoreCase(manifest.sourceType())) {
            throw new IllegalStateException("File-backed builtin skill sourceType must be PLATFORM_STANDARD: " + manifest.skillCode());
        }
        if (!"SKILL.md".equals(manifest.entrypoint())) {
            throw new IllegalStateException("File-backed builtin skill entrypoint must be SKILL.md: " + manifest.skillCode());
        }
        readBytes(manifest.skillCode(), manifest.entrypoint(), siblingResource(manifestResource, manifest.skillCode(), manifest.entrypoint()));
        for (FileBackedBuiltinSkillManifest.Module module : manifest.modules()) {
            if (!isSafeSegment(module.code())) {
                throw new IllegalStateException("Invalid builtin skill module code: " + module.code());
            }
            if (module.files() == null || module.files().isEmpty()) {
                throw new IllegalStateException("Builtin skill module has no files: " + module.code());
            }
            for (String file : module.files()) {
                if (!ALLOWED_DOC_FILES.contains(file)) {
                    throw new IllegalStateException("Unsupported builtin skill document file: " + module.code() + "/" + file);
                }
                String resourcePath = module.code() + "/" + file;
                readBytes(manifest.skillCode(), resourcePath, siblingResource(manifestResource, manifest.skillCode(), resourcePath));
            }
        }
    }

    private String inferSkillCodeFromResource(Resource resource) throws IOException {
        Matcher matcher = MANIFEST_PATH_PATTERN.matcher(resource.getURL().toString());
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot infer builtin skill directory from resource: " + resource);
        }
        return matcher.group(1);
    }

    private Resource siblingResource(Resource manifestResource, String skillCode, String relativePath) {
        if (!isSafeSegment(skillCode) || relativePath.contains("..") || relativePath.startsWith("/")) {
            throw new IllegalArgumentException("Unsafe builtin skill resource path");
        }
        try {
            return manifestResource.createRelative(relativePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to resolve builtin skill resource: " + skillCode + "/" + relativePath, ex);
        }
    }

    private byte[] readBytes(String skillCode, String relativePath) {
        if (!isSafeSegment(skillCode) || relativePath.contains("..") || relativePath.startsWith("/")) {
            throw new IllegalArgumentException("Unsafe builtin skill resource path");
        }
        Optional<FileBackedBuiltinSkillBundle> bundle = findBundle(skillCode);
        if (bundle.isPresent()) {
            Resource resource = bundle.get().resourcesByPath().get(relativePath);
            if (resource != null) {
                return readBytes(skillCode, relativePath, resource);
            }
        }
        Resource resource = resourcePatternResolver.getResource("classpath:" + ROOT + "/" + skillCode + "/" + relativePath);
        return readBytes(skillCode, relativePath, resource);
    }

    private byte[] readBytes(String skillCode, String relativePath, Resource resource) {
        try {
            if (!resource.exists()) {
                throw new IllegalStateException("Missing builtin skill resource: " + skillCode + "/" + relativePath
                        + " (source=" + resource.getDescription() + ")");
            }
            return resource.getInputStream().readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read builtin skill resource: " + skillCode + "/" + relativePath
                    + " (source=" + resource.getDescription() + ")", ex);
        }
    }

    private String readText(String skillCode, String relativePath) {
        return new String(readBytes(skillCode, relativePath), StandardCharsets.UTF_8);
    }

    private static boolean isSafeSegment(String value) {
        return value != null && SKILL_CODE_PATTERN.matcher(value).matches();
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static void updateDigest(MessageDigest digest, String path, byte[] bytes) {
        digest.update(path.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(bytes);
        digest.update((byte) 0);
    }

    public record FileBackedBuiltinSkillManifest(
            int schemaVersion,
            String skillCode,
            String name,
            String description,
            String category,
            String sourceType,
            String visibility,
            String editPolicy,
            String bindingPolicy,
            String updatePolicy,
            String riskLevel,
            Integer version,
            String documentRoot,
            String entrypoint,
            String defaultActivationMode,
            List<Module> modules
    ) {
        public record Module(
                String code,
                String name,
                List<String> files,
                List<String> triggerHints
        ) {
        }
    }

    public record FileBackedBuiltinSkillBundle(
            String skillCode,
            FileBackedBuiltinSkillManifest manifest,
            String resourceUri,
            String entrypointChecksum,
            String bundleChecksum,
            Map<String, FileBackedModuleFile> moduleFiles,
            Map<String, Resource> resourcesByPath
    ) {
        public Optional<FileBackedBuiltinSkillManifest.Module> module(String moduleCode) {
            String normalized = normalizeKey(moduleCode);
            return manifest.modules().stream()
                    .filter(module -> normalizeKey(module.code()).equals(normalized))
                    .findFirst();
        }
    }

    public record FileBackedModuleFile(
            String moduleCode,
            String filename,
            String checksum
    ) {
    }
}
