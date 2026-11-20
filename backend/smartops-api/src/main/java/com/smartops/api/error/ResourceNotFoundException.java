package com.smartops.api.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("No existe " + resource + " con id " + id);
    }
}
