package org.fleetguard.service;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.NotificationDto;
import org.fleetguard.entity.Notification;
import org.fleetguard.exception.NotificationNotFoundException;
import org.fleetguard.exception.NotificationValidationException;
import org.fleetguard.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** La campanita de cada usuario (CAM-96). Siempre con el usuario del token, nunca un id del pedido. */
@Service
public class NotificationInboxService {

    public static final int DEFAULT_LIMIT = 30;
    public static final int MAX_LIMIT = 100;

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationInboxService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(UUID userId, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new NotificationValidationException("Parámetro inválido",
                    List.of(new FieldValidationErrorDetail("limit", "Tiene que estar entre 1 y " + MAX_LIMIT)));
        }
        return repository.findByRecipientIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
                .stream().map(NotificationInboxService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByRecipientIdAndReadAtIsNullAndDismissedAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, String idRaw) {
        find(userId, idRaw).markRead(clock.instant());
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, clock.instant());
    }

    /** La X del panel: lo saca de la campanita sin borrarlo. Idempotente. */
    @Transactional
    public void dismiss(UUID userId, String idRaw) {
        find(userId, idRaw).dismiss(clock.instant());
    }

    // El aviso de otro usuario responde igual que uno que no existe (CAM-96 §6).
    private Notification find(UUID userId, String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            throw notFound(idRaw);
        }
        return repository.findByIdAndRecipientId(id, userId).orElseThrow(() -> notFound(idRaw));
    }

    private static NotificationNotFoundException notFound(String id) {
        return new NotificationNotFoundException("NOTIFICATION_NOT_FOUND", "No existe un aviso con id " + id);
    }

    private static NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId().toString(), n.getType().toJson(), n.getTitle(), n.getMessage(), n.getLink(),
                n.getCreatedAt().toString(), toString(n.getReadAt()));
    }

    private static String toString(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
