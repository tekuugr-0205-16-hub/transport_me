
package com.mobilityos.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Enables Spring Data JPA repository scanning and declarative transaction
 * management across all domain modules under com.mobilityos.
 *
 * Note: @EnableJpaAuditing already lives in AuditingConfig
 * (common/auditing) — kept separate from this class since auditing and
 * repository/transaction wiring are distinct concerns, even though both
 * are JPA-related.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.mobilityos")
@EnableTransactionManagement
public class JpaConfig {
}