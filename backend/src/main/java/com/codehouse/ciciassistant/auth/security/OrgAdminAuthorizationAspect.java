package com.codehouse.ciciassistant.auth.security;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class OrgAdminAuthorizationAspect {

    @Before("@annotation(com.codehouse.ciciassistant.auth.RequireOrgAdmin)"
            + " || @within(com.codehouse.ciciassistant.auth.RequireOrgAdmin)")
    public void requireAdmin() {
        if (TenantContext.getRoles().stream().noneMatch(RoleCodes::isOrgAdminRole)) {
            throw new ForbiddenException("需要组织管理员权限");
        }
    }
}
