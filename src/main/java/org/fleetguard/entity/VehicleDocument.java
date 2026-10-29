package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Documento de un vehículo (CAM-127). Renovar crea una fila nueva y la anterior queda en el historial
 * (replacedById); el vigente de cada tipo es el activo sin reemplazo. Borrar es baja lógica.
 */
@Entity
@Table(name = "vehicle_documents")
public class VehicleDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleDocumentType type;

    // Solo para el tipo "otro": qué documento es.
    @Column(length = 60)
    private String name;

    @Column(length = 60)
    private String number;

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    // Sin vencimiento cargado no bloquea ni avisa nada.
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "file_url", columnDefinition = "text")
    private String fileUrl;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VehicleDocument() {
        // JPA
    }

    public VehicleDocument(UUID vehicleId, VehicleDocumentType type, String name, String number, LocalDate issuedAt,
                           LocalDate expiresAt, String fileUrl, Instant createdAt) {
        this.vehicleId = vehicleId;
        this.type = type;
        this.name = name;
        this.number = number;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.fileUrl = fileUrl;
        this.createdAt = createdAt;
    }

    /** Vigente: activo y no reemplazado por una renovación. */
    public boolean isCurrent() {
        return active && replacedById == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public VehicleDocumentType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Lo que se muestra: el nombre cargado en "otro", o el del tipo. */
    public String getDisplayName() {
        return type == VehicleDocumentType.OTRO && name != null && !name.isBlank() ? name : type.getLabel();
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public LocalDate getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDate issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDate expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public void replaceWith(UUID newDocumentId) {
        this.replacedById = newDocumentId;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
