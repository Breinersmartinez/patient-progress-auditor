package com.patientprogressauditor.user;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_roles")
@Getter
@Setter
@NoArgsConstructor
public class AppRole {

    public static final String NURSING = "ENFERMERIA";
    public static final String DOCTOR = "MEDICO";
    public static final String AGENT = "AGENTE";

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Eager a proposito: son pocos y se leen fuera de transaccion al resolver el usuario de cada peticion.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "app_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<AppPermission> permissions = new HashSet<>();
}
