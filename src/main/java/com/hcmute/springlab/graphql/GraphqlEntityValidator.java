package com.hcmute.springlab.graphql;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class GraphqlEntityValidator {

    private final Validator validator;

    public GraphqlEntityValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(Object entity) {
        validator.validate(entity).stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .ifPresent(message -> {
                    throw new GraphqlBadRequestException(message);
                });
    }
}
