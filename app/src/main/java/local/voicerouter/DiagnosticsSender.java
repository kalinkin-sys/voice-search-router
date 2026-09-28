package local.voicerouter;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class DiagnosticsSender {
    private static final String PREFS = "diagnostic_transport";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final int MAX_RESPONSE_CHARS = 1_000;

    static final class Result {
        final boolean success;
        final boolean notConfigured;
        final int statusCode;
        final String message;

        Result(boolean success, boolean notConfigured, int statusCode, String message) {
            this.success = success;
            this.notConfigured = notConfigured;
            this.statusCode = statusCode;
            this.message = message;
        }
    }

    private DiagnosticsSender() {}

    static Result send(Context context, String report) {
        if (DiagnosticsConfig.ENDPOINT.length() == 0) {
            return new Result(false, true, 0, "Endpoint is not configured");
        }

        HttpURLConnection connection = null;
        try {
            byte[] body = report.getBytes(StandardCharsets.UTF_8);
            URL endpoint = new URL(DiagnosticsConfig.ENDPOINT);
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Voice-Router-Install",
                    installationId(context));
            connection.setRequestProperty("X-Voice-Router-Version", "1");

            OutputStream output = connection.getOutputStream();
            output.write(body);
            output.close();

            int status = connection.getResponseCode();
            String response = readResponse(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            return new Result(status >= 200 && status < 300, false, status, response);
        } catch (Exception error) {
            return new Result(false, false, 0,
                    error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String installationId(Context context) {
        String current = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_INSTALLATION_ID, null);
        if (current != null) return current;
        String created = UUID.randomUUID().toString();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_INSTALLATION_ID, created).apply();
        return created;
    }

    private static String readResponse(InputStream input) throws IOException {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[256];
        int count;
        while ((count = reader.read(buffer)) >= 0 && result.length() < MAX_RESPONSE_CHARS) {
            result.append(buffer, 0,
                    Math.min(count, MAX_RESPONSE_CHARS - result.length()));
        }
        reader.close();
        return result.toString();
    }
}
