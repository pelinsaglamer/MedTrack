package com.medtrack.medications;

import com.medtrack.common.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/medications")
@Tag(name = "Medications", description = "Owner-scoped medication metadata and archiving")
@ApiResponses({
  @ApiResponse(
      responseCode = "401",
      description = "Authentication required",
      content = @Content(schema = @Schema(implementation = ApiError.class))),
  @ApiResponse(
      responseCode = "404",
      description = "Resource inaccessible or not found",
      content = @Content(schema = @Schema(implementation = ApiError.class))),
  @ApiResponse(
      responseCode = "422",
      description = "Validation failed",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class MedicationController {
  private final MedicationService medications;

  public MedicationController(MedicationService medications) {
    this.medications = medications;
  }

  @PostMapping
  @Operation(
      summary = "Create a medication",
      description =
          "initialStock is an opening amount; inventory operations are introduced in Phase 5")
  @ApiResponse(
      responseCode = "201",
      description = "Created",
      content = @Content(schema = @Schema(implementation = MedicationDtos.View.class)))
  public ResponseEntity<MedicationDtos.View> create(
      @Valid @RequestBody MedicationDtos.Create input) {
    var result = medications.create(input);
    return ResponseEntity.created(URI.create("/api/v1/medications/" + result.id())).body(result);
  }

  @GetMapping
  @Operation(
      summary = "List own medications",
      description = "Active only by default, newest first; stable UUID tie-breaker")
  public MedicationDtos.Page list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "false") boolean includeArchived) {
    return medications.list(page, size, includeArchived);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get an owned medication, including archived")
  public MedicationDtos.View get(@PathVariable UUID id) {
    return medications.get(id);
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Edit metadata or archive",
      description =
          "Omitted/null fields are unchanged; archived:true is idempotent; initialStock is immutable")
  public MedicationDtos.View patch(
      @PathVariable UUID id, @Valid @RequestBody MedicationDtos.Patch input) {
    return medications.patch(id, input);
  }
}
