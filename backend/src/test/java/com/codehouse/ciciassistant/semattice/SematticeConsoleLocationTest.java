package com.codehouse.ciciassistant.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SematticeConsoleLocationTest {
    @Test
    void buildsAnEnvironmentInjectedHandoffLocation() {
        SematticeConsoleLocation location = new SematticeConsoleLocation("https://data.example.test:9443/");

        assertThat(location.handoffUri("opaque_ticket").toString())
                .isEqualTo("https://data.example.test:9443/console/handoff?ticket=opaque_ticket");
    }

    @Test
    void rejectsAnythingOtherThanAnHttpsOrigin() {
        assertThatThrownBy(() -> new SematticeConsoleLocation("http://data.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SematticeConsoleLocation("https://data.example.test/path?x=1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
