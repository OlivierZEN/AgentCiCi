package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.service.OidcLoginStateStore;
import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class OidcLoginStateStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void encryptsAndConsumesLoginTokensExactlyOnce() {
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        OidcLoginStateStore store = new OidcLoginStateStore(
                new ObjectMapper().findAndRegisterModules(), redisProvider, new SecretCipherService(""));

        store.saveLoginSession("session-one", "id-token", "refresh-token", Duration.ofMinutes(5));

        assertThat(store.consumeLoginSession("session-one"))
                .isEqualTo(new OidcLoginStateStore.LoginSession("id-token", "refresh-token"));
        assertThat(store.consumeLoginSession("session-one")).isNull();
        assertThat(store.consumeLoginSession(" ")).isNull();
    }
}
