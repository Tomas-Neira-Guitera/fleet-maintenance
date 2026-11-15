package org.fleetguard.service;

import org.fleetguard.dto.CreateVehicleRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.ImportVehiclesRequest;
import org.fleetguard.dto.VehicleImportResultDto;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CAM-154: alta de la flota desde un archivo. Cada fila se valida con las mismas reglas que el alta manual; la
 * vista previa (dryRun) no guarda nada, y al confirmar se crean solo las filas válidas.
 * Ver docs/api/CAM-154-vehicle-import-contract.md.
 */
@Service
public class VehicleImportService {

    static final int MAX_ROWS = 500;

    private final VehicleRepository vehicleRepository;

    public VehicleImportService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional
    public VehicleImportResultDto importVehicles(ImportVehiclesRequest request, boolean dryRun) {
        List<CreateVehicleRequest> rows = request == null || request.rows() == null ? List.of() : request.rows();
        if (rows.isEmpty() || rows.size() > MAX_ROWS) {
            throw new VehicleValidationException("El archivo tiene que tener entre 1 y " + MAX_ROWS + " vehículos",
                    List.of(new FieldValidationErrorDetail("rows", "Cantidad de filas: " + rows.size())));
        }

        List<VehicleImportResultDto.Row> result = new ArrayList<>();
        // Patente (en mayúsculas) -> primera fila del archivo que la trae.
        Map<String, Integer> firstRowByPlate = new HashMap<>();
        int valid = 0;
        int created = 0;
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 1;
            CreateVehicleRequest row = clean(rows.get(i));
            List<FieldValidationErrorDetail> errors = new ArrayList<>(VehicleService.validateNew(row));

            if (row.plate() != null) {
                String key = row.plate().toUpperCase(Locale.ROOT);
                Integer firstRow = firstRowByPlate.putIfAbsent(key, rowNumber);
                if (firstRow != null) {
                    errors.add(new FieldValidationErrorDetail("plate",
                            "La patente se repite en el archivo (fila " + firstRow + ")"));
                } else if (vehicleRepository.existsByPlateIgnoreCase(row.plate())) {
                    errors.add(new FieldValidationErrorDetail("plate", "Ya existe un vehículo con la patente " + row.plate()));
                }
            }

            String vehicleId = null;
            if (errors.isEmpty()) {
                valid++;
                if (!dryRun) {
                    Vehicle saved = vehicleRepository.save(VehicleService.newVehicle(row));
                    vehicleId = saved.getId() == null ? null : saved.getId().toString();
                    created++;
                }
            }
            result.add(new VehicleImportResultDto.Row(rowNumber, row.plate(), errors.isEmpty(), errors, vehicleId));
        }
        return new VehicleImportResultDto(rows.size(), valid, created, result);
    }

    /** Lo que viene de una planilla trae espacios de más; la patente va en mayúsculas y sin espacios. */
    private static CreateVehicleRequest clean(CreateVehicleRequest row) {
        if (row == null) {
            return new CreateVehicleRequest(null, null, null, null, null, null, null);
        }
        String plate = blankToNull(row.plate());
        return new CreateVehicleRequest(
                plate == null ? null : plate.replaceAll("\\s+", "").toUpperCase(Locale.ROOT),
                blankToNull(row.brand()),
                blankToNull(row.model()),
                blankToNull(row.vehicleType()),
                row.year(),
                blankToNull(row.chassisNumber()),
                row.odometerKm());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
