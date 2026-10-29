package org.fleetguard.service;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.NotificationSettingDto;
import org.fleetguard.dto.UpdateNotificationSettingRequest;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationSetting;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.exception.NotificationNotFoundException;
import org.fleetguard.exception.NotificationValidationException;
import org.fleetguard.repository.NotificationSettingRepository;
import org.fleetguard.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Configuración de cada tipo de aviso (CAM-117). Ver docs/api/CAM-117-notification-settings-contract.md. */
@Service
public class NotificationSettingsService {

    public static final int MAX_LEAD_DAYS = 365;
    public static final int MAX_LEAD_KM = 100_000;

    private final NotificationSettingRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    public NotificationSettingsService(NotificationSettingRepository repository, UserRepository userRepository, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EffectiveNotificationSetting effective(NotificationType type) {
        return repository.findById(type).map(NotificationSettingsService::fromRow)
                .orElseGet(() -> NotificationCatalog.of(type).defaults());
    }

    @Transactional(readOnly = true)
    public List<NotificationSettingDto> list() {
        return NotificationCatalog.all().stream().map(def -> toDto(def, effective(def.type()))).toList();
    }

    @Transactional
    public NotificationSettingDto update(String typeRaw, UpdateNotificationSettingRequest request) {
        NotificationCatalog.Definition def = definition(typeRaw);
        List<FieldValidationErrorDetail> details = new ArrayList<>();

        if (request.enabled() == null) {
            details.add(new FieldValidationErrorDetail("enabled", "Obligatorio"));
        }
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        Set<NotificationChannel> channels = parseChannels(request.channels(), details);
        if (enabled && channels.isEmpty()) {
            details.add(new FieldValidationErrorDetail("channels", "Elegí al menos un canal"));
        }

        Set<Role> roles = Set.of();
        Set<UUID> userIds = Set.of();
        boolean sentRecipients = notEmpty(request.recipientRoles()) || notEmpty(request.recipientUserIds());
        if (def.audience() == NotificationCatalog.Audience.OWNER) {
            if (sentRecipients) {
                details.add(new FieldValidationErrorDetail("recipientRoles",
                        "Este aviso le llega siempre a: " + def.ownerDescription() + ". No se eligen destinatarios"));
            }
        } else {
            roles = parseRoles(request.recipientRoles(), details);
            userIds = parseUserIds(request.recipientUserIds(), details);
            if (enabled && roles.isEmpty() && userIds.isEmpty()) {
                details.add(new FieldValidationErrorDetail("recipientRoles", "Elegí al menos un rol o un usuario"));
            }
        }

        Integer leadDays = request.leadDays();
        Integer leadKm = request.leadKm();
        if (def.supportsLead()) {
            validateLead("leadDays", leadDays, MAX_LEAD_DAYS, details);
            if (def.supportsLeadKm()) {
                validateLead("leadKm", leadKm, MAX_LEAD_KM, details);
            } else if (leadKm != null) {
                details.add(new FieldValidationErrorDetail("leadKm", "Este aviso se anticipa solo en días"));
            }
        } else if (leadDays != null || leadKm != null) {
            details.add(new FieldValidationErrorDetail("leadDays",
                    "La anticipación solo se configura en \"Mantenimiento por vencer\" y \"Documento por vencer\""));
        }

        if (!details.isEmpty()) {
            throw new NotificationValidationException("Configuración de aviso inválida", details);
        }

        NotificationSetting setting = repository.findById(def.type()).orElseGet(() -> new NotificationSetting(def.type()));
        setting.apply(enabled, channels, roles, userIds, leadDays, leadKm, clock.instant());
        repository.save(setting);
        return toDto(def, fromRow(setting));
    }

    /** Vuelve a la configuración por defecto del catálogo. */
    @Transactional
    public NotificationSettingDto reset(String typeRaw) {
        NotificationCatalog.Definition def = definition(typeRaw);
        repository.deleteById(def.type());
        return toDto(def, def.defaults());
    }

    static void validateLead(String field, Integer value, int max, List<FieldValidationErrorDetail> details) {
        if (value == null) {
            details.add(new FieldValidationErrorDetail(field, "Obligatorio"));
        } else if (value < 1 || value > max) {
            details.add(new FieldValidationErrorDetail(field, "Tiene que estar entre 1 y " + max));
        }
    }

    static Set<NotificationChannel> parseChannels(Collection<String> raw, List<FieldValidationErrorDetail> details) {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (raw == null) {
            return channels;
        }
        for (String value : raw) {
            NotificationChannel channel = NotificationChannel.fromJson(value);
            if (channel == null) {
                details.add(new FieldValidationErrorDetail("channels", "Canal inválido: '" + value
                        + "'. Debe ser 'in_app', 'push' o 'email'"));
            } else {
                channels.add(channel);
            }
        }
        return channels;
    }

    static List<String> channelsToJson(Set<NotificationChannel> channels) {
        return Arrays.stream(NotificationChannel.values()).filter(channels::contains).map(NotificationChannel::toJson).toList();
    }

    private Set<Role> parseRoles(List<String> raw, List<FieldValidationErrorDetail> details) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (raw == null) {
            return roles;
        }
        for (String value : raw) {
            try {
                roles.add(Role.valueOf(value));
            } catch (IllegalArgumentException | NullPointerException e) {
                details.add(new FieldValidationErrorDetail("recipientRoles", "Rol inválido: '" + value
                        + "'. Debe ser 'ADMIN', 'CHOFER' o 'TECNICO'"));
            }
        }
        return roles;
    }

    private Set<UUID> parseUserIds(List<String> raw, List<FieldValidationErrorDetail> details) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (raw == null) {
            return ids;
        }
        for (String value : raw) {
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException | NullPointerException e) {
                details.add(new FieldValidationErrorDetail("recipientUserIds", "Id de usuario inválido: '" + value + "'"));
            }
        }
        Set<UUID> existing = userRepository.findAllById(ids).stream().map(User::getId).collect(Collectors.toSet());
        ids.stream().filter(id -> !existing.contains(id)).forEach(id ->
                details.add(new FieldValidationErrorDetail("recipientUserIds", "No existe un usuario con id " + id)));
        return ids;
    }

    private static boolean notEmpty(List<String> values) {
        return values != null && !values.isEmpty();
    }

    private static NotificationCatalog.Definition definition(String typeRaw) {
        NotificationType type = NotificationType.fromJson(typeRaw);
        if (type == null) {
            throw new NotificationNotFoundException("NOTIFICATION_TYPE_NOT_FOUND", "No existe el tipo de aviso '" + typeRaw + "'");
        }
        return NotificationCatalog.of(type);
    }

    private static EffectiveNotificationSetting fromRow(NotificationSetting row) {
        return new EffectiveNotificationSetting(row.getType(), row.isEnabled(), row.getChannels(),
                row.getRecipientRoles(), row.getRecipientUserIds(), row.getLeadDays(), row.getLeadKm(), true);
    }

    private static NotificationSettingDto toDto(NotificationCatalog.Definition def, EffectiveNotificationSetting setting) {
        return new NotificationSettingDto(def.type().toJson(), def.label(), def.description(), def.audience().toJson(),
                def.ownerDescription(), setting.enabled(), channelsToJson(setting.channels()),
                setting.recipientRoles().stream().sorted().map(Role::name).toList(),
                setting.recipientUserIds().stream().map(UUID::toString).sorted().toList(),
                def.supportsLead(), def.supportsLeadKm(), setting.leadDays(), setting.leadKm(), setting.customized());
    }
}
