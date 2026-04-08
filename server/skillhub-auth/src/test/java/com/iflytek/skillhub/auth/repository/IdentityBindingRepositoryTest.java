package com.iflytek.skillhub.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = IdentityBindingRepositoryTest.JpaTestConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:identitybinding;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false"
})
class IdentityBindingRepositoryTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = IdentityBinding.class)
    @EnableJpaRepositories(basePackageClasses = IdentityBindingRepository.class)
    static class JpaTestConfig {}

    @Autowired
    private IdentityBindingRepository identityBindingRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldPersistAndReadBackExtraJsonAsJsonb() {
        IdentityBinding binding = new IdentityBinding("usr_1", "dingtalk", "union-1", "alice");
        binding.setExtraJson(Map.of(
            "unionId", "union-1",
            "openId", "open-1",
            "email", "alice@example.com",
            "nick", "Alice"
        ));

        IdentityBinding saved = identityBindingRepository.saveAndFlush(binding);
        entityManager.clear();

        IdentityBinding reloaded = identityBindingRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getExtraJson())
            .containsEntry("unionId", "union-1")
            .containsEntry("openId", "open-1")
            .containsEntry("email", "alice@example.com")
            .containsEntry("nick", "Alice");
    }
}
