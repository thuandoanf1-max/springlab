<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
<jsp:include page="../header.jsp" />

<h2>${empty user.id ? 'Create' : 'Edit'} User</h2>

<form:form action="${pageContext.request.contextPath}/admin/users/${empty user.id ? 'create' : 'edit/'.concat(user.id)}" 
           method="post" modelAttribute="user">
    <form:hidden path="id" />
    
    <div class="form-group">
        <label>Username:</label>
        <form:input path="username" />
        <form:errors path="username" cssClass="error" />
    </div>
    
    <div class="form-group">
        <label>Password:</label>
        <form:password path="password" />
        <form:errors path="password" cssClass="error" />
    </div>

    <div class="form-group">
        <label>Full Name:</label>
        <form:input path="fullname" />
        <form:errors path="fullname" cssClass="error" />
    </div>

    <div class="form-group">
        <label>Email:</label>
        <form:input path="email" />
        <form:errors path="email" cssClass="error" />
    </div>

    <div class="form-group">
        <label>Role:</label>
        <form:select path="role">
            <form:option value="USER">USER</form:option>
            <form:option value="ADMIN">ADMIN</form:option>
        </form:select>
        <form:errors path="role" cssClass="error" />
    </div>
    
    <button type="submit">Save</button>
    <a href="${pageContext.request.contextPath}/admin/users">Cancel</a>
</form:form>

<jsp:include page="../footer.jsp" />

