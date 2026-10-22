package com.medtrack.medications;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {
  Optional<Medication> findByIdAndUserId(UUID id, UUID userId);

  Page<Medication> findByUserId(UUID userId, Pageable pageable);

  Page<Medication> findByUserIdAndArchivedAtIsNull(UUID userId, Pageable pageable);

  @Query("select m.id from Medication m where m.userId=:owner order by m.id")
  java.util.List<UUID> findOwnerIds(@Param("owner") UUID owner);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Medication m where m.id=:id and m.userId=:owner")
  Optional<Medication> findOwnedForUpdate(@Param("id") UUID id, @Param("owner") UUID owner);
}
