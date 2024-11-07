package org.example;

import com.fastcgi.FCGIInterface;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FastCGI {

    private static List<Map<String, String>> results = new ArrayList<>();

    public static void main(String[] args) {
        var fcgiInterface = new FCGIInterface(); // Создаем интерфейс FastCGI

        // Бесконечный цикл для обработки запросов
        while (fcgiInterface.FCGIaccept() >= 0) {

            String queryString = System.getProperties().getProperty("QUERY_STRING");
            String requestUri = System.getProperties().getProperty("REQUEST_URI");


            logToFile("Request URI: " + requestUri);
            logToFile("Query String: " + queryString);

            if (requestUri != null) {
                if (requestUri.contains("script")) {
                    // Запрос требует параметры, проверяем их наличие и валидность
                    if (queryString != null) {
                        if (validateData(queryString)) {
                            handleScript(queryString);
                        } else {
                            sendRedirectToIndex();
                        }
                    } else {
                        sendHttpResponse("HTTP/1.1 400 Bad Request", "Missing query parameters");
                    }
                } else if (requestUri.contains("get_result")) {
                    handleGetResults();
                } else if (requestUri.contains("drop_data")) {
                    handleDropData();
                } else {
                    sendHttpResponse("HTTP/1.1 404 Not Found", "Resource not found");
                }
            } else {
                sendHttpResponse("HTTP/1.1 400 Bad Request", "Invalid request URI");
            }
        }
    }

    // Метод для валидации данных
    private static boolean validateData(String queryString) {
        Map<String, String> params = parseQueryString(queryString);

        // Проверка наличия параметров
        if (!params.containsKey("x") || !params.containsKey("y") || !params.containsKey("R") || !params.containsKey("IsThatPage")) {
            logToFile("Validation failed: missing parameters");
            return false;
        }

        try {
            // Валидация параметра x
            int x = Integer.parseInt(params.get("x"));
            if (x < -5 || x > 3) {
                logToFile("Validation failed: invalid x");
                return false;
            }

            // Валидация параметра y
            double y = Double.parseDouble(params.get("y"));
            if (y < -3 || y > 3) {
                logToFile("Validation failed: invalid y");
                return false;
            }

            // Валидация параметра R
            double R = Double.parseDouble(params.get("R"));
            if (R < 1 || R > 5) {
                logToFile("Validation failed: invalid R");
                return false;
            }

            // Валидация параметра IsThatPage
            String IsThatPage = params.get("IsThatPage");
            if (!(IsThatPage.equals("true") || IsThatPage.equals("false"))) {
                logToFile("Validation failed: invalid IsThatPage");
                return false;
            }

        } catch (NumberFormatException e) {
            // Если произошла ошибка при преобразовании данных в числа
            logToFile("Validation failed: invalid data format");
            return false;
        }

        // Если все параметры валидны
        return true;
    }

    private static void handleScript(String queryString) {
        Map<String, String> params = parseQueryString(queryString);
        double x = Double.parseDouble(params.get("x"));
        double y = Double.parseDouble(params.get("y"));
        double R = Double.parseDouble(params.get("R"));
        boolean IsThatPage = Boolean.parseBoolean(params.get("IsThatPage"));

        // Выполняем логику
        boolean result = isDotInside(x, y, R);

        String executionTime = "0." + (System.nanoTime() / 1_000_000);
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss"));

        Map<String, String> resultData = new HashMap<>();
        resultData.put("x", String.valueOf(x));
        resultData.put("y", String.valueOf(y));
        resultData.put("R", String.valueOf(R));
        resultData.put("res", String.valueOf(result));
        resultData.put("executionTime", executionTime);
        resultData.put("date", currentTime);

        results.add(resultData);


        if (IsThatPage) {
            String jsonResponse = buildJson(resultData);
            sendHttpResponse("HTTP/1.1 200 OK", jsonResponse);
        } else {
            sendRedirectToIndex();
        }
    }

    private static void handleGetResults() {
        String jsonResponse = buildJsonList(results);
        sendHttpResponse("HTTP/1.1 200 OK", jsonResponse);
    }

    private static void handleDropData() {
        results.clear();
        sendHttpResponse("HTTP/1.1 200 OK", "");
    }

    private static boolean isDotInside(double x, double y, double R) {
        // Прямоугольник
        if (x >= -R && x <= 0 && y >= 0 && y <= R / 2) {
            return true;
        }

        // Четверть круга
        if (x >= 0 && y <= 0 && (x * x + y * y) <= (R / 2) * (R / 2)) {
            return true;
        }

        // Треугольная часть
        if (x >= 0 && y >= 0 && x + y <= R) {
            return true;
        }

        return false;
    }

    private static Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString != null && !queryString.isEmpty()) {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }

    private static String buildJson(Map<String, String> data) {
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, String> entry : data.entrySet()) {
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\",");
        }
        json.deleteCharAt(json.length() - 1);
        json.append("}");
        return json.toString();
    }

    private static String buildJsonList(List<Map<String, String>> dataList) {
        StringBuilder json = new StringBuilder("[");
        for (Map<String, String> data : dataList) {
            json.append(buildJson(data)).append(",");
        }
        if (!dataList.isEmpty()) {
            json.deleteCharAt(json.length() - 1);
        }
        json.append("]");
        return json.toString();
    }

    private static void sendHttpResponse(String statusLine, String content) {
        String response = """
            %s
            Content-Type: application/json
            Content-Length: %d

            %s
        """.formatted(statusLine, content.getBytes(StandardCharsets.UTF_8).length, content);
        System.out.println(response);
    }

    private static void sendRedirectToIndex() {
        String response = """
        HTTP/1.1 302 Found
        Location: /index.html
        Content-Length: 0

        """;
        System.out.println(response);
    }

    // Метод для логирования
    private static void logToFile(String message) {
        try (FileWriter fw = new FileWriter("/home/studs/s409352/httpd-root/fcgi-bin/debug.log", true)) { // Указываем путь к файлу
            fw.write(LocalDateTime.now() + ": " + message + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
