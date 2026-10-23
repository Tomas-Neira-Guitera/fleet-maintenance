package org.fleetguard.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-117: los avisos por email. */
class EmailNotificationServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @SuppressWarnings("unchecked")
    private EmailNotificationService service(boolean enabled, JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new EmailNotificationService(provider, enabled, "FleetGuard <avisos@fleetguard.local>", "http://localhost:5173/");
    }

    @Test
    void unEmailPorDestinatarioConElLinkAlFront() {
        service(true, mailSender).send(List.of("a@x.com", "b@x.com"), "Orden de trabajo finalizada", "Frenos (AB123CD).",
                "/ordenes-trabajo/1");

        ArgumentCaptor<SimpleMailMessage> mails = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(mails.capture());
        SimpleMailMessage first = mails.getAllValues().get(0);
        assertArrayEquals(new String[]{"a@x.com"}, first.getTo());
        assertEquals("FleetGuard: Orden de trabajo finalizada", first.getSubject());
        assertEquals("Frenos (AB123CD).\n\nVer en FleetGuard: http://localhost:5173/ordenes-trabajo/1"
                + "\n\n--\nEste aviso se configura en FleetGuard, en Notificaciones.", first.getText());
    }

    @Test
    void sinLinkNoAgregaLaLinea() {
        assertEquals("Hola\n\n--\nEste aviso se configura en FleetGuard, en Notificaciones.",
                service(true, mailSender).body("Hola", null));
    }

    @Test
    void apagadoOSinSmtpNoMandaNada() {
        service(false, mailSender).send(List.of("a@x.com"), "T", "M", null);
        service(true, null).send(List.of("a@x.com"), "T", "M", null);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void unEnvioQueFallaNoFrenaALosDemas() {
        doThrow(new MailSendException("SMTP caído")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        service(true, mailSender).send(List.of("a@x.com", "b@x.com"), "T", "M", null);

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }
}
