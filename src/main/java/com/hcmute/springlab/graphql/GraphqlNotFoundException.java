package com.hcmute.springlab.graphql;

public class GraphqlNotFoundException extends RuntimeException {

    public GraphqlNotFoundException(String message) {
        super(message);
    }
}
