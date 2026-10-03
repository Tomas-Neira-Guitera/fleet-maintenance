package org.fleetguard.config;

import org.fleetguard.auth.JwtAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Habilita CORS para que el frontend (otro origen) pueda consumir la API, y protege las rutas con JWT. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebConfig(@Value("${fleetguard.cors.allowed-origins}") String allowedOrigins,
                     JwtAuthInterceptor jwtAuthInterceptor) {
        this.allowedOrigins = allowedOrigins.split(",");
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    // CAM-23: por ahora solo /users exige JWT de ADMIN. CAM-73 suma el resto de la API acá.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor).addPathPatterns("/users", "/users/**");
    }
}
