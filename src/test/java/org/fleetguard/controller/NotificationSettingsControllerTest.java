package org.fleetguard.controller;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.NotificationSettingDto;
import org.fleetguard.dto.PlanNotificationSettingsDto;
import org.fleetguard.dto.UpdateNotificationSettingRequest;
import org.fleetguard.dto.UpdatePlanNotificationSettingsRequest;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.NotificationValidationException;
import org.fleetguard.service.NotificationSettingsService;
import org.fleetguard.service.PlanNotificationSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/notification-settings (CAM-117) y /api/maintenance-plans/{id}/notifications (CAM-118): solo ADMIN. */
@WebMvcTest({NotificationSettingsController.class, MaintenancePlanNotificationController.class})
class NotificationSettingsControllerTest extends WebMvcTestBase {

    @MockBean
    private NotificationSettingsService settings;

    @MockBean
    private PlanNotificationSettingsService planSettings;

    private static NotificationSettingDto setting() {
        return new NotificationSettingDto("work_order_finalized", "Orden de trabajo finalizada", "…", "configurable", null,
                true, List.of("in_app", "email"), List.of("ADMIN"), List.of(), false, null, null, true);
    }

    @Test
    void elAdminListaLaConfiguracion() throws Exception {
        when(settings.list()).thenReturn(List.of(setting()));

        mvc.perform(get("/notification-settings").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("work_order_finalized"))
                .andExpect(jsonPath("$.items[0].channels[1]").value("email"));
    }

    @Test
    void otroRolEs403YSinTokenEs401() throws Exception {
        mvc.perform(get("/notification-settings").header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/maintenance-plans/1/notifications")).andExpect(status().isUnauthorized());
        verifyNoInteractions(settings, planSettings);
    }

    @Test
    void guardarYRestablecer() throws Exception {
        when(settings.update(eq("work_order_finalized"), any())).thenReturn(setting());
        when(settings.reset("work_order_finalized")).thenReturn(setting());
        String auth = loginAs("admin", Role.ADMIN);

        mvc.perform(put("/notification-settings/work_order_finalized").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true, \"channels\": [\"in_app\", \"email\"], \"recipientRoles\": [\"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(true));
        mvc.perform(delete("/notification-settings/work_order_finalized").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        verify(settings).update("work_order_finalized",
                new UpdateNotificationSettingRequest(true, List.of("in_app", "email"), List.of("ADMIN"), null, null, null));
    }

    @Test
    void unaConfiguracionInvalidaEs422() throws Exception {
        when(settings.update(any(), any())).thenThrow(new NotificationValidationException("Configuración de aviso inválida",
                List.of(new FieldValidationErrorDetail("channels", "Elegí al menos un canal"))));

        mvc.perform(put("/notification-settings/work_order_finalized").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("channels"));
    }

    @Test
    void avisosDeUnPlan() throws Exception {
        PlanNotificationSettingsDto dto = new PlanNotificationSettingsDto("p1", "time", true, true, List.of("push"), 15, null,
                new PlanNotificationSettingsDto.General(true, true, List.of("in_app"), 7, 1000));
        when(planSettings.get("p1")).thenReturn(dto);
        when(planSettings.update(eq("p1"), any())).thenReturn(dto);
        when(planSettings.reset("p1")).thenReturn(dto);
        String auth = loginAs("admin", Role.ADMIN);

        mvc.perform(get("/maintenance-plans/p1/notifications").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadDays").value(15))
                .andExpect(jsonPath("$.general.leadKm").value(1000));
        mvc.perform(put("/maintenance-plans/p1/notifications").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true, \"channels\": [\"push\"], \"leadDays\": 15}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/maintenance-plans/p1/notifications").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        verify(planSettings).update("p1", new UpdatePlanNotificationSettingsRequest(true, List.of("push"), 15, null));
    }
}
