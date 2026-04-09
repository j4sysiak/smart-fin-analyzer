package pl.edu.praktyki.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Konfiguracja Swagger / OpenAPI z obsługą JWT.
 *
 * Po uruchomieniu aplikacji wejdź na:
 *   http://localhost:8080/swagger-ui/index.html
 *
 * Kliknij przycisk 🔒 "Authorize" (prawy górny róg),
 * wklej token JWT (sam token, BEZ prefiksu "Bearer ") i zatwierdź.
 * Od tego momentu wszystkie requesty z Swagger UI będą zawierać nagłówek:
 *   Authorization: Bearer <token>
 *
 * Token uzyskasz z endpointu:
 *   GET /auth/token?user=dev
 */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI smartFinOpenAPI() {
        final String schemeName = 'bearerAuth'

        return new OpenAPI()
                .info(new Info()
                        .title('Smart-Fin-Analyzer API')
                        .description('REST API do zarządzania transakcjami finansowymi. ' +
                                'Aby uzyskać token JWT: GET /auth/token?user=dev')
                        .version('1.0'))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme('bearer')
                                .bearerFormat('JWT')
                                .description('Wklej token JWT (bez prefiksu "Bearer"). ' +
                                        'Token uzyskasz z: GET /auth/token?user=dev')))
    }
}

