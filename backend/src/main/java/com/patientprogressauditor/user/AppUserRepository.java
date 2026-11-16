package com.patientprogressauditor.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    // El correo del token es la llave entre Keycloak y la aplicacion.
    Optional<AppUser> findByEmailIgnoreCaseAndActiveTrue(String email);
}
