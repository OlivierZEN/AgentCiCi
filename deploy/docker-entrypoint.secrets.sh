#!/bin/sh
set -eu

load_secret() {
    variable_name="$1"
    file_variable_name="${variable_name}_FILE"
    eval "file_path=\${$file_variable_name:-}"
    if [ -z "$file_path" ]; then
        return
    fi
    if [ ! -f "$file_path" ] || [ ! -r "$file_path" ]; then
        echo "AgentCiCi startup failed: unreadable secret file for $variable_name" >&2
        exit 1
    fi
    value="$(tr -d '\r\n' < "$file_path")"
    if [ -z "$value" ]; then
        echo "AgentCiCi startup failed: empty secret file for $variable_name" >&2
        exit 1
    fi
    export "$variable_name=$value"
    unset "$file_variable_name"
}

for variable_name in \
    SPRING_DATASOURCE_PASSWORD \
    SPRING_RABBITMQ_PASSWORD \
    APP_AUTH_JWT_SECRET \
    APP_AUTH_OIDC_CLIENT_SECRET \
    APP_AUTH_OIDC_PROVISIONING_ADMIN_CLIENT_SECRET \
    APP_AGENT_OPEN_API_KEY_PEPPER \
    APP_SECRET_KEY \
    APP_SECURITY_SECRET_KEY \
    APP_NATIVE_AGENTCICI_INTERNAL_HMAC_KEY \
    APP_SEMATTICE_INTERNAL_HMAC_KEY \
    APP_AUTH_OFFICIAL_ACCESS_PRIVATE_KEY_PKCS8_BASE64
do
    load_secret "$variable_name"
done

exec "$@"
