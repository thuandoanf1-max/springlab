package com.hcmute.springlab.controller.graphql;

import com.hcmute.springlab.graphql.GraphqlBadRequestException;
import com.hcmute.springlab.graphql.GraphqlForbiddenException;
import com.hcmute.springlab.graphql.GraphqlNotFoundException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class GraphqlExceptionHandler {

    @GraphQlExceptionHandler(GraphqlBadRequestException.class)
    public GraphQLError handleBadRequest(GraphqlBadRequestException exception) {
        return error(exception.getMessage(), ErrorType.BAD_REQUEST);
    }

    @GraphQlExceptionHandler(GraphqlForbiddenException.class)
    public GraphQLError handleForbidden(GraphqlForbiddenException exception) {
        return error(exception.getMessage(), ErrorType.FORBIDDEN);
    }

    @GraphQlExceptionHandler(GraphqlNotFoundException.class)
    public GraphQLError handleNotFound(GraphqlNotFoundException exception) {
        return error(exception.getMessage(), ErrorType.NOT_FOUND);
    }

    private GraphQLError error(String message, ErrorType errorType) {
        return GraphqlErrorBuilder.newError()
                .message(message)
                .errorType(errorType)
                .build();
    }
}
