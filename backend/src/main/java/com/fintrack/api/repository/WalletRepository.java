package com.fintrack.api.repository;

import com.fintrack.api.dto.response.wallet.WalletResponse;
import com.fintrack.api.service.finance.FinanceSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class WalletRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = "SELECT w.*, b.balance_minor FROM fintrack.wallets w "
            + "JOIN fintrack.wallet_balances b ON b.user_id=w.user_id AND b.wallet_id=w.id ";
    public WalletRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<WalletResponse> list(UUID user, String status) {
        String filter = switch(status) { case "ACTIVE" -> " AND w.archived_at IS NULL";
            case "ARCHIVED" -> " AND w.archived_at IS NOT NULL"; default -> ""; };
        return jdbc.query(SELECT+"WHERE w.user_id=?"+filter+" ORDER BY w.sort_order,w.name,w.id", this::map,user);
    }
    public WalletResponse get(UUID user, UUID id) {
        return jdbc.query(SELECT+"WHERE w.user_id=? AND w.id=?",this::map,user,id)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }
    public void create(UUID user,UUID id,String name,String type,long opening,boolean assets,int sort) {
        jdbc.update("INSERT INTO fintrack.wallets(id,user_id,name,wallet_type,opening_balance_minor,include_in_assets,sort_order) "
                + "VALUES (?,?,?,?,?,?,?)",id,user,name,type,opening,assets,sort);
    }
    public void update(UUID user,UUID id,String name,String type,long opening,boolean assets,int sort) {
        jdbc.update("UPDATE fintrack.wallets SET name=?,wallet_type=?,opening_balance_minor=?,include_in_assets=?,sort_order=?,updated_at=now(),version=version+1 WHERE user_id=? AND id=?",
                name,type,opening,assets,sort,user,id);
    }
    public void status(UUID user,UUID id,boolean active) {
        jdbc.update("UPDATE fintrack.wallets SET archived_at="+(active?"NULL":"now()")
                +",updated_at=now(),version=version+1 WHERE user_id=? AND id=? AND archived_at IS "
                +(active?"NOT NULL":"NULL"),user,id);
    }
    private WalletResponse map(ResultSet rs,int row) throws SQLException {
        return new WalletResponse(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("wallet_type"),
                rs.getString("currency_code"),rs.getLong("opening_balance_minor"),FinanceSupport.exact(rs.getBigDecimal("balance_minor")),
                rs.getBoolean("include_in_assets"),rs.getInt("sort_order"),rs.getTimestamp("archived_at")==null?null:rs.getTimestamp("archived_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
    }
}
