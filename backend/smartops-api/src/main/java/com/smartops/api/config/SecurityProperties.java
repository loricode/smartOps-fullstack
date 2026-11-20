package com.smartops.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Usuarios de demostración. En un entorno real se reemplaza por un proveedor OAuth2/OIDC. */
@ConfigurationProperties(prefix = "smartops.security")
public record SecurityProperties(List<User> users) {

    public record User(String username, String password, List<String> roles) { }
}
