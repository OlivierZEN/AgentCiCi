package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.platform.service.PlatformRegisteredUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PlatformRegisteredUserServiceTest {

    private final UserAccountRepository accounts = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final PlatformRegisteredUserService service = new PlatformRegisteredUserService(accounts);

    @Test
    void listsEveryGlobalAccountExactlyOnceWithoutConsultingMemberships() {
        UserAccountEntity withoutCompany = account("13800000001", "未加入组织");
        UserAccountEntity oneCompany = account("13800000002", "单组织成员");
        UserAccountEntity multipleCompanies = account("13800000003", "多组织成员");
        PageRequest request = PageRequest.of(0, 50);
        when(accounts.searchRegisteredAccounts("组织", request)).thenReturn(new PageImpl<>(
                List.of(withoutCompany, oneCompany, multipleCompanies), request, 3));

        PlatformRegisteredUserService.RegisteredUserPage result = service.listRegisteredUsers(" 组织 ", 0, 50);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items()).extracting(PlatformRegisteredUserService.RegisteredUserView::mobile)
                .containsExactly("13800000001", "13800000002", "13800000003");
        verify(accounts).searchRegisteredAccounts("组织", request);
    }

    @Test
    void preservesSearchAndPaginationParametersForTheAccountDirectory() {
        PageRequest request = PageRequest.of(1, 20);
        when(accounts.searchRegisteredAccounts("alice", request)).thenReturn(new PageImpl<>(List.of(), request, 21));

        PlatformRegisteredUserService.RegisteredUserPage result = service.listRegisteredUsers(" alice ", 1, 20);

        assertThat(result.total()).isEqualTo(21);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(20);
        verify(accounts).searchRegisteredAccounts("alice", request);
    }

    private UserAccountEntity account(String mobile, String displayName) {
        UserAccountEntity account = new UserAccountEntity(mobile);
        account.setDisplayName(displayName);
        return account;
    }
}
