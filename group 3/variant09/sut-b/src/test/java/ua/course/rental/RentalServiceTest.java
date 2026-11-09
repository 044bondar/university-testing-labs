package ua.course.rental;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentalServiceTest {

    @Mock
    private RentalService.EquipmentRepository equipmentRepo;

    @Mock
    private RentalService.RentalRepository rentalRepo;

    @Mock
    private RentalService.RentalPolicy policy;

    @InjectMocks
    private RentalService rentalService;

    // -------------------------------------------------------------------------
    // 1. Позитивний State-Based Test: перевірка сформованого замовлення та стану
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Позитивне оформлення оренди: створення замовлення зі статусом Active та коректними сумами")
    void rent_ValidRequest_ShouldReturnActiveOrderWithCorrectSums() {
        // Arrange
        RentalService.Equipment equipment = new RentalService.Equipment(1L, "EQ-001", 5000L, 5);
        RentalService.RentalRequest request = new RentalService.RentalRequest("REF-100", 1L, 2, 3, false);

        when(equipmentRepo.findById(1L)).thenReturn(equipment);
        when(policy.isAllowed(equipment, 2, 3)).thenReturn(true);
        when(rentalRepo.create(any(RentalService.RentalOrder.class))).thenAnswer(inv -> {
            RentalService.RentalOrder order = inv.getArgument(0);
            return new RentalService.RentalOrder(
                    99L, order.reference(), order.equipmentId(), order.quantity(),
                    order.days(), order.dailyRateSnapshot(), order.total(), order.deposit(), order.status()
            );
        });

        // Act
        RentalService.RentalOrder result = rentalService.rent(request);

        // Assert (State-Based Testing)
        assertNotNull(result);
        assertEquals(99L, result.id());
        assertEquals("Active", result.status());
        assertEquals(30_000L, result.total()); // 5000 * 2 * 3 = 30 000 коп
        assertEquals(30_000L, result.deposit()); // 20% від 30 000 = 6000 < 30 000 мінімуму
        assertEquals(5000L, result.dailyRateSnapshot());
    }

    // -------------------------------------------------------------------------
    // 2. State-Based Test: перевірка розрахунку депозиту для преміум-клієнта <= 1500 грн
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Преміум-клієнт із сумою оренди <= 1500 грн отримує нульову заставу")
    void rent_PremiumCustomerUnder1500Uah_DepositShouldBeZero() {
        // Arrange: 10 000 коп * 1 од * 10 днів = 100 000 коп (1000 грн <= 1500 грн)
        RentalService.Equipment equipment = new RentalService.Equipment(2L, "EQ-002", 10_000L, 10);
        RentalService.RentalRequest request = new RentalService.RentalRequest("REF-PREM", 2L, 1, 10, true);

        when(equipmentRepo.findById(2L)).thenReturn(equipment);
        when(policy.isAllowed(equipment, 1, 10)).thenReturn(true);
        when(rentalRepo.create(any(RentalService.RentalOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RentalService.RentalOrder result = rentalService.rent(request);

        // Assert
        assertEquals(100_000L, result.total());
        assertEquals(0L, result.deposit(), "Застава для преміум-клієнта до 1500 грн має дорівнювати 0");
    }

    // -------------------------------------------------------------------------
    // 3. Параметризований тест: перевірка персонального правила Варіанта 09 (6 наборів)
    // -------------------------------------------------------------------------
    @ParameterizedTest(name = "[{index}] rate={0}, qty={1}, days={2}, prem={3} -> expectedDeposit={4}")
    @CsvSource({
            "10000, 1, 10, true,  0",       // 1000 грн, преміум <= 1500 грн -> 0 застави
            "15000, 1, 10, true,  0",       // 1500 грн (границя), преміум -> 0 застави
            "20000, 1, 10, true,  40000",   // 2000 грн (>1500), преміум -> 20% = 400 грн (40 000 коп)
            "5000,  1, 2,  false, 30000",   // 100 грн, не преміум -> 20% = 20 грн < 300 грн -> мінімум 30 000 коп
            "50000, 1, 5,  false, 50000",   // 2500 грн, не преміум -> 20% = 500 грн (50 000 коп)
            "10000, 2, 1,  false, 30000"    // 200 грн, не преміум -> 20% = 40 грн < 300 грн -> мінімум 30 000 коп
    })
    @DisplayName("Параметризована перевірка розрахунку застави за правилом Варіанта 09")
    void calculateDeposit_ParameterizedRules(long dailyRate, int quantity, int days, boolean premium, long expectedDeposit) {
        long actualDeposit = rentalService.calculateDeposit(dailyRate, quantity, days, premium);
        assertEquals(expectedDeposit, actualDeposit);
    }

    // -------------------------------------------------------------------------
    // 4. Interaction-Based Test: перевірка викликів та аргументів через ArgumentCaptor
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Interaction: перевірка аргументів, переданих у rentalRepo.create через ArgumentCaptor")
    void rent_ValidRequest_CapturesCorrectOrderArguments() {
        // Arrange
        RentalService.Equipment equipment = new RentalService.Equipment(5L, "DRILL-01", 20_000L, 8);
        RentalService.RentalRequest request = new RentalService.RentalRequest("REF-CAPT", 5L, 2, 4, false);

        when(equipmentRepo.findById(5L)).thenReturn(equipment);
        when(policy.isAllowed(equipment, 2, 4)).thenReturn(true);

        ArgumentCaptor<RentalService.RentalOrder> captor = ArgumentCaptor.forClass(RentalService.RentalOrder.class);

        // Act
        rentalService.rent(request);

        // Assert (Interaction testing: факт виклику, кількість викликів та точні аргументи)
        verify(rentalRepo, times(1)).create(captor.capture());
        RentalService.RentalOrder capturedOrder = captor.getValue();

        assertEquals("REF-CAPT", capturedOrder.reference());
        assertEquals(5L, capturedOrder.equipmentId());
        assertEquals(2, capturedOrder.quantity());
        assertEquals(4, capturedOrder.days());
        assertEquals(160_000L, capturedOrder.total()); // 20000 * 2 * 4
        assertEquals(32_000L, capturedOrder.deposit()); // 20% від 160 000 = 32 000
    }

    // -------------------------------------------------------------------------
    // 5. Interaction-Based Test: перевірка повернення та відсутності зайвих викликів
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Interaction: returnRental викликає markReturned(id, 0) і не зачіпає інші залежності")
    void returnRental_ValidId_CallsMarkReturnedWithoutExternalInteractions() {
        // Act
        rentalService.returnRental(42L);

        // Assert
        verify(rentalRepo, times(1)).markReturned(42L, 0L);
        verifyNoInteractions(equipmentRepo);
        verifyNoInteractions(policy);
    }

    // -------------------------------------------------------------------------
    // 6. Негативний бізнес-сценарій: відмова політики (isAllowed == false)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Негативний сценарій: якщо RentalPolicy повертає false, замовлення НЕ зберігається")
    void rent_PolicyDisallows_ThrowsExceptionAndNeverSavesOrder() {
        // Arrange
        RentalService.Equipment equipment = new RentalService.Equipment(3L, "EQ-003", 5000L, 5);
        RentalService.RentalRequest request = new RentalService.RentalRequest("REF-REJ", 3L, 2, 3, false);

        when(equipmentRepo.findById(3L)).thenReturn(equipment);
        when(policy.isAllowed(equipment, 2, 3)).thenReturn(false);

        // Act & Assert
        RentalService.RentalRejectedException ex = assertThrows(
                RentalService.RentalRejectedException.class,
                () -> rentalService.rent(request)
        );

        assertTrue(ex.getMessage().contains("відхилено політикою"));
        verify(rentalRepo, never()).create(any()); // Перевірка відсутності запису в БД
    }

    // -------------------------------------------------------------------------
    // 7. Негативний бізнес-сценарій: недостатній залишок на складі
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Негативний сценарій: запит кількості більшої за залишок викликає виняток і не опитує політику")
    void rent_InsufficientStock_ThrowsExceptionWithoutCallingPolicy() {
        // Arrange
        RentalService.Equipment equipment = new RentalService.Equipment(4L, "EQ-004", 5000L, 2);
        RentalService.RentalRequest request = new RentalService.RentalRequest("REF-STOCK", 4L, 5, 3, false);

        when(equipmentRepo.findById(4L)).thenReturn(equipment);

        // Act & Assert
        assertThrows(RentalService.RentalRejectedException.class, () -> rentalService.rent(request));

        verify(policy, never()).isAllowed(any(), anyInt(), anyInt());
        verifyNoInteractions(rentalRepo);
    }

    // -------------------------------------------------------------------------
    // 8. Винятковий сценарій: залежність (політика) генерує виняток
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Винятковий сценарій: коли RentalPolicy кидає RuntimeException, сервіс перехоплює і не зберігає")
    void rent_PolicyThrowsException_HandledAndNoOrderCreated() {
        // Arrange
        RentalService.Equipment equipment = new RentalService.Equipment(1L, "EQ-001", 5000L, 10);
        RentalService.RentalRequest request = new RentalService.RentalRequest("REF-ERR", 1L, 1, 1, false);

        when(equipmentRepo.findById(1L)).thenReturn(equipment);
        when(policy.isAllowed(any(), anyInt(), anyInt())).thenThrow(new RuntimeException("External service network error"));

        // Act & Assert
        RentalService.RentalRejectedException ex = assertThrows(
                RentalService.RentalRejectedException.class,
                () -> rentalService.rent(request)
        );

        assertTrue(ex.getMessage().contains("Помилка перевірки політики"));
        verify(rentalRepo, never()).create(any());
    }

    // -------------------------------------------------------------------------
    // 9. Перевірка некоректних вхідних даних (Validation)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Валідація: запит з null викликає IllegalArgumentException без виклику моків")
    void rent_NullRequest_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> rentalService.rent(null));
        verifyNoInteractions(equipmentRepo, rentalRepo, policy);
    }

    // -------------------------------------------------------------------------
    // 10. Граничні значення тривалості оренди
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Граничні значення: тривалість 0 або 31 день викликає помилку")
    void calculateTotal_InvalidDays_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> rentalService.calculateTotal(5000L, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> rentalService.calculateTotal(5000L, 1, 31));
    }
}