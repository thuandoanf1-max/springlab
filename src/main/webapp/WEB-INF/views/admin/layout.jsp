<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <title>Admin Dashboard</title>
    <style>
        body { font-family: Arial, sans-serif; }
        .nav { margin-bottom: 20px; padding: 10px; background: #eee; }
        .nav a { margin-right: 15px; text-decoration: none; font-weight: bold; }
        .content { padding: 10px; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
        .error { color: red; }
    </style>
</head>
<body>
    <div class="nav">
        <a href="${pageContext.request.contextPath}/admin/categories">Manage Categories</a>
        <a href="${pageContext.request.contextPath}/admin/users">Manage Users</a>
        <span style="float: right;">
            Welcome, ${sessionScope.loggedInUser.fullname}
            | <a href="${pageContext.request.contextPath}/logout">Logout</a>
        </span>
    </div>
    <div class="content">
        <jsp:invoke fragment="body"/>
    </div>
</body>
</html>

