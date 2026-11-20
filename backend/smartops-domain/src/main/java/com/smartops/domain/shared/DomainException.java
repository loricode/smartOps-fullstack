package com.smartops.domain.shared;

/**
 * Raíz de las excepciones de negocio. Siempre lleva un código estable
 * que la capa de exposición traduce a una respuesta HTTP.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
