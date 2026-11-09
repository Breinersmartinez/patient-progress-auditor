package com.patientprogressauditor.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    List<AppUser> findByDemoTrueAndActiveTrueOrderByDisplayName();

    // A quien avisar (D30, RF-45): todo activo del rol, menos quien hizo la accion (una entrada no se notifica a su
    // propio autor). `excludedId` puede ser null (enviar un informe no excluye a nadie: enfermeria nunca es medico).
    @Query("SELECT u FROM AppUser u JOIN u.roles r WHERE r.code = :roleCode AND u.active = true "
            + "AND (:excludedId IS NULL OR u.id <> :excludedId)")
    List<AppUser> findActiveByRoleExcluding(@Param("roleCode") String roleCode, @Param("excludedId") UUID excludedId);
}
