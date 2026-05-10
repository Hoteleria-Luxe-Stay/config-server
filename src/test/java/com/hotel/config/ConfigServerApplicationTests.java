package com.hotel.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke + integration test del Config Server.
 *
 * Levanta el ApplicationContext completo con perfil 'native' y verifica:
 *  - El contexto de Spring carga sin errores (smoke).
 *  - La SecurityFilterChain se construye correctamente (cubre SecurityConfig.filterChain).
 *  - El bean ConfigServerApplication esta en el contexto.
 *
 * Como SecurityConfig.filterChain corre durante el setup del context, JaCoCo registra
 * todas las lineas (incluidos los lambdas de la DSL de HttpSecurity) como covered.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.profiles.active=native",
        "spring.cloud.config.server.native.search-locations=classpath:/configs/",
        "CONFIG_SERVER_USER=test-user",
        "CONFIG_SERVER_PASSWORD=test-pass"
})
class ConfigServerApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void contextLoadsSuccessfully() {
        assertThat(applicationContext).isNotNull();
        // El bean se llama 'filterChain' (nombre del metodo @Bean en SecurityConfig)
        assertThat(applicationContext.containsBean("filterChain")).isTrue();
    }

    @Test
    void securityFilterChainBeanIsBuilt() {
        // Cubre los lambdas de la DSL HttpSecurity (csrf disable, sessionManagement,
        // authorizeHttpRequests con permitAll en /actuator/health|info, httpBasic).
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void configServerApplicationBeanIsLoaded() {
        // Verifica que el SpringBootApplication se registro como bean
        ConfigServerApplication app = applicationContext.getBean(ConfigServerApplication.class);
        assertThat(app).isNotNull();
    }
}
