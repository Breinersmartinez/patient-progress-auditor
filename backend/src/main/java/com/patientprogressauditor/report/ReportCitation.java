package com.patientprogressauditor.report;

import java.io.Serializable;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Indice normalizado de content.claims: una fila por afirmacion y entrada citada (RN-05).
@Entity
@Immutable
@Table(name = "report_citations")
@IdClass(ReportCitation.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportCitation {

    @Id
    @Column(name = "report_version_id")
    private UUID reportVersionId;

    @Id
    @Column(name = "claim_key", length = 40)
    private String claimKey;

    @Id
    @Column(name = "entry_id")
    private UUID entryId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID reportVersionId;
        private String claimKey;
        private UUID entryId;
    }
}
