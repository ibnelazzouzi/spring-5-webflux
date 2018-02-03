package org.iproduct.demos.spring.hellowebflux;

import lombok.extern.slf4j.Slf4j;
import org.iproduct.demos.spring.manageusers.WebfluxUsersApplication;
import org.iproduct.demos.spring.manageusers.domain.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.springframework.security.web.server.header.ContentTypeOptionsServerHttpHeadersWriter.NOSNIFF;
import static org.springframework.security.web.server.header.ContentTypeOptionsServerHttpHeadersWriter.X_CONTENT_OPTIONS;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = WebfluxUsersApplication.class)
@Slf4j
//@TestPropertySource(properties = {"local.server.port=9000", "local.management.port=0"})
public class WebfluxUsersApplicationTests {
    private static final ParameterizedTypeReference<List<User>> typeRef = new ParameterizedTypeReference<List<User>>() {
    };

    @Autowired
    private ApplicationContext context;

    private WebTestClient client;
    private User createdCustomer;

    private static ExchangeFilterFunction rootCredentials() {
        return basicAuthentication("root", "root");
    }

    private static ExchangeFilterFunction adminCredentials() {
        return basicAuthentication("admin", "admin");
    }

    @Before
    public void setUp() {
        client = WebTestClient.bindToApplicationContext(context).build();
        client
                .mutate()
                .filter(adminCredentials())
                .build()
                .post()
                .uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .syncBody("{\"username\":\"testUser\", \"password\":\"test123\", \"fname\":\"Spring\",\"lname\":\"User\", \"role\":\"CUSTOMER\"}")
                .exchange()
                .expectStatus().isCreated()
                .returnResult(User.class)
                .consumeWith(userRespFlux -> {
//                    log.info(userRespFlux.toString());
                    userRespFlux.getResponseBody()
                            .subscribe((User user) -> {
                                createdCustomer = user;
                            });
                });
    }

    @After
    public void cleanUpAfterClass() {
        client
                .mutate()
                .filter(adminCredentials())
                .build()
                .delete()
                .uri("/api/users/" + createdCustomer.getId())
                .exchange()
                .returnResult(User.class)
                .consumeWith(user -> {
//                    log.info(user.toString());
                });
    }

    @Test
    public void whenUnauthenticatedGetUsersShouldFail() throws Exception {
        client
                .get()
                .uri("/api/users")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    public void whenAdminCredentialsGetUsersShouldSucceed() throws Exception {
        client
            .mutate()
            .filter(adminCredentials())
            .build()
            .get()
            .uri("/api/users")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(User.class).consumeWith(result -> {
                log.info("Received: " + result);
                assertTrue("Should contain username testUser",
                    result.getResponseBody().stream().anyMatch(user -> user.getUsername().equals("testUser")));
        });
    }

    @Test
    public void whenAdminCredentialsGetUsersShouldSucceedWithHeaders() throws Exception {
        client
            .mutate()
            .filter(adminCredentials())
            .build()
            .get()
            .uri("/api/users")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate")
            .expectHeader().valueEquals(HttpHeaders.EXPIRES, "0")
            .expectHeader().valueEquals(HttpHeaders.PRAGMA, "no-cache")
            .expectHeader().valueEquals(X_CONTENT_OPTIONS, NOSNIFF)
            .expectBodyList(User.class).consumeWith(result -> {
                log.info("Received: " + result);
                assertTrue("Should contain username testUser",
                    result.getResponseBody().stream().anyMatch(user -> user.getUsername().equals("testUser")));
            });
    }

}
