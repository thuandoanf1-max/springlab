package com.hcmute.springlab.graphql;

import com.hcmute.springlab.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AdminMutationAuthorizer {

    public void requireAdmin() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new GraphqlForbiddenException("Administrator authentication is required for mutations");
        }

        HttpServletRequest request = attributes.getRequest();
        HttpSession session = request.getSession(false);
        Object loggedInUser = session == null ? null : session.getAttribute("loggedInUser");

        if (!(loggedInUser instanceof User user) || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new GraphqlForbiddenException("Administrator authentication is required for mutations");
        }
    }
}
