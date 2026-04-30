package com.codehouse.ciciassistant.auth.security;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PlatformRoleAuthorizationAspect {

    @Before("@annotation(requirePlatformRole) || @within(requirePlatformRole)")
    public void requirePlatformRole(JoinPoint joinPoint, RequirePlatformRole requirePlatformRole) {
        List<String> roles = TenantContext.getRoles();
        if (roles.stream().noneMatch(RoleCodes::isPlatformRole)) {
            throw new ForbiddenException("需要平台角色权限");
        }
        String[] allowed = requirePlatformRole == null ? new String[0] : requirePlatformRole.value();
        if (allowed.length == 0) {
            return;
        }
        for (String role : allowed) {
            if (roles.contains(role)) {
                return;
            }
        }
        throw new ForbiddenException("当前平台角色无权访问该资源");
    }
}
