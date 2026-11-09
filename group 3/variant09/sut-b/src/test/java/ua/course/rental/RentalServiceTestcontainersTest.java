package ua.course.rental;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RentalServiceTestcontainersTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withStartupTimeout(Duration.ofMinutes(3));

    private static WireMockServer wireMockServer;
    private Connection connection;
    private EquipmentRepository equipmentRepo;
    private RentalRepository rentalRepo;
    private RentalService rentalService;

    @BeforeAll
    static void startWireMockAndInitDb() throws Exception {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        try (Connection initConn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = initConn.createStatement()) {

            executeSqlResource(stmt, "/migrations/V1__create_tables.sql");
            executeSqlResource(stmt, "/migrations/V2__add_deposit.sql");
        }
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        wireMockServer.resetAll();

        connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("TRUNCATE TABLE rental_items CASCADE;");
            stmt.execute("TRUNCATE TABLE rentals CASCADE;");
            stmt.execute("TRUNCATE TABLE equipment CASCADE;");
        }

        equipmentRepo = new EquipmentRepository(connection);
        rentalRepo = new RentalRepository(connection);

        RentalService.EquipmentRepository serviceEquipRepo = id -> {
            try {
                Equipment e = equipmentRepo.findById(id);
                return e == null ? null : new RentalService.Equipment(e.id(), e.code(), e.dailyRate(), e.stock());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };

        RentalService.RentalRepository serviceRentalRepo = new RentalService.RentalRepository() {
            @Override
            public RentalService.RentalOrder create(RentalService.RentalOrder order) {
                try {
                    RentalItem item = new RentalItem(0L, order.equipmentId(), order.quantity(), order.dailyRateSnapshot());
                    RentalOrder domainOrder = new RentalOrder(
                            0L,
                            order.reference(),
                            order.days(),
                            false,
                            order.total(),
                            0L,
                            order.deposit(),
                            RentalStatus.valueOf(order.status()),
                            item
                    );
                    RentalOrder saved = rentalRepo.create(domainOrder);
                    return new RentalService.RentalOrder(
                            saved.id(),
                            saved.reference(),
                            saved.item().equipmentId(),
                            saved.item().quantity(),
                            saved.days(),
                            saved.item().dailyRate(),
                            saved.total(),
                            saved.deposit(),
                            saved.status().name()
                    );
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public void markReturned(long id, long lateFee) {
                try {
                    rentalRepo.markReturned(id, lateFee);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public RentalService.RentalOrder findById(long id) {
                try {
                    RentalOrder o = rentalRepo.findById(id);
                    if (o == null) return null;
                    return new RentalService.RentalOrder(
                            o.id(),
                            o.reference(),
                            o.item().equipmentId(),
                            o.item().quantity(),
                            o.days(),
                            o.item().dailyRate(),
                            o.total(),
                            o.deposit(),
                            o.status().name()
                    );
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };

        URI baseUri = URI.create("http://localhost:" + wireMockServer.port() + "/");
        HttpRentalPolicy realHttpPolicy = new HttpRentalPolicy(baseUri, Duration.ofSeconds(5));

        RentalService.RentalPolicy servicePolicy = (equipment, quantity, days) -> {
            Equipment domainEq = new Equipment(equipment.id(), equipment.code(), equipment.dailyRate(), equipment.stock());
            return realHttpPolicy.isAllowed(domainEq, quantity, days);
        };

        rentalService = new RentalService(serviceEquipRepo, serviceRentalRepo, servicePolicy);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private static void executeSqlResource(Statement stmt, String resourcePath) throws Exception {
        try (InputStream in = RentalServiceTestcontainersTest.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                stmt.execute(sql);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Сценарій 1: Позитивний бізнес-сценарій (п. 3.5 методички)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("1. Позитивний: WireMock 200 OK -> збереження в PostgreSQL та перевірка застави Варіанта 09")
    void rent_Success_PersistsInPostgresAndCallsWiremock() throws Exception {
        Equipment createdEq = equipmentRepo.create(new Equipment(0L, "GEN-01", 10_000L, 5));

        stubFor(post(urlEqualTo("/rental-policy"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"allowed\": true}")));

        RentalService.RentalRequest request = new RentalService.RentalRequest("REQ-OK-01", createdEq.id(), 2, 3, false);

        RentalService.RentalOrder order = rentalService.rent(request);

        assertNotNull(order.id());
        assertTrue(order.id() > 0);

        RentalOrder inDb = rentalRepo.findById(order.id());
        assertNotNull(inDb, "Запис повинен існувати в реальній PostgreSQL");
        assertEquals(RentalStatus.Active, inDb.status());
        assertEquals(60_000L, inDb.total());
        assertEquals(30_000L, inDb.deposit());

        verify(1, postRequestedFor(urlEqualTo("/rental-policy")));
    }

    // -------------------------------------------------------------------------
    // Сценарій 2: Зовнішня бізнес-відмова (п. 3.6 методички)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("2. Зовнішня відмова: WireMock allowed=false -> запис у БД відсутній, HTTP-запит виконано")
    void rent_ExternalRejection_ThrowsExceptionAndNoRecordInDb() throws Exception {
        Equipment createdEq = equipmentRepo.create(new Equipment(0L, "SAW-02", 5000L, 3));

        stubFor(post(urlEqualTo("/rental-policy"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"allowed\": false}")));

        RentalService.RentalRequest request = new RentalService.RentalRequest("REQ-REJ-EXT", createdEq.id(), 1, 2, false);

        assertThrows(RentalService.RentalRejectedException.class, () -> rentalService.rent(request));

        assertEquals(0, rentalRepo.findRentals(createdEq.id(), RentalStatus.Active).size());
        verify(1, postRequestedFor(urlEqualTo("/rental-policy")));
    }

    // -------------------------------------------------------------------------
    // Сценарій 3: Недоступність зовнішнього HTTP-сервісу (п. 3.7 методички)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("3. Недоступність сервісу: WireMock 503 -> операція переривається, БД чиста")
    void rent_ExternalServiceUnavailable_ThrowsExceptionAndRollbacks() throws Exception {
        Equipment createdEq = equipmentRepo.create(new Equipment(0L, "PUMP-03", 8000L, 2));

        stubFor(post(urlEqualTo("/rental-policy"))
                .willReturn(aResponse()
                        .withStatus(503)));

        RentalService.RentalRequest request = new RentalService.RentalRequest("REQ-ERR-503", createdEq.id(), 1, 1, false);

        assertThrows(RentalService.RentalRejectedException.class, () -> rentalService.rent(request));

        assertEquals(0, rentalRepo.findRentals(createdEq.id(), RentalStatus.Active).size());
        verify(1, postRequestedFor(urlEqualTo("/rental-policy")));
    }

    // -------------------------------------------------------------------------
    // Сценарій 4: Локальна бізнес-відмова (п. 3.8 методички)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("4. Локальна відмова: перевищення залишку -> помилка ДО мережі, WireMock НЕ викликався (HTTP = 0)")
    void rent_LocalRejection_StockExceeded_NeverCallsWiremock() throws Exception {
        Equipment createdEq = equipmentRepo.create(new Equipment(0L, "DRILL-04", 4000L, 1));
        RentalService.RentalRequest request = new RentalService.RentalRequest("REQ-LOCAL-REJ", createdEq.id(), 3, 1, false);

        assertThrows(RentalService.RentalRejectedException.class, () -> rentalService.rent(request));

        assertEquals(0, rentalRepo.findRentals(createdEq.id(), RentalStatus.Active).size());
        verify(0, postRequestedFor(urlEqualTo("/rental-policy")));
    }
}