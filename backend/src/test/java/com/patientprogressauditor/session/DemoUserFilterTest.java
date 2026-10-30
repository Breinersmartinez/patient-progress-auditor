package com.patientprogressauditor.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.patientprogressauditor.user.AppPermission;
import com.patientprogressauditor.user.AppRole;
import com.patientprogressauditor.user.AppUser;
import com.patientprogressauditor.user.AppUserRepository;

class DemoUserFilterTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final DemoUserFilter filter = new DemoUserFilter(users);
    private final UUID nurseId = UUID.randomUUID();

    @BeforeEach
    void nurse() {
        AppPermission read = new AppPermission();
        read.setCode("PATIENTS_READ");
        AppRole role = new AppRole();
        role.setCode(AppRole.NURSING);
        role.setPermissions(Set.of(read));
        AppUser user = new AppUser();
        user.setId(nurseId);
        user.setEmail("x@example.test");
        user.setDisplayName("Enfermera");
        user.setDemo(true);
        user.setRoles(Set.of(role));
        when(users.findById(nurseId)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private boolean authenticatedAfter(MockHttpServletRequest request) throws Exception {
        SecurityContextHolder.clearContext();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    @Test
    void headerIdentifiesTheUser() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/patients");
        request.addHeader("X-Demo-User", nurseId.toString());

        assertThat(authenticatedAfter(request)).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).contains("ROLE_ENFERMERIA", "PATIENTS_READ");
    }

    @Test
    void queryParameterIsAcceptedOnlyOnTheSseRouteWithGet() throws Exception {
        MockHttpServletRequest sse = request("GET", "/api/runs/abc/events");
        sse.setParameter("demoUser", nurseId.toString());
        assertThat(authenticatedAfter(sse)).isTrue();

        MockHttpServletRequest otherRoute = request("GET", "/api/patients");
        otherRoute.setParameter("demoUser", nurseId.toString());
        assertThat(authenticatedAfter(otherRoute)).isFalse();

        MockHttpServletRequest post = request("POST", "/api/runs/abc/events");
        post.setParameter("demoUser", nurseId.toString());
        assertThat(authenticatedAfter(post)).isFalse();
    }

    @Test
    void noHeaderLeavesNoUser() throws Exception {
        assertThat(authenticatedAfter(request("GET", "/api/patients"))).isFalse();
    }
}
