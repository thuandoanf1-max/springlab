package com.hcmute.springlab.graphql;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class GraphqlPageRequestFactory {

    private static final int MAX_PAGE_SIZE = 100;

    public Pageable create(Integer page, Integer size) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? 5 : size;
        if (resolvedPage < 0) {
            throw new GraphqlBadRequestException("Page must not be negative");
        }
        if (resolvedSize <= 0) {
            throw new GraphqlBadRequestException("Size must be greater than zero");
        }
        if (resolvedSize > MAX_PAGE_SIZE) {
            throw new GraphqlBadRequestException("Size must not exceed " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(resolvedPage, resolvedSize, Sort.by("id").ascending());
    }
}
