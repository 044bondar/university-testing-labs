package ua.course.rental;

import java.util.Objects;

public class RentalService {

    public record Equipment(long id, String code, long dailyRate, int stock) {}
    public record RentalRequest(String reference, long equipmentId, int quantity, int days, boolean premium) {}
    public record RentalOrder(Long id, String reference, long equipmentId, int quantity, int days,
                              long dailyRateSnapshot, long total, long deposit, String status) {}

    public interface EquipmentRepository {
        Equipment findById(long id);
    }

    public interface RentalRepository {
        RentalOrder create(RentalOrder order);
        void markReturned(long id, long lateFee);
        RentalOrder findById(long id);
    }

    public interface RentalPolicy {
        boolean isAllowed(Equipment equipment, int quantity, int days);
    }

    public static class RentalRejectedException extends RuntimeException {
        public RentalRejectedException(String message) {
            super(message);
        }
    }

    private final EquipmentRepository equipmentRepo;
    private final RentalRepository rentalRepo;
    private final RentalPolicy policy;

    public RentalService(EquipmentRepository equipmentRepo, RentalRepository rentalRepo, RentalPolicy policy) {
        this.equipmentRepo = Objects.requireNonNull(equipmentRepo, "equipmentRepo must not be null");
        this.rentalRepo = Objects.requireNonNull(rentalRepo, "rentalRepo must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public long calculateTotal(long dailyRate, int quantity, int days) {
        if (dailyRate < 100 || dailyRate > 1_000_000) {
            throw new IllegalArgumentException("Некоректна добова ціна");
        }
        if (quantity < 1 || quantity > 20) {
            throw new IllegalArgumentException("Кількість має бути від 1 до 20");
        }
        if (days < 1 || days > 30) {
            throw new IllegalArgumentException("Термін має бути від 1 до 30 днів");
        }
        return dailyRate * quantity * days;
    }

    public long calculateDeposit(long dailyRate, int quantity, int days, boolean premium) {
        long total = calculateTotal(dailyRate, quantity, days);
        // Персональне правило Варіанта 09:
        // Преміальному клієнту при сумі до 1500 грн (150 000 коп) застава не потрібна (0).
        // Інакше — 20% суми, але не менше 300 грн (30 000 коп).
        if (premium && total <= 150_000L) {
            return 0L;
        }
        long calculated = (total * 20L) / 100L;
        return Math.max(calculated, 30_000L);
    }

    public RentalOrder rent(RentalRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Запит не може бути порожнім");
        }
        if (request.reference() == null || request.reference().isBlank() || request.reference().length() > 40) {
            throw new IllegalArgumentException("Некоректний код оренди");
        }
        if (request.equipmentId() <= 0) {
            throw new IllegalArgumentException("Ідентифікатор обладнання має бути додатним");
        }

        Equipment equipment = equipmentRepo.findById(request.equipmentId());
        if (equipment == null) {
            throw new IllegalArgumentException("Обладнання не знайдено");
        }
        if (equipment.stock() < request.quantity()) {
            throw new RentalRejectedException("Недостатній залишок на складі");
        }

        boolean allowed;
        try {
            allowed = policy.isAllowed(equipment, request.quantity(), request.days());
        } catch (Exception e) {
            throw new RentalRejectedException("Помилка перевірки політики: " + e.getMessage());
        }

        if (!allowed) {
            throw new RentalRejectedException("Оренду відхилено політикою");
        }

        long total = calculateTotal(equipment.dailyRate(), request.quantity(), request.days());
        long deposit = calculateDeposit(equipment.dailyRate(), request.quantity(), request.days(), request.premium());

        RentalOrder newOrder = new RentalOrder(
                null,
                request.reference(),
                equipment.id(),
                request.quantity(),
                request.days(),
                equipment.dailyRate(),
                total,
                deposit,
                "Active"
        );

        return rentalRepo.create(newOrder);
    }

    public void returnRental(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Некоректний id");
        }
        rentalRepo.markReturned(id, 0L);
    }
}