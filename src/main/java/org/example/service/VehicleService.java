package org.example.service;

import org.example.dto.VehicleSummaryDto;
import org.example.entity.TripStatus;
import org.example.mapper.VehicleMapper;
import org.example.repository.TripRepository;
import org.example.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Lógica de negocio de GET /api/vehicles -- ver openapi.yaml. */
@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final VehicleMapper vehicleMapper;

    public VehicleService(VehicleRepository vehicleRepository, TripRepository tripRepository, VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.vehicleMapper = vehicleMapper;
    }

    public List<VehicleSummaryDto> listVehicles() {
        return vehicleRepository.findAll().stream()
                .map(vehicle -> vehicleMapper.toSummary(vehicle, hasOpenTrip(vehicle.getId())))
                .toList();
    }

    private boolean hasOpenTrip(UUID vehicleId) {
        return tripRepository.findFirstByVehicle_IdAndStatus(vehicleId, TripStatus.OPEN).isPresent();
    }
}
