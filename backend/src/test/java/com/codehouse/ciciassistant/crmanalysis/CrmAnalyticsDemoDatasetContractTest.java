package com.codehouse.ciciassistant.crmanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CrmAnalyticsDemoDatasetContractTest {

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
    }
}
