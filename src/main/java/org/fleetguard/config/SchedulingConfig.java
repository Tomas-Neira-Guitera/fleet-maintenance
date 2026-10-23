package org.fleetguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Tareas programadas (@Scheduled), como el recordatorio de cerrar el viaje (CAM-101). El reloj es un
 * bean para que esas tareas se puedan probar con una hora fija, sin depender del reloj real.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
