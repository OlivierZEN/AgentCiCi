package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.platform.service.PlatformRegisteredUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class PlatformRegisteredUserServiceTest {

    private final UserAccountRepository accounts = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final UserRepository memberships = org.mockito.Mockito.mock(UserRepository.class);
    private final PlatformRegisteredUserService service = new PlatformRegisteredUserService(accounts, memberships);

    @Test
    void listsEveryGlobalAccountExactlyOnceWithCurrentOrganizations() {
        UserAccountEntity withoutCompany = account("13800000001", "未加入组织");
        UserAccountEntity oneCompany = account("13800000002", "单组织成员");
        UserAccountEntity multipleCompanies = account("13800000003", "多组织成员");
        CompanyEntity firstCompany = company("company-1", "第一组织");
        CompanyEntity secondCompany = company("company-2", "第二组织");
        UserEntity inactiveMembership = membership(multipleCompanies, company("company-inactive", "历史组织"));
        inactiveMembership.setMemberStatus(UserEntity.STATUS_SUSPENDED);
        PageRequest request = PageRequest.of(0, 50);
        when(accounts.searchRegisteredAccounts("组织", request)).thenReturn(new PageImpl<>(
                List.of(withoutCompany, oneCompany, multipleCompanies), request, 3));
        when(memberships.findByAccount_IdInAndMemberStatusOrderByCreatedAtDesc(
                List.of(withoutCompany.getId(), oneCompany.getId(), multipleCompanies.getId()),
                UserEntity.STATUS_ACTIVE)).thenReturn(List.of(
                membership(oneCompany, firstCompany),
                membership(multipleCompanies, firstCompany),
                membership(multipleCompanies, firstCompany),
                membership(multipleCompanies, secondCompany),
                inactiveMembership));

        PlatformRegisteredUserService.RegisteredUserPage result = service.listRegisteredUsers(" 组织 ", 0, 50);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items()).extracting(PlatformRegisteredUserService.RegisteredUserView::mobile)
                .containsExactly("13800000001", "13800000002", "13800000003");
        assertThat(result.items().get(0).organizations()).isEmpty();
        assertThat(result.items().get(1).organizations())
                .extracting(PlatformRegisteredUserService.RegisteredUserOrganizationView::name)
                .containsExactly("第一组织");
        assertThat(result.items().get(2).organizations())
                .extracting(PlatformRegisteredUserService.RegisteredUserOrganizationView::name)
                .containsExactly("第一组织", "第二组织");
        assertThat(result.items()).extracting(PlatformRegisteredUserService.RegisteredUserView::publicId)
                .containsExactly("U2026A1B2C3D4", "U2026E5F6G7H8", "U2026J9K0L1M2");
        verify(accounts).searchRegisteredAccounts("组织", request);
        verify(memberships).findByAccount_IdInAndMemberStatusOrderByCreatedAtDesc(
                List.of(withoutCompany.getId(), oneCompany.getId(), multipleCompanies.getId()),
                UserEntity.STATUS_ACTIVE);
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
        ReflectionTestUtils.setField(account, "publicId", switch (mobile) {
            case "13800000001" -> "U2026A1B2C3D4";
            case "13800000002" -> "U2026E5F6G7H8";
            default -> "U2026J9K0L1M2";
        });
        return account;
    }

    private CompanyEntity company(String id, String name) {
        return new CompanyEntity(id, name, "ACTIVE");
    }

    private UserEntity membership(UserAccountEntity account, CompanyEntity company) {
        return new UserEntity(company, account, "ORG_USER");
    }
}
