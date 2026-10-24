package org.fleetguard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Manda los avisos por email (CAM-117) con el SMTP de spring.mail (en local, Mailpit). Un email por
 * destinatario, así nadie ve las direcciones de los demás. Un SMTP caído queda en el log.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;
    private final String frontendUrl;

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSender,
                                    @Value("${fleetguard.mail.enabled:false}") boolean enabled,
                                    @Value("${fleetguard.mail.from:FleetMaintenance <avisos@fleetguard.local>}") String from,
                                    @Value("${fleetguard.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Async
    public void send(List<String> addresses, String subject, String message, String link) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!enabled || sender == null) {
            log.info("Email \"{}\" sin enviar: el email está apagado (fleetguard.mail.enabled / spring.mail.host)", subject);
            return;
        }
        int sent = 0;
        for (String address : addresses) {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(address);
            mail.setSubject("FleetMaintenance: " + subject);
            mail.setText(body(message, link));
            try {
                sender.send(mail);
                sent++;
            } catch (MailException e) {
                log.error("No se pudo mandar el email \"{}\" a {}", subject, address, e);
            }
        }
        log.info("Email \"{}\" enviado a {} de {} destinatarios", subject, sent, addresses.size());
    }

    String body(String message, String link) {
        String text = message;
        if (link != null && !link.isBlank()) {
            text += "\n\nVer en FleetMaintenance: " + frontendUrl + (link.startsWith("/") ? link : "/" + link);
        }
        return text + "\n\n--\nEste aviso se configura en FleetMaintenance, en Notificaciones.";
    }
}
