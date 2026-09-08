<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<jsp:include page="../header.jsp" />

<h2>Manage Users</h2>

<form action="${pageContext.request.contextPath}/admin/users" method="get">
    <input type="text" name="keyword" value="${keyword}" placeholder="Search username, name, email..." style="width: 250px;" />
    <button type="submit">Search</button>
</form>
<br/>
<a href="${pageContext.request.contextPath}/admin/users/create">Create New User</a>

<table>
    <thead>
        <tr>
            <th>ID</th>
            <th>Username</th>
            <th>Full Name</th>
            <th>Email</th>
            <th>Role</th>
            <th>Actions</th>
        </tr>
    </thead>
    <tbody>
        <c:forEach var="item" items="${users}">
            <tr>
                <td>${item.id}</td>
                <td>${item.username}</td>
                <td>${item.fullname}</td>
                <td>${item.email}</td>
                <td>${item.role}</td>
                <td>
                    <a href="${pageContext.request.contextPath}/admin/users/edit/${item.id}">Edit</a> |
                    <a href="${pageContext.request.contextPath}/admin/users/delete/${item.id}" onclick="return confirm('Are you sure?');">Delete</a>
                </td>
            </tr>
        </c:forEach>
    </tbody>
</table>

<jsp:include page="../footer.jsp" />

