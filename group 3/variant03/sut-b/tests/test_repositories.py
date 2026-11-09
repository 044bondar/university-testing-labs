import pytest
from rental.database import connect
from rental.models import Equipment, RentalOrder
from rental.repositories import EquipmentRepository, RentalRepository


@pytest.fixture(autouse=True)
def clean_database():
    """Очищення таблиць перед кожним тестом для ізоляції."""
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM rental_items;")
            cur.execute("DELETE FROM rentals;")
            cur.execute("DELETE FROM equipment;")
        conn.commit()


# 1-й обов'язковий тест: збереження сутності
def test_save_equipment_persists_data_repositoryState():
    with connect() as conn:
        repo = EquipmentRepository(conn)
        eq = Equipment(id=None, code="EQ-REP-01", daily_rate=50000, stock=10)
        created = repo.create(eq)

        assert created.id is not None

        # Перевірка фактичного стану БД через повторне звернення
        persisted = repo.find_by_id(created.id)
        assert persisted is not None
        assert persisted.code == "EQ-REP-01"
        assert persisted.daily_rate == 50000
        assert persisted.stock == 10


# 2-й обов'язковий тест: повторне отримання даних
def test_find_by_id_returns_persisted_rental_repositoryState():
    with connect() as conn:
        eq_repo = EquipmentRepository(conn)
        rental_repo = RentalRepository(conn)

        eq = eq_repo.create(
            Equipment(id=None, code="EQ-REP-02", daily_rate=10000, stock=5)
        )
        rental = rental_repo.create(
            RentalOrder(
                id=None,
                reference="RNT-REP-01",
                equipment_id=eq.id,
                quantity=2,
                days=3,
                total=60000,
                deposit=30000,
                status="Active",
            )
        )

        persisted = rental_repo.find_by_id(rental.id)
        assert persisted is not None
        assert persisted.reference == "RNT-REP-01"
        assert persisted.status == "Active"


def test_mark_returned_modifies_row_in_database():
    with connect() as conn:
        eq_repo = EquipmentRepository(conn)
        rental_repo = RentalRepository(conn)

        eq = eq_repo.create(
            Equipment(id=None, code="EQ-REP-03", daily_rate=20000, stock=5)
        )
        rental = rental_repo.create(
            RentalOrder(
                id=None,
                reference="RNT-REP-02",
                equipment_id=eq.id,
                quantity=1,
                days=2,
                total=40000,
                deposit=0,
                status="Active",
            )
        )

        rental_repo.mark_returned(rental.id, late_fee=0)

        updated = rental_repo.find_by_id(rental.id)
        assert updated is not None
        assert updated.status == "Returned"