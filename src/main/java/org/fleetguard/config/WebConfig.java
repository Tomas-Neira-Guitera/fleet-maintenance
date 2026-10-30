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
    // un vehículo (CAM-105), los defectos recurrentes (CAM-135) y el tiempo fuera de servicio (CAM-134) son
    // del admin; las inspecciones (CAM-85), del chofer; las push al navegador (CAM-119) y la campanita (CAM-96),
    // de cualquiera que esté logueado. CAM-73 suma el resto.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiring(Role.ADMIN))
                .addPathPatterns("/users", "/users/**", "/expenses", "/vehicles/*/costs", "/defects/recurring",
                        "/downtime");
        registry.addInterceptor(requiring(Role.CHOFER)).addPathPatterns("/inspections/**");
        registry.addInterceptor(requiring(Role.ADMIN, Role.CHOFER, Role.TECNICO))
                .addPathPatterns("/push/**", "/notifications", "/notifications/**");
        // CAM-117/CAM-118: configuración de avisos, solo el admin.
        registry.addInterceptor(requiring(Role.ADMIN))
                .addPathPatterns("/notification-settings", "/notification-settings/**", "/maintenance-plans/*/notifications");
    }

    private JwtAuthInterceptor requiring(Role... roles) {
        return new JwtAuthInterceptor(jwtService, userRepository, roles);
    }
}
