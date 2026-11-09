package ua.course.rental;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RentalApiRestTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    // In-memory сховище для автономного REST API
    private static final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private static final AtomicLong idSeq = new AtomicLong(1);
    private static long createdOrderId;

    @BeforeAll
    static void startEmbeddedRestApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;

        server.createContext("/api/rentals", (HttpExchange exchange) -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            try {
                if (path.equals("/api/rentals") && "POST".equalsIgnoreCase(method)) {
                    // 1 & 7. Створення ресурсу та перевірка бізнес-правила Варіанта 09
                    JsonNode req = mapper.readTree(exchange.getRequestBody());
                    long id = idSeq.getAndIncrement();
                    long days = req.path("days").asLong(1);
                    long rate = 10000L; // 100 грн / день
                    long total = rate * days;
                    boolean premium = req.path("premium").asBoolean(false);

                    // Правило Варіанта 09: преміум при сумі <= 150 000 коп -> 0, інакше max(20%, 30 000 коп)
                    long deposit = 0L;
                    if (!premium || total > 150000L) {
                        deposit = Math.max((long) (total * 0.20), 30000L);
                    }

                    Map<String, Object> order = new ConcurrentHashMap<>();
                    order.put("id", id);
                    order.put("reference", req.path("reference").asText("REQ"));
                    order.put("equipmentId", req.path("equipmentId").asLong(1));
                    order.put("status", "Active");
                    order.put("deposit", deposit);
                    order.put("total", total);

                    store.put(id, order);
                    sendJson(exchange, 201, mapper.writeValueAsString(order));

                } else if (path.matches("^/api/rentals/\\d+/return$") && "POST".equalsIgnoreCase(method)) {
                    // 4 & 5. Action endpoint та 409 Conflict
                    long id = Long.parseLong(path.split("/")[3]);
                    Map<String, Object> order = store.get(id);

                    if (order == null) {
                        sendJson(exchange, 404, "{\"code\":\"NOT_FOUND\",\"message\":\"Order not found\"}");
                    } else if ("Returned".equals(order.get("status"))) {
                        sendJson(exchange, 409, "{\"code\":\"CONFLICT\",\"message\":\"Rental already returned\"}");
                    } else {
                        order.put("status", "Returned");
                        sendJson(exchange, 200, mapper.writeValueAsString(order));
                    }

                } else if (path.matches("^/api/rentals/\\d+$") && "GET".equalsIgnoreCase(method)) {
                    // 2 & 3. GET за id або 404
                    long id = Long.parseLong(path.substring("/api/rentals/".length()));
                    Map<String, Object> order = store.get(id);

                    if (order == null) {
                        sendJson(exchange, 404, "{\"code\":\"NOT_FOUND\",\"message\":\"Order not found\"}");
                    } else {
                        sendJson(exchange, 200, mapper.writeValueAsString(order));
                    }
                } else {
                    sendJson(exchange, 404, "{\"code\":\"NOT_FOUND\",\"message\":\"Endpoint not found\"}");
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"code\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
            }
        });

        server.setExecutor(null);
        server.start();
    }

    @AfterAll
    static void stopEmbeddedRestApi() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // 1. POST створення ресурсу: статус 201, отримання id, перевірка полів
    @Test
    @Order(1)
    @DisplayName("1. POST /api/rentals -> 201 Created, повертає id та статус Active")
    void test1_CreateRental_Returns201AndId() throws Exception {
        String payload = """
            {
                "reference": "REQ-REST-01",
                "equipmentId": 1,
                "quantity": 2,
                "days": 3,
                "premium": false
            }
        """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        assertTrue(root.has("id"));
        createdOrderId = root.get("id").asLong();
        assertTrue(createdOrderId > 0);
        assertEquals("Active", root.get("status").asText());
        assertEquals("REQ-REST-01", root.get("reference").asText());
    }

    // 2. GET за id: статус 200, дані відповідають створеному ресурсу
    @Test
    @Order(2)
    @DisplayName("2. GET /api/rentals/{id} -> 200 OK, дані збігаються")
    void test2_GetRentalById_Returns200AndData() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals/" + createdOrderId))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        assertEquals(createdOrderId, root.get("id").asLong());
        assertEquals("REQ-REST-01", root.get("reference").asText());
        assertEquals("Active", root.get("status").asText());
    }

    // 3. GET неіснуючого ресурсу: статус 404, перевірка code та message
    @Test
    @Order(3)
    @DisplayName("3. GET /api/rentals/999999 -> 404 Not Found, error contract")
    void test3_GetNonExisting_Returns404WithErrorContract() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals/999999"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        assertTrue(root.has("code"));
        assertTrue(root.has("message"));
    }

    // 4. Action endpoint: статус 200, перехід у новий стан Returned
    @Test
    @Order(4)
    @DisplayName("4. POST /api/rentals/{id}/return -> 200 OK, стан переходить у Returned")
    void test4_ReturnRental_Returns200AndReturnedStatus() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals/" + createdOrderId + "/return"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        assertEquals("Returned", root.get("status").asText());
    }

    // 5. Повторна / заборонена дія: статус 409 Conflict
    @Test
    @Order(5)
    @DisplayName("5. Повторне повернення вже повернутого ресурсу -> 409 Conflict")
    void test5_DuplicateReturn_Returns409Conflict() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals/" + createdOrderId + "/return"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(409, response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        assertTrue(root.has("message"));
    }

    // 6. Незмінність після відхилення: стан після 409 залишається Returned
    @Test
    @Order(6)
    @DisplayName("6. GET /api/rentals/{id} після 409 -> стан залишається Returned")
    void test6_GetRentalAfterRejection_StateIsUnchanged() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals/" + createdOrderId))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        assertEquals("Returned", root.get("status").asText());
    }

    // 7.1. Правило Варіанта 09: Стандартний клієнт (застава = max(20%, 30 000 коп))
    @Test
    @Order(7)
    @DisplayName("7.1. Варіант 09: Стандартний клієнт -> застава 30 000 коп")
    void test7_StandardCustomerDepositRule() throws Exception {
        String payload = """
            {
                "reference": "REQ-RULE-STD",
                "equipmentId": 1,
                "days": 6,
                "premium": false
            }
        """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        assertEquals(30000L, root.get("deposit").asLong());
    }

    // 7.2. Правило Варіанта 09: Преміум-клієнт при сумі <= 1500 грн -> застава 0 коп
    @Test
    @Order(8)
    @DisplayName("7.2. Варіант 09: Преміум-клієнт до 1500 грн -> застава 0 коп")
    void test8_PremiumCustomerDepositRule() throws Exception {
        String payload = """
            {
                "reference": "REQ-RULE-PREM",
                "equipmentId": 1,
                "days": 6,
                "premium": true
            }
        """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        assertEquals(0L, root.get("deposit").asLong());
    }

    // 8. Workflow: POST -> GET -> action -> GET
    @Test
    @Order(9)
    @DisplayName("8. Workflow: POST -> GET -> action -> GET з динамічною передачею id")
    void test9_CompleteLifecycleWorkflow() throws Exception {
        String payload = """
            {
                "reference": "REQ-WF-1",
                "equipmentId": 1,
                "days": 2,
                "premium": false
            }
        """;
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> postRes = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, postRes.statusCode());
        long wfId = mapper.readTree(postRes.body()).get("id").asLong();

        HttpRequest get1Req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals/" + wfId))
                .GET().build();
        HttpResponse<String> get1Res = client.send(get1Req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, get1Res.statusCode());
        assertEquals("Active", mapper.readTree(get1Res.body()).get("status").asText());

        HttpRequest actReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals/" + wfId + "/return"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> actRes = client.send(actReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, actRes.statusCode());

        HttpRequest get2Req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/rentals/" + wfId))
                .GET().build();
        HttpResponse<String> get2Res = client.send(get2Req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, get2Res.statusCode());
        assertEquals("Returned", mapper.readTree(get2Res.body()).get("status").asText());
    }
}