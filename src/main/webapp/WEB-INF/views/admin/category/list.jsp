<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<jsp:include page="../header.jsp" />

<h2>Manage Categories</h2>

<form action="${pageContext.request.contextPath}/admin/categories" method="get">
    <input type="text" name="keyword" value="${keyword}" placeholder="Search by name..." />
    <button type="submit">Search</button>
</form>
<br/>
<a href="${pageContext.request.contextPath}/admin/categories/create">Create New Category</a>

<table>
    <thead>
        <tr>
            <th>ID</th>
            <th>Image</th>
            <th>Name</th>
            <th>Description</th>
            <th>Actions</th>
        </tr>
    </thead>
    <tbody>
        <c:forEach var="item" items="${categories}">
            <tr>
                <td>${item.id}</td>
                <td>
                    <c:if test="${not empty item.image}">
                        <img src="${pageContext.request.contextPath}/uploads/${item.image}" width="50" height="50" style="object-fit: cover;" />
                    </c:if>
                </td>
                <td>${item.name}</td>
                <td>${item.description}</td>
                <td>
                    <a href="${pageContext.request.contextPath}/admin/categories/edit/${item.id}">Edit</a> |
                    <a href="${pageContext.request.contextPath}/admin/categories/delete/${item.id}" onclick="return confirm('Are you sure?');">Delete</a>
                </td>
            </tr>
        </c:forEach>
    </tbody>
</table>

<jsp:include page="../footer.jsp" />

