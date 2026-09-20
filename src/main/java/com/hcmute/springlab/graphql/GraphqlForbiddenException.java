package com.hcmute.springlab.graphql;

public class GraphqlForbiddenException extends RuntimeException {

    public GraphqlForbiddenException(String message) {
        super(message);
    }
}
