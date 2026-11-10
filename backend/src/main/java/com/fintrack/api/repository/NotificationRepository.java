package com.fintrack.api.repository;

import com.fintrack.api.dto.response.notification.NotificationResponse;
import com.fintrack.api.service.finance.FinanceSupport;
import com.fintrack.api.service.notification.NotificationJson;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class NotificationRepository {
    private final JdbcTemplate jdbc;
    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void lockInbox(UUID user) { jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 114))", Object.class, user.toString()); }
    public Instant now() { return jdbc.queryForObject("SELECT statement_timestamp()", Timestamp.class).toInstant(); }
    public long unread(UUID user) { return jdbc.queryForObject("SELECT count(*) FROM fintrack.notifications WHERE user_id=? AND deleted_at IS NULL AND read_at IS NULL", Long.class, user); }
    public long count(UUID user, String status) { return jdbc.queryForObject("SELECT count(*) FROM fintrack.notifications WHERE user_id=? AND deleted_at IS NULL" + filter(status), Long.class, user); }
    public List<NotificationResponse> list(UUID user, int page, int size, String status) {
        return jdbc.query("SELECT * FROM fintrack.notifications WHERE user_id=? AND deleted_at IS NULL" + filter(status)
            + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", NotificationRepository::response, user, size, (long)page*size);
    }
    public NotificationResponse detail(UUID user, UUID id) {
        return jdbc.query("SELECT * FROM fintrack.notifications WHERE user_id=? AND id=? AND deleted_at IS NULL", NotificationRepository::response, user,id)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }
    public void read(UUID user, UUID id) {
        if (jdbc.update("UPDATE fintrack.notifications SET read_at=COALESCE(read_at,statement_timestamp()) WHERE user_id=? AND id=? AND deleted_at IS NULL", user,id)==0)
            throw FinanceSupport.notFound();
    }
    public int readAll(UUID user, Instant through) { return jdbc.update("UPDATE fintrack.notifications SET read_at=statement_timestamp() WHERE user_id=? AND deleted_at IS NULL AND read_at IS NULL AND created_at<=?",user,Timestamp.from(through)); }
    public void delete(UUID user, UUID id) {
        if (jdbc.update("UPDATE fintrack.notifications SET deleted_at=COALESCE(deleted_at,statement_timestamp()) WHERE user_id=? AND id=?",user,id)==0) throw FinanceSupport.notFound();
        jdbc.update("UPDATE fintrack.notification_outbox SET state='CANCELLED',reason='ITEM_DELETED',claim_token=NULL WHERE user_id=? AND notification_id=? AND state IN ('PENDING','RETRY','IN_FLIGHT')",user,id);
    }
    public UUID insert(UUID user, String eventKey, String type, String title, String body, Long amount, Map<String,Object> payload) {
        lockInbox(user);
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("INSERT INTO fintrack.notifications(id,user_id,event_key,type,title,body,amount_minor,payload) VALUES(?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(user_id,event_key) DO NOTHING",
                id,user,eventKey,type,title,body,amount,NotificationJson.write(payload));
        return inserted==0 ? null : id;
    }
    private static String filter(String status) { return switch(status) { case "READ" -> " AND read_at IS NOT NULL"; case "UNREAD" -> " AND read_at IS NULL"; default -> ""; }; }
    @SuppressWarnings("unchecked")
    private static NotificationResponse response(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var read = rs.getTimestamp("read_at");
        return new NotificationResponse(rs.getObject("id",UUID.class),rs.getString("type"),rs.getString("title"),rs.getString("body"),rs.getTimestamp("created_at").toInstant(),read==null?null:read.toInstant(),
                (Long)rs.getObject("amount_minor"),(Map<String,Object>)NotificationJson.read(rs.getString("payload")).get("target"));
    }
}
