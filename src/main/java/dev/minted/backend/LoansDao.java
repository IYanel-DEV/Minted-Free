package dev.minted.backend;

import dev.minted.bank.Loan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQL for the loans table, always through prepared statements. A loan is a
 * plain row keyed by its own id; at most one open loan per player is enforced
 * by the service, not the schema.
 */
public final class LoansDao {

    private final HikariPool pool;
    private final SqlDialect dialect;

    public LoansDao(HikariPool pool, SqlDialect dialect) {
        this.pool = pool;
        this.dialect = dialect;
    }

    public void createTable() {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.createLoans())) {
            statement.execute();
        } catch (SQLException e) {
            throw new StorageException("Could not create loans table", e);
        }
    }

    /** Highest id currently stored, used to resume the id counter after a restart. */
    public int maxId() {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(MAX(id), 0) FROM minted_loans");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException e) {
            throw new StorageException("Could not read the loans table id", e);
        }
    }

    public void insert(int id, UUID borrower, double amount, double owed, long takenAt, long dueAt) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO minted_loans(id, borrower, amount, owed, taken_at, due_at, repaid)"
                             + " VALUES(?, ?, ?, ?, ?, ?, 0)")) {
            statement.setInt(1, id);
            statement.setString(2, borrower.toString());
            statement.setDouble(3, amount);
            statement.setDouble(4, owed);
            statement.setLong(5, takenAt);
            statement.setLong(6, dueAt);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not record a loan", e);
        }
    }

    public void repay(int id, long repaidAt) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE minted_loans SET repaid = 1 WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not mark loan repaid", e);
        }
    }

    public void setOwed(int id, double owed) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE minted_loans SET owed = ? WHERE id = ?")) {
            statement.setDouble(1, owed);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not update a loan balance", e);
        }
    }

    /** Every still-open loan, for the late-fee sweep. */
    public List<Loan> openLoans() {
        List<Loan> loans = new ArrayList<Loan>();
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, borrower, amount, owed, taken_at, due_at FROM minted_loans WHERE repaid = 0");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                loans.add(new Loan(rows.getInt(1), UUID.fromString(rows.getString(2)),
                        rows.getDouble(3), rows.getDouble(4), rows.getLong(5), rows.getLong(6)));
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load open loans", e);
        }
        return loans;
    }
}