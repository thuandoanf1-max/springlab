package com.hcmute.springlab.graphql;

public class GraphqlBadRequestException extends RuntimeException {

    public GraphqlBadRequestException(String message) {
        super(message);
    }
}
