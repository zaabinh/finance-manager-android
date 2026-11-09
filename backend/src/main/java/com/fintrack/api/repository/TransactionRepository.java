package com.fintrack.api.repository;

import com.fintrack.api.dto.response.transaction.TransactionResponse;
import com.fintrack.api.service.finance.FinanceSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class TransactionRepository {
    private final JdbcTemplate jdbc;
    public TransactionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Validated business fields of an income/expense/transfer; destinationWalletId is set only for TRANSFER. */
    public record Fields(String type, long amount, UUID categoryId, UUID walletId, Instant occurredAt, String note, String payee,
            UUID destinationWalletId) {
        public Fields(String type, long amount, UUID categoryId, UUID walletId, Instant occurredAt, String note, String payee) {
            this(type, amount, categoryId, walletId, occurredAt, note, payee, null);
        }
        /** Contribution of the source/single wallet. */
        public long delta() { return TransactionRepository.delta(type, amount); }
        public Map<UUID, Long> deltas() { return TransactionRepository.deltas(type, amount, walletId, destinationWalletId); }
    }
    /** Locked ledger state of one transaction; walletId is the source of a TRANSFER. */
    public record Row(UUID id, String type, long amount, UUID walletId, boolean posted, UUID destinationWalletId) {
        public Row(UUID id, String type, long amount, UUID walletId, boolean posted) { this(id, type, amount, walletId, posted, null); }
        public long delta() { return TransactionRepository.delta(type, amount); }
        public Map<UUID, Long> deltas() { return TransactionRepository.deltas(type, amount, walletId, destinationWalletId); }
    }
    /** Signed contribution of the source/single wallet: income +amount, expense/transfer -amount. */
    public static long delta(String type, long amount) { return "INCOME".equals(type) ? amount : -amount; }
    /** Every entry of a transaction: a transfer is -amount on the source and +amount on the destination. */
    public static Map<UUID, Long> deltas(String type, long amount, UUID wallet, UUID destination) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        result.put(wallet, delta(type, amount));
        if ("TRANSFER".equals(type)) result.put(destination, amount);
        return result;
    }
    public record Replay(UUID id, String fingerprint) {}
    public record Page(List<TransactionResponse> items, long total) {}

    private static final String COLUMNS = "t.id, t.transaction_type, t.amount_minor, t.category_id, e.wallet_id, "
            + "d.wallet_id AS destination_wallet_id, t.occurred_at, "
            + "t.note, t.payee, t.client_request_id, t.status, t.voided_at, t.created_at, t.updated_at";
    // e = the single income/expense entry or the negative (source) transfer entry; d = the positive transfer entry.
    // The ledger trigger allows one entry per income/expense and exactly one of each sign per transfer, so rows stay 1:1.
    private static final String FROM = " FROM fintrack.transactions t JOIN fintrack.transaction_entries e "
            + "ON e.user_id = t.user_id AND e.transaction_id = t.id AND (t.transaction_type <> 'TRANSFER' OR e.delta_minor < 0) "
            + "LEFT JOIN fintrack.transaction_entries d ON d.user_id = t.user_id AND d.transaction_id = t.id "
            + "AND t.transaction_type = 'TRANSFER' AND d.delta_minor > 0 "
            + "WHERE t.user_id = ? AND t.transaction_type IN ('EXPENSE', 'INCOME', 'TRANSFER')";

    public TransactionResponse detail(UUID user, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " AND t.id = ?", TransactionRepository::map, user, id)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }

    public Optional<Replay> byRequest(UUID user, UUID clientRequestId) {
        return jdbc.query("SELECT id, request_fingerprint FROM fintrack.transactions WHERE user_id = ? AND client_request_id = ?",
                (rs, row) -> new Replay(rs.getObject("id", UUID.class), rs.getString("request_fingerprint")),
                user, clientRequestId).stream().findFirst();
    }

    /** Locks the transaction row alone, then reads its entries in a fresh statement so a concurrent edit is seen. */
    public Row lock(UUID user, UUID id) {
        Row row = jdbc.query("SELECT id, transaction_type, amount_minor, status FROM fintrack.transactions "
                        + "WHERE user_id = ? AND id = ? AND transaction_type IN ('EXPENSE', 'INCOME', 'TRANSFER') FOR UPDATE",
                (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("transaction_type"),
                        rs.getLong("amount_minor"), null, "POSTED".equals(rs.getString("status"))), user, id)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
        // Ordered by sign: the first entry is the transfer source or the single income/expense entry.
        List<UUID> wallets = jdbc.queryForList("SELECT wallet_id FROM fintrack.transaction_entries "
                + "WHERE user_id = ? AND transaction_id = ? ORDER BY delta_minor", UUID.class, user, id);
        UUID destination = "TRANSFER".equals(row.type()) && wallets.size() > 1 ? wallets.get(1) : null;
        return new Row(row.id(), row.type(), row.amount(), wallets.get(0), row.posted(), destination);
    }

    /** Returns false when another request already owns this idempotency key; the DB transaction stays usable. */
    public boolean create(UUID user, UUID id, Fields f, UUID clientRequestId, String fingerprint) {
        int inserted = jdbc.update("INSERT INTO fintrack.transactions(id, user_id, transaction_type, amount_minor, category_id, "
                        + "occurred_at, note, payee, client_request_id, request_fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (user_id, client_request_id) WHERE client_request_id IS NOT NULL DO NOTHING",
                id, user, f.type(), f.amount(), f.categoryId(), utc(f.occurredAt()), f.note(), f.payee(), clientRequestId, fingerprint);
        if (inserted == 0) return false;
        insertEntries(user, id, f);
        return true;
    }

    private void insertEntries(UUID user, UUID id, Fields f) {
        for (var entry : f.deltas().entrySet())
            jdbc.update("INSERT INTO fintrack.transaction_entries(user_id, transaction_id, wallet_id, delta_minor) VALUES (?, ?, ?, ?)",
                    user, id, entry.getKey(), entry.getValue());
    }

    /**
     * Rewrites business fields and replaces the entries (their count differs between transfer and income/expense);
     * the deferred ledger trigger validates the final set at commit. client_request_id and request_fingerprint are never touched.
     */
    public void update(UUID user, UUID id, Fields f) {
        jdbc.update("UPDATE fintrack.transactions SET transaction_type = ?, amount_minor = ?, category_id = ?, occurred_at = ?, "
                + "note = ?, payee = ?, updated_at = now() WHERE user_id = ? AND id = ?",
                f.type(), f.amount(), f.categoryId(), utc(f.occurredAt()), f.note(), f.payee(), user, id);
        jdbc.update("DELETE FROM fintrack.transaction_entries WHERE user_id = ? AND transaction_id = ?", user, id);
        insertEntries(user, id, f);
    }

    /** Void keeps entries; wallet_balances drops VOIDED contributions, so nothing is reversed twice. */
    public void voidPosted(UUID user, UUID id) {
        jdbc.update("UPDATE fintrack.transactions SET status = 'VOIDED', voided_at = now(), updated_at = now() "
                + "WHERE user_id = ? AND id = ? AND status = 'POSTED'", user, id);
    }

    /** Count and page come from one statement, hence one snapshot. sort must already be allowlisted. */
    public Page page(UUID user, Instant from, Instant to, UUID wallet, UUID category, String type, String q,
            String sort, int size, long offset) {
        StringBuilder where = new StringBuilder(" AND t.status = 'POSTED'");
        List<Object> args = new ArrayList<>(); args.add(user);
        if (from != null) { where.append(" AND t.occurred_at >= ?"); args.add(utc(from)); }
        if (to != null) { where.append(" AND t.occurred_at < ?"); args.add(utc(to)); }
        // A transfer matches its source or its destination wallet.
        if (wallet != null) { where.append(" AND (e.wallet_id = ? OR d.wallet_id = ?)"); args.add(wallet); args.add(wallet); }
        if (category != null) { where.append(" AND t.category_id = ?"); args.add(category); }
        if (type != null) { where.append(" AND t.transaction_type = ?"); args.add(type); }
        if (q != null) {
            String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (t.note ILIKE ? ESCAPE '\\' OR t.payee ILIKE ? ESCAPE '\\')");
            args.add(pattern); args.add(pattern);
        }
        String order = switch (sort) {
            case "occurredAt,asc" -> "occurred_at ASC, id ASC";
            case "amountMinor,desc" -> "amount_minor DESC, id DESC";
            case "amountMinor,asc" -> "amount_minor ASC, id ASC";
            default -> "occurred_at DESC, id DESC";
        };
        args.add(size); args.add(offset);
        String sql = "WITH f AS (SELECT " + COLUMNS + FROM + where + ") "
                + "SELECT c.total, p.* FROM (SELECT count(*) AS total FROM f) c "
                + "LEFT JOIN LATERAL (SELECT * FROM f ORDER BY " + order + " LIMIT ? OFFSET ?) p ON true ORDER BY " + order;
        return jdbc.query(sql, rs -> {
            long total = 0; List<TransactionResponse> items = new ArrayList<>();
            while (rs.next()) {
                total = rs.getLong("total");
                if (rs.getObject("id") != null) items.add(map(rs, items.size()));
            }
            return new Page(items, total);
        }, args.toArray());
    }

    private static OffsetDateTime utc(Instant value) { return value.atOffset(ZoneOffset.UTC); }

    private static TransactionResponse map(ResultSet rs, int row) throws SQLException {
        return new TransactionResponse(rs.getObject("id", UUID.class), rs.getString("transaction_type"), rs.getLong("amount_minor"),
                rs.getObject("category_id", UUID.class), rs.getObject("wallet_id", UUID.class),
                rs.getObject("destination_wallet_id", UUID.class), instant(rs, "occurred_at"),
                text(rs.getString("note")), text(rs.getString("payee")), rs.getObject("client_request_id", UUID.class),
                rs.getString("status"), instant(rs, "voided_at"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }
    private static String text(String value) { return value == null ? "" : value; }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
