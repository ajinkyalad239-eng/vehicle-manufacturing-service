package com.example.manufacturing.security;

/**
 * Application roles. They are Keycloak realm roles; the app sees them as ROLE_&lt;name&gt; authorities.
 *
 * <table>
 *   <caption>Who may do what</caption>
 *   <tr><th>Role</th><th>Can do</th></tr>
 *   <tr><td>VIEWER</td><td>Read everything</td></tr>
 *   <tr><td>PLANNER</td><td>Read; create, re-plan, delete and change the status of production orders</td></tr>
 *   <tr><td>SUPERVISOR</td><td>Read; change order status and report progress on the line</td></tr>
 *   <tr><td>ADMIN</td><td>Everything, including managing plants and vehicle models</td></tr>
 * </table>
 */
public final class Roles {

    public static final String VIEWER = "VIEWER";
    public static final String PLANNER = "PLANNER";
    public static final String SUPERVISOR = "SUPERVISOR";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}
