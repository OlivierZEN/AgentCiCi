package com.codehouse.ciciassistant.auth.security;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PlatformRoleAuthorizationAspect {

    @Before("@annotation(com.codehouse.ciciassistant.auth.RequirePlatformRole)"
            + " || @within(com.codehouse.ciciassistant.auth.RequirePlatformRole)")
    public void requirePlatformRole(JoinPoint joinPoint) {
        if (TenantContext.getTokenType().filter("platform"::equals).isEmpty()) {
            throw new ForbiddenException("需要平台账号权限");
        }
        List<String> roles = TenantContext.getRoles();
        if (roles.stream().noneMatch(RoleCodes::isPlatformRole)) {
            throw new ForbiddenException("需要平台角色权限");
        }
        RequirePlatformRole requirePlatformRole = resolveAnnotation(joinPoint);
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

    private RequirePlatformRole resolveAnnotation(JoinPoint joinPoint) {
        if (joinPoint.getSignature() instanceof MethodSignature signature) {
            RequirePlatformRole methodAnnotation = signature.getMethod().getAnnotation(RequirePlatformRole.class);
            if (methodAnnotation != null) {
                return methodAnnotation;
            }
        }
        Object target = joinPoint.getTarget();
        return target == null ? null : target.getClass().getAnnotation(RequirePlatformRole.class);
    }
}
