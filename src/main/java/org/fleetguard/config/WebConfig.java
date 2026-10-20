package org.fleetguard.config;

import org.fleetguard.auth.JwtAuthInterceptor;
import org.fleetguard.entity.Role;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Habilita CORS para que el frontend (otro origen) pueda consumir la API, y protege las rutas con JWT. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public WebConfig(@Value("${fleetguard.cors.allowed-origins}") String allowedOrigins,
                     JwtService jwtService, UserRepository userRepository) {
        this.allowedOrigins = allowedOrigins.split(",");
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    // Rutas que ya exigen JWT, cada una con su rol: usuarios (CAM-23), gastos (CAM-52) y costos de
    // un vehículo (CAM-105) son del admin; las inspecciones (CAM-85), del chofer. CAM-73 suma el
    // resto de la API acá.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiring(Role.ADMIN))
                .addPathPatterns("/users", "/users/**", "/expenses", "/vehicles/*/costs");
        registry.addInterceptor(requiring(Role.CHOFER)).addPathPatterns("/inspections/**");
    }

    private JwtAuthInterceptor requiring(Role role) {
        return new JwtAuthInterceptor(jwtService, userRepository, role);
    }
}
