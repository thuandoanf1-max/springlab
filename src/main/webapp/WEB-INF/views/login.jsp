<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <title>Login</title>
</head>
<body>
    <h2>Admin Login</h2>
    <c:if test="${not empty error}">
        <div style="color: red;">${error}</div>
    </c:if>
    <form action="${pageContext.request.contextPath}/login" method="post">
        <div>
            <label>Username:</label>
            <input type="text" name="username" required />
        </div>
        <div>
            <label>Password:</label>
            <input type="password" name="password" required />
        </div>
        <button type="submit">Login</button>
    </form>
</body>
</html>

