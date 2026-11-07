package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.dto.request.category.CategoryRequest;
import com.fintrack.api.dto.request.category.CategoryStatusRequest;
import com.fintrack.api.dto.response.category.CategoryResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.CategoryRepository;
import com.fintrack.api.service.category.CategorySeedService;
import com.fintrack.api.service.category.CategoryService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class CategoryServiceTest {
    private final UUID owner=UUID.randomUUID(), id=UUID.randomUUID();
    private final CategoryRepository repo=mock(CategoryRepository.class);
    private final CategorySeedService seeds=mock(CategorySeedService.class);
    private final CategoryService service=new CategoryService(repo,seeds);
    private CategoryResponse response(boolean archived) {
        return new CategoryResponse(id,"Ăn uống","EXPENSE","coffee",null,0,
                archived?Instant.EPOCH:null,Instant.EPOCH,Instant.EPOCH);
    }
    private CategoryRequest request() {
        CategoryRequest r=new CategoryRequest(); r.name="  Cafe\u0301  "; r.categoryType="EXPENSE";
        r.colorHex="#abcdef"; return r;
    }
    @Test void createNormalizesUnicodeAndColorAndInitializesOnce() {
        when(repo.create(owner,"Café","EXPENSE",null,"#ABCDEF",0)).thenReturn(response(false));
        assertThat(service.create(owner,request()).categoryId()).isEqualTo(id);
        verify(seeds).initialize(owner);
        verify(repo).create(owner,"Café","EXPENSE",null,"#ABCDEF",0);
    }
    @Test void archivedMetadataCannotBeUpdated() {
        when(repo.find(owner,id,true)).thenReturn(response(true));
        assertThatThrownBy(()->service.update(owner,id,request())).isInstanceOfSatisfying(AppException.class,
                e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_ARCHIVED));
        verify(repo,never()).update(any(),any(),any(),any(),any(),any(),anyInt());
    }
    @Test void everyHistoricalReferencePreventsTypeChange() {
        when(repo.find(owner,id,true)).thenReturn(response(false)); when(repo.referenced(owner,id)).thenReturn(true);
        CategoryRequest r=request(); r.categoryType="INCOME";
        assertThatThrownBy(()->service.update(owner,id,r)).isInstanceOfSatisfying(AppException.class,
                e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_TYPE_IN_USE));
    }
    @Test void renameSameTypeDoesNotRejectReferencedCategory() {
        when(repo.find(owner,id,true)).thenReturn(response(false)); when(repo.find(owner,id,false)).thenReturn(response(false));
        service.update(owner,id,request()); verify(repo,never()).referenced(any(),any());
    }
    @Test void repeatedArchiveLeavesOriginalTimestamp() {
        when(repo.find(owner,id,true)).thenReturn(response(true)); service.archive(owner,id);
        verify(repo,never()).status(any(),any(),anyBoolean());
    }
    @Test void restoreMapsConcurrentUniqueConstraint() {
        when(repo.find(owner,id,true)).thenReturn(response(true));
        doThrow(new DuplicateKeyException("unique")).when(repo).status(owner,id,true);
        CategoryStatusRequest r=new CategoryStatusRequest(); r.active=true;
        assertThatThrownBy(()->service.status(owner,id,r)).isInstanceOfSatisfying(AppException.class,
                e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NAME_CONFLICT));
    }
    @Test void getNeverSeedsAndInvalidFiltersFail() {
        service.list(owner,null,null); verify(repo).list(owner,"ALL",null); verifyNoInteractions(seeds);
        assertThatThrownBy(()->service.list(owner,"unknown",null)).isInstanceOf(AppException.class);
        assertThatThrownBy(()->service.list(owner,null,"TRANSFER")).isInstanceOf(AppException.class);
    }
    @Test void validatesIconColorSortAndMissingStatus() {
        CategoryRequest r=request(); r.iconKey="";
        assertThatThrownBy(()->service.create(owner,r)).isInstanceOf(AppException.class);
        r.iconKey=null; r.colorHex="#fff";
        assertThatThrownBy(()->service.create(owner,r)).isInstanceOf(AppException.class);
        r.colorHex=null; r.sortOrder=-1;
        assertThatThrownBy(()->service.create(owner,r)).isInstanceOf(AppException.class);
        assertThatThrownBy(()->service.status(owner,id,new CategoryStatusRequest())).isInstanceOf(AppException.class);
        verifyNoInteractions(repo,seeds);
    }
    @Test void jsonRejectsUnsupportedFieldsAndFractionalSortOrder() {
        ObjectMapper mapper=new ObjectMapper();
        assertThatThrownBy(()->mapper.readValue("{\"name\":\"x\",\"userId\":\"x\"}",CategoryRequest.class)).hasRootCauseInstanceOf(AppException.class);
        assertThatThrownBy(()->mapper.readValue("{\"sortOrder\":1.1}",CategoryRequest.class)).hasRootCauseInstanceOf(AppException.class);
    }
}
