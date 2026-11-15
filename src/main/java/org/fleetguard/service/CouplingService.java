package org.fleetguard.service;

import org.fleetguard.dto.CouplingDto;
import org.fleetguard.dto.CoupleRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.TrailerCoupling;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleType;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.TrailerCouplingRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Enganche de remolques: un semirremolque va con un tractor; un acoplado, con un camión rígido. Uno por vez de
 * cada lado. Mientras está enganchado, el remolque suma los km de lo que lo arrastra (OdometerReadings).
 */
@Service
public class CouplingService {

    private final TrailerCouplingRepository couplingRepository;
    private final VehicleRepository vehicleRepository;
    private final Clock clock;

    public CouplingService(TrailerCouplingRepository couplingRepository, VehicleRepository vehicleRepository, Clock clock) {
        this.couplingRepository = couplingRepository;
        this.vehicleRepository = vehicleRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CouplingDto get(String vehicleId) {
        return toDto(find(vehicleId));
    }

    /** Engancha el remolque; si ya estaba con otro, o el otro arrastraba uno, esos enganches se cierran. */
    @Transactional
    public CouplingDto couple(String trailerId, CoupleRequest request, String byName) {
        Vehicle trailer = find(trailerId);
        if (request == null || request.towingId() == null || request.towingId().isBlank()) {
            throw new VehicleValidationException("Datos inválidos",
                    List.of(new FieldValidationErrorDetail("towingId", "Obligatorio")));
        }
        couple(trailer, find(request.towingId()), byName);
        return toDto(trailer);
    }

    @Transactional
    public CouplingDto uncouple(String trailerId) {
        Vehicle trailer = find(trailerId);
        couplingRepository.findFirstByTrailer_IdAndUncoupledAtIsNull(trailer.getId()).ifPresent(this::close);
        return toDto(trailer);
    }

    /**
     * El pre-viaje de un tractor o camión puede decir qué remolque lleva: un id lo engancha, "none" desengancha
     * el que tenía, null deja todo como estaba. Se valida antes de guardar la inspección.
     */
    public Optional<Vehicle> resolveInspectionTrailer(Vehicle towing, String trailerIdRaw) {
        if (trailerIdRaw == null || trailerIdRaw.isBlank() || trailerIdRaw.equals("none")) {
            return Optional.empty();
        }
        Vehicle trailer = Uuids.parse(trailerIdRaw).flatMap(vehicleRepository::findById)
                .orElseThrow(() -> new VehicleNotFoundException(trailerIdRaw));
        requireCanCouple(trailer, towing);
        return Optional.of(trailer);
    }

    @Transactional
    public void applyInspectionTrailer(Vehicle towing, String trailerIdRaw, Optional<Vehicle> trailer, String byName) {
        if (trailerIdRaw == null || trailerIdRaw.isBlank()) {
            return;
        }
        if (trailer.isEmpty()) {
            couplingRepository.findFirstByTowing_IdAndUncoupledAtIsNull(towing.getId()).ifPresent(this::close);
            return;
        }
        Optional<TrailerCoupling> current = couplingRepository.findFirstByTowing_IdAndUncoupledAtIsNull(towing.getId());
        if (current.isPresent() && current.get().getTrailer().getId().equals(trailer.get().getId())) {
            return;
        }
        couple(trailer.get(), towing, byName);
    }

    private void couple(Vehicle trailer, Vehicle towing, String byName) {
        requireCanCouple(trailer, towing);
        Optional<TrailerCoupling> current = couplingRepository.findFirstByTrailer_IdAndUncoupledAtIsNull(trailer.getId());
        if (current.isPresent() && current.get().getTowing().getId().equals(towing.getId())) {
            return;
        }
        current.ifPresent(this::close);
        couplingRepository.findFirstByTowing_IdAndUncoupledAtIsNull(towing.getId()).ifPresent(this::close);
        couplingRepository.save(new TrailerCoupling(trailer, towing, clock.instant(), byName));
    }

    private static void requireCanCouple(Vehicle trailer, Vehicle towing) {
        if (trailer.getVehicleType() == null || !trailer.getVehicleType().isTrailer()) {
            throw new VehicleStateConflictException("NOT_A_TRAILER", trailer.getPlate() + " no es un remolque");
        }
        if (towing.getVehicleType() == null || !towing.getVehicleType().canTow(trailer.getVehicleType())) {
            throw new VehicleStateConflictException("CANNOT_TOW", towing.getPlate() + " no puede arrastrar un "
                    + trailer.getVehicleType().label().toLowerCase()
                    + ": un semirremolque va con un tractor y un acoplado con un camión rígido");
        }
        if (!trailer.isActive() || !towing.isActive()) {
            throw new VehicleStateConflictException("VEHICLE_INACTIVE", "No se engancha un vehículo dado de baja");
        }
    }

    private void close(TrailerCoupling coupling) {
        coupling.uncouple(clock.instant());
        couplingRepository.save(coupling);
    }

    private Vehicle find(String id) {
        return Uuids.parse(id).flatMap(vehicleRepository::findById).orElseThrow(() -> new VehicleNotFoundException(id));
    }

    private CouplingDto toDto(Vehicle vehicle) {
        CouplingDto.VehicleRef towedBy = couplingRepository.findFirstByTrailer_IdAndUncoupledAtIsNull(vehicle.getId())
                .map(c -> ref(c.getTowing(), c)).orElse(null);
        CouplingDto.VehicleRef towing = couplingRepository.findFirstByTowing_IdAndUncoupledAtIsNull(vehicle.getId())
                .map(c -> ref(c.getTrailer(), c)).orElse(null);
        VehicleType type = vehicle.getVehicleType();
        List<String> canTow = type == null ? List.of() : Arrays.stream(VehicleType.values())
                .filter(type::canTow).map(t -> t.toJson()).toList();
        List<CouplingDto.HistoryItem> history = couplingRepository
                .findByTrailer_IdOrTowing_IdOrderByCoupledAtDesc(vehicle.getId(), vehicle.getId()).stream()
                .map(c -> new CouplingDto.HistoryItem(c.getTrailer().getId().toString(), c.getTrailer().getPlate(),
                        c.getTowing().getId().toString(), c.getTowing().getPlate(), c.getCoupledAt().toString(),
                        c.getUncoupledAt() == null ? null : c.getUncoupledAt().toString(), c.getCoupledByName()))
                .toList();
        return new CouplingDto(vehicle.getId().toString(), towedBy, towing, canTow, history);
    }

    private static CouplingDto.VehicleRef ref(Vehicle other, TrailerCoupling coupling) {
        return new CouplingDto.VehicleRef(other.getId().toString(), other.getPlate(),
                VehicleType.toJson(other.getVehicleType()), coupling.getCoupledAt().toString());
    }
}
