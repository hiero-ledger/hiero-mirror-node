// SPDX-License-Identifier: Apache-2.0

import java.net.HttpURLConnection;
import java.net.URI;

// Dependency-free liveness probe for the container HEALTHCHECK. Compiled at build time
// by the :compileHealthCheck Gradle task and copied into the runtime image as a plain class file.
// Exits 0 when the endpoint returns HTTP 200, otherwise 1.
public final class HealthCheck {

    public static void main(String[] args) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(args[0]).toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setRequestMethod("GET");
            System.exit(connection.getResponseCode() == 200 ? 0 : 1);
        } catch (Exception e) {
            System.err.println("Health check failed: " + e.getMessage());
            System.exit(1);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
