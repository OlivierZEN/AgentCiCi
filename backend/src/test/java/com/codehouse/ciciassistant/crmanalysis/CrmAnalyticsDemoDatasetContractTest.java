package com.codehouse.ciciassistant.crmanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrmAnalyticsDemoDatasetContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BATCH = "TASK-205-CRM-ANALYTICS-DEMO-V1";
    private static final String TARGET_OWNER = "00520264AE58B11bw6gE";
    private static final String SOURCE_OWNER = "0052017BE8702F1PIi4j";
    private static final List<String> PRODUCT_CODES = List.of(
            "DEMO-X1", "DEMO-G5", "DEMO-S2", "DEMO-MP", "DEMO-PA", "DEMO-FS",
            "DEMO-VI", "DEMO-DH", "DEMO-IM", "DEMO-TR", "DEMO-API", "DEMO-BK");

    @Test
    void dryRunBuildsRichDatasetWithStableQuantityAndDifferentAmountRanking() throws Exception {
        Path script = Path.of("..", "scripts", "seed-crm-analytics-demo.py").toAbsolutePath().normalize();
        Process process = new ProcessBuilder(
                "python3",
                script.toString(),
                "--dry-run",
                "--offline",
                "--as-of",
                "2026-07-14",
                "--json"
        ).redirectErrorStream(true).start();
        assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage(output).isZero();

        JsonNode plan = new ObjectMapper().readTree(output);
        assertThat(plan.path("batch").asText()).isEqualTo("TASK-205-CRM-ANALYTICS-DEMO-V1");
        assertThat(plan.path("counts").path("products").asInt()).isEqualTo(12);
        assertThat(plan.path("counts").path("accounts").asInt()).isEqualTo(16);
        assertThat(plan.path("counts").path("orders").asInt()).isEqualTo(48);
        assertThat(plan.path("counts").path("orderItems").asInt()).isGreaterThanOrEqualTo(120);
        assertThat(plan.path("counts").path("contracts").asInt()).isGreaterThanOrEqualTo(16);
        assertThat(plan.path("counts").path("opportunities").asInt()).isGreaterThanOrEqualTo(24);
        assertThat(plan.path("counts").path("opportunityProducts").asInt()).isGreaterThanOrEqualTo(60);
        assertThat(plan.path("invalidCurrentOrders").asInt()).isGreaterThanOrEqualTo(3);

        assertThat(plan.path("expectedRankings").path("last30DaysQuantity"))
                .extracting(JsonNode::asText)
                .startsWith("DEMO-X1", "DEMO-G5", "DEMO-S2");
        List<String> amountTop3 = new java.util.ArrayList<>();
        plan.path("expectedRankings").path("last30DaysAmount").forEach(node -> amountTop3.add(node.asText()));
        assertThat(amountTop3.subList(0, 3)).isNotEqualTo(List.of("DEMO-X1", "DEMO-G5", "DEMO-S2"));
        assertThat(plan.path("qualityChecks").path("invalidHighQuantityExcluded").asBoolean()).isTrue();
        assertThat(plan.path("qualityChecks").path("allOrderItemsResolveProduct").asBoolean()).isTrue();
        assertThat(plan.path("qualityChecks").path("allOrderItemsResolveOrder").asBoolean()).isTrue();

        JsonNode migration = plan.path("migrationPlan");
        assertThat(migration.path("sourceBatch").asText()).isEqualTo("TASK-205-CRM-ANALYTICS-DEMO-V1");
        assertThat(migration.path("targetOwner").path("id").asText())
                .isEqualTo("00520264AE58B11bw6gE");
        assertThat(migration.path("targetOwner").path("name").asText()).isEqualTo("SalesA");
        assertThat(migration.path("targetAccounts").path("marker").asText()).isEqualTo("TASK-203-DEMO-V2");
        assertThat(migration.path("targetAccounts").path("requiredCount").asInt()).isEqualTo(16);
        assertThat(migration.path("targetAccounts").path("stableSort").asText()).isEqualTo("customerName");
        assertThat(migration.path("expectedOwnerUpdates").asInt()).isEqualTo(316);
        assertThat(migration.path("expectedAccountRelinks").asInt()).isEqualTo(88);
        assertThat(migration.path("writePolicy").path("allowedActions"))
                .extracting(JsonNode::asText)
                .containsExactly("update");
        assertThat(migration.path("writePolicy").path("creates").asInt()).isZero();
        assertThat(migration.path("writePolicy").path("accountWrites").asInt()).isZero();
        assertThat(migration.path("rollbackManifest").path("requiredFields"))
                .extracting(JsonNode::asText)
                .containsExactly("objectApiName", "id", "oldOwnerId", "oldAccountId", "targetOwnerId", "targetAccountId");
    }

    @Test
    void snapshotDryRunPlansOnlyExistingBatchUpdatesAndIsIdempotent(@TempDir Path tempDir) throws Exception {
        ObjectNode snapshot = buildSnapshot();
        Path before = tempDir.resolve("before.json");
        Files.writeString(before, JSON.writeValueAsString(snapshot), StandardCharsets.UTF_8);

        ScriptResult first = runScript("--dry-run", "--snapshot", before.toString(), "--as-of", "2026-07-14", "--json");
        assertThat(first.exitCode()).withFailMessage(first.output()).isZero();
        JsonNode firstPlan = JSON.readTree(first.output()).path("migrationPlan");
        assertThat(firstPlan.path("status").asText()).isEqualTo("READY");
        assertThat(firstPlan.path("accountMapping")).hasSize(16);
        assertThat(firstPlan.path("accountMapping").path(0).path("targetAccountName").asText())
                .isEqualTo("V2-客户-01");
        assertThat(firstPlan.path("updatePlan").path("summary").path("plannedRecordUpdates").asInt())
                .isEqualTo(316);
        assertThat(firstPlan.path("updatePlan").path("summary").path("ownerChanges").asInt())
                .isEqualTo(316);
        assertThat(firstPlan.path("updatePlan").path("summary").path("accountChanges").asInt())
                .isEqualTo(88);
        assertThat(firstPlan.path("updatePlan").path("summary").path("fieldChanges").asInt())
                .isEqualTo(404);
        assertThat(firstPlan.path("updatePlan").path("summary").path("creates").asInt()).isZero();
        assertThat(firstPlan.path("rollbackManifest").path("records")).hasSize(316);

        applyPlanToSnapshot(snapshot, firstPlan.path("updatePlan").path("records"));
        Path after = tempDir.resolve("after.json");
        Files.writeString(after, JSON.writeValueAsString(snapshot), StandardCharsets.UTF_8);
        ScriptResult second = runScript("--dry-run", "--snapshot", after.toString(), "--as-of", "2026-07-14", "--json");
        assertThat(second.exitCode()).withFailMessage(second.output()).isZero();
        JsonNode secondSummary = JSON.readTree(second.output())
                .path("migrationPlan").path("updatePlan").path("summary");
        assertThat(secondSummary.path("plannedRecordUpdates").asInt()).isZero();
        assertThat(secondSummary.path("ownerChanges").asInt()).isZero();
        assertThat(secondSummary.path("accountChanges").asInt()).isZero();
        assertThat(secondSummary.path("creates").asInt()).isZero();
    }

    @Test
    void snapshotDryRunFailsClosedWhenBatchRecordIdIsMissing(@TempDir Path tempDir) throws Exception {
        ObjectNode snapshot = buildSnapshot();
        ((ObjectNode) snapshot.path("products").path(0)).remove("id");
        Path broken = tempDir.resolve("missing-id.json");
        Files.writeString(broken, JSON.writeValueAsString(snapshot), StandardCharsets.UTF_8);

        ScriptResult result = runScript("--dry-run", "--snapshot", broken.toString(), "--json");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("missing id").doesNotContain("Traceback");
    }

    @Test
    void fakeCloudccPersistsCompleteBackupBeforePartialFailureAndRollbackIsRetrySafe(@TempDir Path tempDir)
            throws Exception {
        FakeCloudcc fake = createFakeCloudcc(tempDir.resolve("fake"), buildSnapshot(), List.of("success", "exit_failure"));
        Path originalBackup = tempDir.resolve("before-execute.json");

        ScriptResult execute = runScript(
                fakeEnvironment(fake, originalBackup),
                "--execute",
                "--backup-file", originalBackup.toString(),
                "--cloudcc-cli", fake.executable().toString(),
                "--cloudcc-project", tempDir.toString(),
                "--as-of", "2026-07-14",
                "--json");

        assertThat(execute.exitCode()).isNotZero();
        assertThat(execute.output()).contains("CloudCC update Opportunity failed").doesNotContain("Traceback");
        assertSecureCompleteBackup(originalBackup);
        ObjectNode partialState = readFakeState(fake);
        assertThat(partialState.path("_control").path("backupVerified").asBoolean()).isTrue();
        assertThat(partialState.path("_control").path("updateCalls").asInt()).isEqualTo(2);
        assertThat(partialState.path("products")).allSatisfy(row ->
                assertThat(row.path("ownerid").asText()).isEqualTo(TARGET_OWNER));
        assertThat(partialState.path("opportunities")).allSatisfy(row ->
                assertThat(row.path("ownerid").asText()).isEqualTo(SOURCE_OWNER));

        configureFake(fake, List.of("exit_failure"));
        Path failedRollbackBackup = tempDir.resolve("before-failed-rollback.json");
        ScriptResult failedRollback = runScript(
                fakeEnvironment(fake, failedRollbackBackup),
                "--execute",
                "--rollback-from", originalBackup.toString(),
                "--backup-file", failedRollbackBackup.toString(),
                "--cloudcc-cli", fake.executable().toString(),
                "--cloudcc-project", tempDir.toString(),
                "--as-of", "2026-07-14",
                "--json");
        assertThat(failedRollback.exitCode()).isNotZero();
        assertSecureCompleteBackup(failedRollbackBackup);
        assertThat(readFakeState(fake).path("products")).allSatisfy(row ->
                assertThat(row.path("ownerid").asText()).isEqualTo(TARGET_OWNER));

        configureFake(fake, List.of("success"));
        Path retryBackup = tempDir.resolve("before-rollback-retry.json");
        ScriptResult retry = runScript(
                fakeEnvironment(fake, retryBackup),
                "--execute",
                "--rollback-from", originalBackup.toString(),
                "--backup-file", retryBackup.toString(),
                "--cloudcc-cli", fake.executable().toString(),
                "--cloudcc-project", tempDir.toString(),
                "--as-of", "2026-07-14",
                "--json");
        assertThat(retry.exitCode()).withFailMessage(retry.output()).isZero();
        JsonNode retryReport = JSON.readTree(retry.output());
        assertThat(retryReport.path("rollbackPlan").path("summary").path("plannedRecordUpdates").asInt())
                .isEqualTo(12);
        assertThat(readFakeState(fake).path("products")).allSatisfy(row ->
                assertThat(row.path("ownerid").asText()).isEqualTo(SOURCE_OWNER));

        configureFake(fake, List.of("success"));
        Path repeatBackup = tempDir.resolve("before-repeat-rollback.json");
        ScriptResult repeat = runScript(
                fakeEnvironment(fake, repeatBackup),
                "--execute",
                "--rollback-from", originalBackup.toString(),
                "--backup-file", repeatBackup.toString(),
                "--cloudcc-cli", fake.executable().toString(),
                "--cloudcc-project", tempDir.toString(),
                "--as-of", "2026-07-14",
                "--json");
        assertThat(repeat.exitCode()).withFailMessage(repeat.output()).isZero();
        assertThat(JSON.readTree(repeat.output())
                .path("rollbackPlan").path("summary").path("plannedRecordUpdates").asInt()).isZero();
        assertThat(updateActionCount(readFakeState(fake))).isZero();
    }

    @Test
    void executeFailsClosedWhenOwnerDriftsBeforeFirstForwardBatch(@TempDir Path tempDir) throws Exception {
        FakeCloudcc fake = createFakeCloudcc(tempDir.resolve("fake"), buildSnapshot(), List.of("success"));
        configureLiveDrift(fake, "product", "PRODUCT-1", "ownerid", "EXTERNAL-OWNER", 2);
        Path backup = tempDir.resolve("before-execute.json");

        ScriptResult result = runScript(
                fakeEnvironment(fake, backup),
                "--execute",
                "--backup-file", backup.toString(),
                "--cloudcc-cli", fake.executable().toString(),
                "--cloudcc-project", tempDir.toString(),
                "--as-of", "2026-07-14",
                "--json");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output())
                .contains("forward update aborted", "live drift", "product", "ownerid", "before batch write")
                .doesNotContain("Traceback");
        assertSecureCompleteBackup(backup);
        ObjectNode state = readFakeState(fake);
        assertThat(state.path("_control").path("driftApplied").asBoolean()).isTrue();
        assertThat(state.path("_control").path("updateCalls").asInt()).isZero();
        assertThat(updateActionCount(state)).isZero();
        assertThat(state.path("products").path(0).path("ownerid").asText()).isEqualTo("EXTERNAL-OWNER");
        assertThat(state.path("products").path(1).path("ownerid").asText()).isEqualTo(SOURCE_OWNER);
    }

    @Test
    void executeDetectsAccountDriftBeforeSecondBatchAndFullBackupRecoversFirstBatch(@TempDir Path tempDir)
            throws Exception {
        FakeCloudcc fake = createFakeCloudcc(tempDir.resolve("fake"), buildSnapshot(), List.of("success"));
        configureLiveDrift(fake, "Opportunity", "OPPORTUNITY-ID-1", "khmc", "EXTERNAL-ACCOUNT", 2);
        Path originalBackup = tempDir.resolve("before-execute.json");

        ScriptResult execute = runScript(
                fakeEnvironment(fake, originalBackup),
                "--execute",
                "--backup-file", originalBackup.toString(),
                "--cloudcc-cli", fake.executable().toString(),
                "--cloudcc-project", tempDir.toString(),
                "--as-of", "2026-07-14",
                "--json");

        assertThat(execute.exitCode()).isNotZero();
        assertThat(execute.output())
                .contains("forward update aborted", "live drift", "Opportunity", "khmc", "before batch write")
                .doesNotContain("Traceback");
        assertSecureCompleteBackup(originalBackup);
        ObjectNode partialState = readFakeState(fake);
        assertThat(partialState.path("_control").path("driftApplied").asBoolean()).isTrue();
        assertThat(partialState.path("_control").path("updateCalls").asInt()).isEqualTo(1);
        assertThat(updateActionCount(partialState)).isEqualTo(1);
        assertThat(partialState.path("products")).allSatisfy(row ->
                assertThat(row.path("ownerid").asText()).isEqualTo(TARGET_OWNER));
        assertThat(partialState.path("opportunities").path(0).path("ownerid").asText())
                .isEqualTo(SOURCE_OWNER);
        assertThat(partialState.path("opportunities").path(0).path("khmc").asText())
                .isEqualTo("EXTERNAL-ACCOUNT");

        ((ObjectNode) partialState.path("opportunities").path(0)).put("khmc", "OLD-ACCOUNT-1");
        Files.writeString(fake.state(), JSON.writeValueAsString(partialState), StandardCharsets.UTF_8);
        configureFake(fake, List.of("success"));
        Path rollbackBackup = tempDir.resolve("before-rollback.json");
        ScriptResult rollback = runScript(
                fakeEnvironment(fake, rollbackBackup),
                "--execute",
                "--rollback-from", originalBackup.toString(),
                "--backup-file", rollbackBackup.toString(),
                "--cloudcc-cli", fake.executable().toString(),
                "--cloudcc-project", tempDir.toString(),
                "--as-of", "2026-07-14",
                "--json");

        assertThat(rollback.exitCode()).withFailMessage(rollback.output()).isZero();
        assertSecureCompleteBackup(rollbackBackup);
        assertThat(JSON.readTree(rollback.output())
                .path("rollbackPlan").path("summary").path("plannedRecordUpdates").asInt()).isEqualTo(12);
        assertThat(readFakeState(fake).path("products")).allSatisfy(row ->
                assertThat(row.path("ownerid").asText()).isEqualTo(SOURCE_OWNER));
    }

    @Test
    void executeRejectsMalformedUpdateResponses(@TempDir Path tempDir) throws Exception {
        Map<String, String> scenarios = Map.of(
                "count", "unexpected result count",
                "item_failure", "failed for one record",
                "missing_id", "missing id",
                "empty_id", "missing id",
                "mismatched_id", "mismatched id");
        for (Map.Entry<String, String> scenario : scenarios.entrySet()) {
            Path caseDir = tempDir.resolve(scenario.getKey());
            FakeCloudcc fake = createFakeCloudcc(caseDir, buildSnapshot(), List.of(scenario.getKey()));
            Path backup = caseDir.resolve("backup.json");

            ScriptResult result = runScript(
                    fakeEnvironment(fake, backup),
                    "--execute",
                    "--backup-file", backup.toString(),
                    "--cloudcc-cli", fake.executable().toString(),
                    "--cloudcc-project", caseDir.toString(),
                    "--json");

            assertThat(result.exitCode()).as(scenario.getKey()).isNotZero();
            assertThat(result.output()).as(scenario.getKey())
                    .contains(scenario.getValue())
                    .doesNotContain("Traceback");
            assertSecureCompleteBackup(backup);
        }
    }

    @Test
    void successfulFakeExecuteRequiresExternalSalesAAndSalesBAcceptance(@TempDir Path tempDir) throws Exception {
        FakeCloudcc fake = createFakeCloudcc(tempDir.resolve("fake"), buildSnapshot(), List.of("success"));
        Path backup = tempDir.resolve("before-execute.json");

        ScriptResult result = runScript(
                fakeEnvironment(fake, backup),
                "--execute",
                "--backup-file", backup.toString(),
                "--cloudcc-cli", fake.executable().toString(),
                "--cloudcc-project", tempDir.toString(),
                "--as-of", "2026-07-14",
                "--json");

        assertThat(result.exitCode()).withFailMessage(result.output()).isZero();
        assertSecureCompleteBackup(backup);
        JsonNode report = JSON.readTree(result.output());
        assertThat(report.path("verification").path("status").asText())
                .isEqualTo("CONFIGURED_PROJECT_FIELD_STATE_VERIFIED");
        assertThat(report.path("verification").path("externalAcceptance").asText()).isEqualTo("REQUIRED");
        assertThat(report.path("verification").path("plannedRecordUpdatesAfterExecute").asInt()).isZero();
        int updated = 0;
        for (JsonNode count : report.path("writeStats")) {
            updated += count.asInt();
        }
        assertThat(updated).isEqualTo(316);
    }

    @Test
    void executeRefusesUnsafeBackupTargetsBeforeAnyUpdate(@TempDir Path tempDir) throws Exception {
        FakeCloudcc fake = createFakeCloudcc(tempDir.resolve("fake"), buildSnapshot(), List.of("success"));

        Path existing = tempDir.resolve("existing.json");
        Files.writeString(existing, "do-not-overwrite", StandardCharsets.UTF_8);
        assertBackupFailureHasNoUpdate(fake, existing, "File exists");

        configureFake(fake, List.of("success"));
        Path missingDirectory = tempDir.resolve("missing").resolve("backup.json");
        assertBackupFailureHasNoUpdate(fake, missingDirectory, "backup directory does not exist");

        if (Files.exists(Path.of("/dev/null"))) {
            configureFake(fake, List.of("success"));
            assertBackupFailureHasNoUpdate(fake, Path.of("/dev/null", "backup.json"), "Not a directory");
        }
    }

    @Test
    void defaultDryRunExplicitDryRunOfflineAndSnapshotNeverUpdate(@TempDir Path tempDir) throws Exception {
        FakeCloudcc fake = createFakeCloudcc(tempDir.resolve("fake"), buildSnapshot(), List.of("success"));
        List<List<String>> liveReadOnlyModes = List.of(
                List.of("--cloudcc-cli", fake.executable().toString(), "--cloudcc-project", tempDir.toString(), "--json"),
                List.of("--dry-run", "--cloudcc-cli", fake.executable().toString(),
                        "--cloudcc-project", tempDir.toString(), "--json"));
        for (List<String> arguments : liveReadOnlyModes) {
            configureFake(fake, List.of("success"));
            ScriptResult result = runScript(Map.of("FAKE_CLOUDCC_STATE", fake.state().toString()),
                    arguments.toArray(String[]::new));
            assertThat(result.exitCode()).withFailMessage(result.output()).isZero();
            assertThat(updateActionCount(readFakeState(fake))).isZero();
        }

        configureFake(fake, List.of("success"));
        ScriptResult offline = runScript(
                Map.of("FAKE_CLOUDCC_STATE", fake.state().toString()),
                "--offline", "--cloudcc-cli", fake.executable().toString(), "--json");
        assertThat(offline.exitCode()).withFailMessage(offline.output()).isZero();
        assertThat(readFakeState(fake).path("_actions")).isEmpty();

        configureFake(fake, List.of("success"));
        Path snapshot = tempDir.resolve("snapshot.json");
        Files.writeString(snapshot, JSON.writeValueAsString(buildSnapshot()), StandardCharsets.UTF_8);
        ScriptResult snapshotResult = runScript(
                Map.of("FAKE_CLOUDCC_STATE", fake.state().toString()),
                "--snapshot", snapshot.toString(), "--cloudcc-cli", fake.executable().toString(), "--json");
        assertThat(snapshotResult.exitCode()).withFailMessage(snapshotResult.output()).isZero();
        assertThat(readFakeState(fake).path("_actions")).isEmpty();
    }

    @Test
    void snapshotFailsClosedForAccountBoundaryBatchBoundaryAndOrphanReferences(@TempDir Path tempDir)
            throws Exception {
        assertSnapshotFails(tempDir, "v2-count", snapshot ->
                ((ArrayNode) snapshot.path("accounts")).remove(15), "V2 Account count mismatch");
        assertSnapshotFails(tempDir, "v2-duplicate", snapshot -> {
            ObjectNode first = (ObjectNode) snapshot.path("accounts").path(0);
            ObjectNode second = (ObjectNode) snapshot.path("accounts").path(1);
            second.put("id", first.path("id").asText());
            second.put("name", first.path("name").asText());
        }, "missing or duplicate id");
        assertSnapshotFails(tempDir, "v2-owner", snapshot ->
                ((ObjectNode) snapshot.path("accounts").path(0)).put("ownerid", SOURCE_OWNER), "owner mismatch");
        assertSnapshotFails(tempDir, "batch-missing", snapshot ->
                ((ArrayNode) snapshot.path("products")).remove(0), "batch count mismatch");
        assertSnapshotFails(tempDir, "batch-extra", snapshot ->
                ((ArrayNode) snapshot.path("opportunities")).add(snapshot.path("opportunities").path(0).deepCopy()),
                "batch count mismatch");
        assertSnapshotFails(tempDir, "orphan-order-item", snapshot ->
                ((ObjectNode) snapshot.path("orderItems").path(0)).put("orderid", "UNKNOWN-ORDER"),
                "invalid orderid");
    }

    private static ScriptResult runScript(String... arguments) throws Exception {
        return runScript(Map.of(), arguments);
    }

    private static ScriptResult runScript(Map<String, String> environment, String... arguments) throws Exception {
        Path script = Path.of("..", "scripts", "seed-crm-analytics-demo.py").toAbsolutePath().normalize();
        ArrayList<String> command = new ArrayList<>();
        command.add("python3");
        command.add(script.toString());
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
        return new ScriptResult(
                process.exitValue(),
                new String(output, StandardCharsets.UTF_8));
    }

    private static FakeCloudcc createFakeCloudcc(Path directory, ObjectNode snapshot, List<String> responses)
            throws Exception {
        Files.createDirectories(directory);
        Path state = directory.resolve("state.json");
        Files.writeString(state, JSON.writeValueAsString(snapshot), StandardCharsets.UTF_8);
        FakeCloudcc fake = new FakeCloudcc(directory.resolve("fake-cloudcc.py"), state);
        Files.writeString(fake.executable(), fakeCloudccSource(), StandardCharsets.UTF_8);
        assertThat(fake.executable().toFile().setExecutable(true)).isTrue();
        configureFake(fake, responses);
        return fake;
    }

    private static void configureFake(FakeCloudcc fake, List<String> responses) throws Exception {
        ObjectNode state = readFakeState(fake);
        ObjectNode control = state.putObject("_control");
        ArrayNode responseArray = control.putArray("responses");
        responses.forEach(responseArray::add);
        control.put("updateCalls", 0);
        control.put("backupVerified", false);
        state.putArray("_actions");
        Files.writeString(fake.state(), JSON.writeValueAsString(state), StandardCharsets.UTF_8);
    }

    private static void configureLiveDrift(
            FakeCloudcc fake,
            String objectApi,
            String recordId,
            String field,
            String value,
            int queryNumber) throws Exception {
        ObjectNode state = readFakeState(fake);
        ObjectNode control = (ObjectNode) state.path("_control");
        ObjectNode drift = control.putObject("drift");
        drift.put("object", objectApi);
        drift.put("recordId", recordId);
        drift.put("field", field);
        drift.put("value", value);
        drift.put("queryNumber", queryNumber);
        Files.writeString(fake.state(), JSON.writeValueAsString(state), StandardCharsets.UTF_8);
    }

    private static ObjectNode readFakeState(FakeCloudcc fake) throws Exception {
        return (ObjectNode) JSON.readTree(Files.readString(fake.state(), StandardCharsets.UTF_8));
    }

    private static Map<String, String> fakeEnvironment(FakeCloudcc fake, Path expectedBackup) {
        return Map.of(
                "FAKE_CLOUDCC_STATE", fake.state().toString(),
                "FAKE_EXPECTED_BACKUP", expectedBackup.toString());
    }

    private static int updateActionCount(ObjectNode state) {
        int count = 0;
        for (JsonNode action : state.path("_actions")) {
            if (action.path("action").asText().equals("update")) {
                count++;
            }
        }
        return count;
    }

    private static void assertSecureCompleteBackup(Path backup) throws Exception {
        assertThat(backup).exists().isRegularFile();
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(backup);
        assertThat(permissions).containsExactlyInAnyOrderElementsOf(EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
        JsonNode manifest = JSON.readTree(Files.readString(backup, StandardCharsets.UTF_8));
        assertThat(manifest.path("recordCount").asInt()).isEqualTo(316);
        assertThat(manifest.path("records")).hasSize(316);
    }

    private static void assertBackupFailureHasNoUpdate(FakeCloudcc fake, Path backup, String expectedError)
            throws Exception {
        ScriptResult result = runScript(
                fakeEnvironment(fake, backup),
                "--execute",
                "--backup-file", backup.toString(),
                "--cloudcc-cli", fake.executable().toString(),
                "--cloudcc-project", fake.state().getParent().toString(),
                "--json");
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).containsIgnoringCase(expectedError).doesNotContain("Traceback");
        assertThat(updateActionCount(readFakeState(fake))).isZero();
    }

    private static void assertSnapshotFails(
            Path tempDir,
            String name,
            Consumer<ObjectNode> mutation,
            String expectedError) throws Exception {
        ObjectNode snapshot = buildSnapshot();
        mutation.accept(snapshot);
        Path path = tempDir.resolve(name + ".json");
        Files.writeString(path, JSON.writeValueAsString(snapshot), StandardCharsets.UTF_8);
        ScriptResult result = runScript("--snapshot", path.toString(), "--json");
        assertThat(result.exitCode()).as(name).isNotZero();
        assertThat(result.output()).as(name).contains(expectedError).doesNotContain("Traceback");
    }

    private static String fakeCloudccSource() {
        return """
                #!/usr/bin/env python3
                import json
                import math
                import os
                import stat
                import sys
                from pathlib import Path

                OBJECT_KEYS = {
                    "ccuser": "users",
                    "Account": "accounts",
                    "product": "products",
                    "Opportunity": "opportunities",
                    "opportunitypdt": "opportunityProducts",
                    "contract": "contracts",
                    "cloudccorder": "orders",
                    "cloudccorderitem": "orderItems",
                }

                state_path = Path(os.environ["FAKE_CLOUDCC_STATE"])
                state = json.loads(state_path.read_text(encoding="utf-8"))
                action = sys.argv[1]
                body = json.loads(sys.argv[4])
                object_api = body["objectApiName"]
                actions = state.setdefault("_actions", [])
                control = state.setdefault("_control", {})

                def save():
                    state_path.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")

                if action == "pageQuery":
                    rows = state[OBJECT_KEYS[object_api]]
                    query_counts = control.setdefault("queryCounts", {})
                    query_number = int(query_counts.get(object_api, 0)) + 1
                    query_counts[object_api] = query_number
                    drift = control.get("drift") or {}
                    if (drift.get("object") == object_api
                            and int(drift.get("queryNumber", 0)) == query_number
                            and not control.get("driftApplied")):
                        rows_by_id = {str(row.get("id")): row for row in rows}
                        drift_row = rows_by_id[str(drift["recordId"])]
                        drift_row[str(drift["field"])] = str(drift["value"])
                        control["driftApplied"] = True
                    page = int(body.get("pageNUM") or body.get("pageNum") or 1)
                    size = int(body.get("pageSize") or 200)
                    start = (page - 1) * size
                    batch = rows[start:start + size]
                    pages = math.ceil(len(rows) / size) if rows else 0
                    actions.append({"action": action, "object": object_api, "count": len(batch)})
                    save()
                    print(json.dumps({
                        "result": True,
                        "data": batch,
                        "pageCount": pages,
                        "pageNUM": page,
                        "pageSize": size,
                        "totalCount": len(rows),
                    }, ensure_ascii=False))
                    raise SystemExit(0)

                if action != "update":
                    raise SystemExit(80)

                updates = body.get("data", [])
                call = int(control.get("updateCalls", 0)) + 1
                control["updateCalls"] = call
                actions.append({"action": action, "object": object_api, "count": len(updates)})
                if call == 1:
                    backup_path = Path(os.environ["FAKE_EXPECTED_BACKUP"])
                    manifest = json.loads(backup_path.read_text(encoding="utf-8"))
                    mode = stat.S_IMODE(backup_path.stat().st_mode)
                    if mode != 0o600 or manifest.get("recordCount") != 316 or len(manifest.get("records", [])) != 316:
                        save()
                        raise SystemExit(81)
                    control["backupVerified"] = True

                responses = control.get("responses") or ["success"]
                response = responses[min(call - 1, len(responses) - 1)]
                if response == "exit_failure":
                    save()
                    raise SystemExit(9)

                requested_ids = [str(item.get("id") or "") for item in updates]
                if response == "success":
                    rows_by_id = {str(row.get("id")): row for row in state[OBJECT_KEYS[object_api]]}
                    for update in updates:
                        row = rows_by_id[str(update["id"])]
                        for field, value in update.items():
                            if field != "id":
                                row[field] = value
                    ids = [{"success": True, "id": record_id} for record_id in requested_ids]
                elif response == "count":
                    ids = [{"success": True, "id": record_id} for record_id in requested_ids[:-1]]
                elif response == "item_failure":
                    ids = [{"success": False, "id": record_id} if index == 0
                           else {"success": True, "id": record_id}
                           for index, record_id in enumerate(requested_ids)]
                elif response == "missing_id":
                    ids = [{"success": True} for _ in requested_ids]
                elif response == "empty_id":
                    ids = [{"success": True, "id": ""} for _ in requested_ids]
                elif response == "mismatched_id":
                    ids = [{"success": True, "id": "WRONG-ID" if index == 0 else record_id}
                           for index, record_id in enumerate(requested_ids)]
                else:
                    raise SystemExit(82)
                save()
                print(json.dumps({"result": True, "data": {"ids": ids}}, ensure_ascii=False))
                """;
    }

    private static ObjectNode buildSnapshot() {
        ObjectNode snapshot = JSON.createObjectNode();
        ArrayNode users = snapshot.putArray("users");
        users.addObject().put("id", TARGET_OWNER).put("name", "SalesA").put("isusing", "1");

        ArrayNode accounts = snapshot.putArray("accounts");
        for (int index = 1; index <= 16; index++) {
            accounts.addObject()
                    .put("id", "V2-ACCOUNT-" + index)
                    .put("name", "V2-客户-" + String.format("%02d", index))
                    .put("ownerid", TARGET_OWNER)
                    .put("beizhu", "TASK-203-DEMO-V2 | fixture");
        }

        ArrayNode products = snapshot.putArray("products");
        for (int index = 0; index < PRODUCT_CODES.size(); index++) {
            products.addObject()
                    .put("id", "PRODUCT-" + (index + 1))
                    .put("cpdm", PRODUCT_CODES.get(index))
                    .put("ownerid", SOURCE_OWNER);
        }
        addMarkedRows(snapshot.putArray("opportunities"), 24, "OPPORTUNITY", "OPP", "description", "khmc");
        addMarkedRows(snapshot.putArray("opportunityProducts"), 72, "OPPORTUNITY_PRODUCT", "OPP-PDT", "description", null);
        addMarkedRows(snapshot.putArray("contracts"), 16, "CONTRACT", "CONTRACT", "beizhu", "khmc");
        addMarkedRows(snapshot.putArray("orders"), 48, "ORDER", "ORDER", "description", "accountid");
        addMarkedRows(snapshot.putArray("orderItems"), 144, "ORDER_ITEM", "ORDER-ITEM", "description", null);
        return snapshot;
    }

    private static void addMarkedRows(
            ArrayNode rows,
            int count,
            String markerKind,
            String keyPrefix,
            String markerField,
            String accountField) {
        for (int index = 1; index <= count; index++) {
            String key;
            if (markerKind.equals("OPPORTUNITY_PRODUCT")) {
                key = "OPP-" + String.format("%03d", (index - 1) / 3 + 1)
                        + "-" + String.format("%02d", (index - 1) % 3 + 1);
            } else if (markerKind.equals("ORDER_ITEM")) {
                key = "ORDER-" + String.format("%03d", (index - 1) / 3 + 1)
                        + "-" + String.format("%02d", (index - 1) % 3 + 1);
            } else {
                key = keyPrefix + "-" + String.format("%03d", index);
            }
            ObjectNode row = rows.addObject()
                    .put("id", markerKind + "-ID-" + index)
                    .put("ownerid", SOURCE_OWNER)
                    .put(markerField, BATCH + "|" + markerKind + ":" + key + "| fixture");
            if (accountField != null) {
                row.put(accountField, "OLD-ACCOUNT-" + index);
            }
            if (markerKind.equals("OPPORTUNITY_PRODUCT")) {
                row.put("opportunity", "OPPORTUNITY-ID-" + ((index - 1) / 3 + 1));
                row.put("product2", "PRODUCT-" + ((index - 1) % 12 + 1));
            } else if (markerKind.equals("CONTRACT")) {
                row.put("opportunityid", "OPPORTUNITY-ID-" + index);
            } else if (markerKind.equals("ORDER")) {
                row.put("opportunityid", "OPPORTUNITY-ID-" + ((index - 1) % 24 + 1));
                row.put("contractid", "CONTRACT-ID-" + ((index - 1) % 16 + 1));
            } else if (markerKind.equals("ORDER_ITEM")) {
                row.put("orderid", "ORDER-ID-" + ((index - 1) / 3 + 1));
                row.put("product2id", "PRODUCT-" + ((index - 1) % 12 + 1));
            }
        }
    }

    private static void applyPlanToSnapshot(ObjectNode snapshot, JsonNode updates) {
        Map<String, ObjectNode> rowsById = new HashMap<>();
        snapshot.fields().forEachRemaining(entry -> {
            if (entry.getValue().isArray()) {
                entry.getValue().forEach(row -> {
                    if (row.isObject() && row.hasNonNull("id")) {
                        rowsById.put(row.path("id").asText(), (ObjectNode) row);
                    }
                });
            }
        });
        updates.forEach(update -> {
            ObjectNode row = rowsById.get(update.path("id").asText());
            assertThat(row).isNotNull();
            update.path("changes").fields().forEachRemaining(change ->
                    row.put(change.getKey(), change.getValue().path("new").asText()));
        });
    }

    private record FakeCloudcc(Path executable, Path state) {}

    private record ScriptResult(int exitCode, String output) {}
}
