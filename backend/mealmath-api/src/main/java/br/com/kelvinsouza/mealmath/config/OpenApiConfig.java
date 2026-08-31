package br.com.kelvinsouza.mealmath.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuracao da documentacao da API. O Swagger fica em /swagger-ui.html e aceita o token JWT. */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER = "bearer-jwt";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("MealMath API")
                                .description(
                                        "API de controle de custo de dietas: rateio fracionado"
                                                + " de itens de mercado, biblioteca de refeições,"
                                                + " diário de consumo e metas de orçamento.")
                                .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        ESQUEMA_BEARER,
                                        new SecurityScheme()
                                                .name(ESQUEMA_BEARER)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }
}
