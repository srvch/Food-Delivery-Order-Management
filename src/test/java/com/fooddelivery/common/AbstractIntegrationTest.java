package com.fooddelivery.common;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    // Deliberately NOT managed by @Testcontainers/@Container: that JUnit
    // extension starts/stops the container per test CLASS. Since this static
    // field is declared once in this shared superclass, every IT subclass's
    // extension instance stops the very same container object after its own
    // tests finish - killing it out from under whichever subclass runs next
    // in the same JVM. Spring's context cache doesn't rebuild per subclass
    // (the inherited @DynamicPropertySource method makes the customizer look
    // identical across subclasses), so the next class replays a cached
    // ApplicationContext wired to a datasource URL/port that no longer
    // exists - "connection refused" against an already-stopped container.
    // The standard fix (Testcontainers' "singleton container" pattern) is to
    // start it once, here, and never stop it explicitly; the Ryuk reaper
    // cleans it up when the whole JVM exits.
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("fooddelivery")
                    .withUsername("fooddelivery")
                    .withPassword("fooddelivery");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    // The default HttpURLConnection-backed request factory used by
    // TestRestTemplate has a long-standing JDK bug (JDK-8146565):
    // it throws HttpRetryException ("cannot retry due to server
    // authentication, in streaming mode") for any 401/407 response to a
    // POST request with a body, even without a WWW-Authenticate challenge.
    // Switching to java.net.http.HttpClient (via JdkClientHttpRequestFactory)
    // avoids this bug entirely.
    @PostConstruct
    void configureRestTemplateRequestFactory() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }
}
