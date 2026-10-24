package org.fleetguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Trabajo fuera del pedido: tareas programadas (@Scheduled), como el recordatorio de cerrar el viaje
 * (CAM-101), y envíos en segundo plano (@Async), como las push al navegador (CAM-119), que usan el
 * pool de hilos que trae Spring Boot. El reloj es un bean para que las tareas se puedan probar con
 * una hora fija, sin depender del reloj real.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
