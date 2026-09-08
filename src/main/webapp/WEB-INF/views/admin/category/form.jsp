<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
<jsp:include page="../header.jsp" />

<h2>${empty category.id ? 'Create' : 'Edit'} Category</h2>

<form:form action="${pageContext.request.contextPath}/admin/categories/${empty category.id ? 'create' : 'edit/'.concat(category.id)}" 
           method="post" modelAttribute="category" enctype="multipart/form-data">
    <form:hidden path="id" />
    
    <div class="form-group">
        <label>Name:</label>
        <form:input path="name" />
        <form:errors path="name" cssClass="error" />
    </div>
    
    <div class="form-group">
        <label>Description:</label>
        <form:textarea path="description" rows="4" cols="50"/>
        <form:errors path="description" cssClass="error" />
    </div>

    <div class="form-group">
        <label>Category Image:</label>
        <c:if test="${not empty category.image}">
            <p>Current image: <img src="${pageContext.request.contextPath}/uploads/${category.image}" width="100" /></p>
        </c:if>
        <input type="file" name="imageFile" accept="image/*" />
    </div>
    
    <button type="submit">Save</button>
    <a href="${pageContext.request.contextPath}/admin/categories">Cancel</a>
</form:form>

<jsp:include page="../footer.jsp" />
