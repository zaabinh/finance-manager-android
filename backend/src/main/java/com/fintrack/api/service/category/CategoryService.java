package com.fintrack.api.service.category;

import com.fintrack.api.dto.request.category.CategoryRequest;
import com.fintrack.api.dto.request.category.CategoryStatusRequest;
import com.fintrack.api.dto.response.category.CategoryResponse;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.CategoryRepository;
import com.fintrack.api.service.finance.FinanceSupport;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
public class CategoryService {
    /** Icons the Android app can render; must equal CategoryIcons.KEYS on Android. */
    public static final List<String> ICON_KEYS = List.of("coffee","car","ticket","shopping_bag","receipt","heart",
            "briefcase","gift","school","more_horiz","image_placeholder");
    private final CategoryRepository categories;
    private final CategorySeedService seeds;
    public CategoryService(CategoryRepository categories, CategorySeedService seeds) { this.categories=categories; this.seeds=seeds; }
    @Transactional(readOnly=true)
    public List<CategoryResponse> list(UUID owner,String status,String type) {
        String lifecycle=FinanceSupport.lifecycle(status);
        if (type!=null && !List.of("INCOME","EXPENSE").contains(type)) throw FinanceSupport.badRequest("categoryType không hợp lệ");
        return categories.list(owner,lifecycle,type);
    }
    @Transactional(readOnly=true)
    public CategoryResponse detail(UUID owner, UUID id) { return categories.find(owner,id,false); }
    @Transactional
    public CategoryResponse create(UUID owner, CategoryRequest body) {
        Values v=validate(body);
        seeds.initialize(owner);
        try { return categories.create(owner,v.name,v.type,v.icon,v.color,v.sort); }
        catch (DuplicateKeyException exception) { throw nameConflict(); }
    }
    @Transactional
    public CategoryResponse update(UUID owner, UUID id, CategoryRequest body) {
        CategoryResponse current=categories.find(owner,id,true);
        if(current.archivedAt()!=null) throw FinanceSupport.conflict(ErrorCode.CATEGORY_ARCHIVED,"Khôi phục danh mục trước khi chỉnh sửa");
        Values v=validate(body);
        if(!current.categoryType().equals(v.type) && categories.referenced(owner,id))
            throw FinanceSupport.conflict(ErrorCode.CATEGORY_TYPE_IN_USE,"Danh mục đã có giao dịch hoặc ngân sách, không thể đổi loại");
        try { categories.update(owner,id,v.name,v.type,v.icon,v.color,v.sort); return categories.find(owner,id,false); }
        catch (DuplicateKeyException exception) { throw nameConflict(); }
    }
    @Transactional
    public CategoryResponse status(UUID owner, UUID id, CategoryStatusRequest body) {
        if(body.active==null) throw FinanceSupport.invalid("active là bắt buộc");
        CategoryResponse current=categories.find(owner,id,true);
        if((current.archivedAt()==null)==body.active) return current;
        try { categories.status(owner,id,body.active); return categories.find(owner,id,false); }
        catch(DuplicateKeyException exception) { throw nameConflict(); }
    }
    @Transactional
    public void archive(UUID owner,UUID id) {
        if(categories.find(owner,id,true).archivedAt()==null) categories.status(owner,id,false);
    }
    private static Values validate(CategoryRequest r) {
        String name=FinanceSupport.name(r.name), type=FinanceSupport.type(r.categoryType,"EXPENSE","INCOME");
        if(r.iconKey!=null && !ICON_KEYS.contains(r.iconKey))
            throw FinanceSupport.invalid("iconKey không nằm trong danh sách icon được hỗ trợ");
        if(r.colorHex!=null && !r.colorHex.matches("#[0-9a-fA-F]{6}")) throw FinanceSupport.invalid("colorHex phải có định dạng #RRGGBB");
        return new Values(name,type,r.iconKey,r.colorHex==null?null:r.colorHex.toUpperCase(Locale.ROOT),FinanceSupport.sort(r.sortOrder));
    }
    private static RuntimeException nameConflict() { return FinanceSupport.conflict(ErrorCode.CATEGORY_NAME_CONFLICT,"Tên danh mục đã tồn tại trong cùng loại"); }
    private record Values(String name,String type,String icon,String color,int sort) {}
}
