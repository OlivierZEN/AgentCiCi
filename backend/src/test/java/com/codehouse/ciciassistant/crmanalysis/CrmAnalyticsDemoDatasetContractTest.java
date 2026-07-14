package com.codehouse.ciciassistant.crmanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    private static ScriptResult runScript(String... arguments) throws Exception {
        Path script = Path.of("..", "scripts", "seed-crm-analytics-demo.py").toAbsolutePath().normalize();
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("python3");
        command.add(script.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
        return new ScriptResult(
                process.exitValue(),
                new String(output, StandardCharsets.UTF_8));
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

    private record ScriptResult(int exitCode, String output) {}
}
