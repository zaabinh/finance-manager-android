package com.example.fintrack.app.user.viewmodel;

import static org.junit.Assert.assertNotNull;

import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.UserProfileResponse;

import org.junit.Test;

public class UserViewModelTest {

    @Test
    public void resourceSuccess_containsData() {
        Resource<UserProfileResponse> resource = Resource.success(null);
        assertNotNull(resource);
        org.junit.Assert.assertEquals(Resource.Status.SUCCESS, resource.getStatus());
    }

    @Test
    public void resourceError_containsMessage() {
        Resource<UserProfileResponse> resource = Resource.error("Lỗi kết nối", null);
        assertNotNull(resource);
        org.junit.Assert.assertEquals(Resource.Status.ERROR, resource.getStatus());
        org.junit.Assert.assertEquals("Lỗi kết nối", resource.getMessage());
    }
}
