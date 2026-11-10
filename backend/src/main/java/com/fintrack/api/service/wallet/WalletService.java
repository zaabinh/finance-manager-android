package com.fintrack.api.service.wallet;

import com.fintrack.api.dto.request.wallet.WalletRequest;
import com.fintrack.api.dto.request.wallet.WalletStatusRequest;
import com.fintrack.api.dto.response.wallet.WalletResponse;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.WalletRepository;
import com.fintrack.api.service.finance.FinanceSupport;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
public class WalletService {
    private final WalletRepository wallets;
    private final FinanceSupport finance;
    public WalletService(WalletRepository wallets,FinanceSupport finance) {this.wallets=wallets;this.finance=finance;}
    @Transactional(readOnly=true) public List<WalletResponse> list(UUID user,String status) {
        return wallets.list(user,FinanceSupport.lifecycle(status));
    }
    @Transactional(readOnly=true) public WalletResponse get(UUID user,UUID id) {return wallets.get(user,id);}
    @Transactional public WalletResponse create(UUID user,WalletRequest body) {
        String name=FinanceSupport.name(body.name), type=FinanceSupport.type(body.walletType,"CASH","BANK","SAVINGS");
        long opening=FinanceSupport.money(body.openingBalanceMinor==null?0L:body.openingBalanceMinor,true);
        int sort=FinanceSupport.sort(body.sortOrder); UUID id=UUID.randomUUID();
        try {wallets.create(user,id,name,type,opening,body.includeInAssets==null||body.includeInAssets,sort);}
        catch(DuplicateKeyException error) {throw duplicate();}
        return wallets.get(user,id);
    }
    @Transactional public WalletResponse update(UUID user,UUID id,WalletRequest body) {
        FinanceSupport.WalletRef wallet=finance.wallet(user,id,true);
        if(!wallet.active()) throw FinanceSupport.conflict(ErrorCode.WALLET_ARCHIVED,"Reactivate wallet before editing");
        String name=FinanceSupport.name(body.name), type=FinanceSupport.type(body.walletType,"CASH","BANK","SAVINGS");
        long opening=FinanceSupport.money(body.openingBalanceMinor,true); int sort=FinanceSupport.sort(body.sortOrder);
        BigInteger candidate=BigInteger.valueOf(finance.balance(user,id)).subtract(BigInteger.valueOf(wallet.openingBalanceMinor())).add(BigInteger.valueOf(opening));
        if(candidate.signum()<0) throw FinanceSupport.conflict(ErrorCode.WALLET_INSUFFICIENT_FUNDS,"Opening balance would make wallet negative");
        FinanceSupport.exact(new java.math.BigDecimal(candidate));
        try {wallets.update(user,id,name,type,opening,body.includeInAssets==null||body.includeInAssets,sort);}
        catch(DuplicateKeyException error) {throw duplicate();}
        return wallets.get(user,id);
    }
    @Transactional public WalletResponse status(UUID user,UUID id,WalletStatusRequest body) {
        if(body.active==null) throw FinanceSupport.invalid("active is required");
        finance.wallet(user,id,true);
        try {wallets.status(user,id,body.active);}
        catch(DuplicateKeyException error) {throw duplicate();}
        return wallets.get(user,id);
    }
    @Transactional public void archive(UUID user,UUID id) {
        finance.wallet(user,id,true);wallets.status(user,id,false);
    }
    private static com.fintrack.api.exception.AppException duplicate() {
        return FinanceSupport.conflict(ErrorCode.WALLET_NAME_CONFLICT,"An active wallet already has this name");
    }
}
