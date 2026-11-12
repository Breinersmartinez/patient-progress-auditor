package com.patientprogressauditor.consultation.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.patientprogressauditor.consultation.ConsultationService;
import com.patientprogressauditor.consultation.ConsultationService.ConsultationDetail;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.user.AppUserRepository;
import com.patientprogressauditor.web.FieldException;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.pdf.SimplePdfExporterConfiguration;

// El PDF de la consulta del medico (D31, RF-46, RN-12): «Descargar PDF», como en el mockup, con la marca «Consulta del
// médico» en la cabecera y en el pie (consulta.jrxml, aparte de informe.jrxml porque esa plantilla trae «Aprobado por»
// fijo en el XML, falso aqui). La regla de quien la ve es la de la consulta (ConsultationService.findVisible): solo su autor.
@Service
public class ConsultationPdfService {

    private static final String TEMPLATE = "/reports/consulta.jrxml";

    public record Pdf(byte[] bytes, String fileName) {
    }

    private final ConsultationService consultations;
    private final AppUserRepository users;
    private volatile JasperReport compiled;

    public ConsultationPdfService(ConsultationService consultations, AppUserRepository users) {
        this.consultations = consultations;
        this.users = users;
    }

    public Pdf render(UUID id, DemoPrincipal principal) {
        ConsultationDetail detail = consultations.detail(id, principal);
        if (detail.content() == null) {
            throw new FieldException(HttpStatus.CONFLICT, "status", "Esta consulta no tiene un contenido estructurado que se pueda imprimir.");
        }
        String doctorName = users.findById(principal.id()).map(u -> u.getDisplayName()).orElse(principal.displayName());
        ConsultationPdfBlocks.Document document = ConsultationPdfBlocks.of(detail, doctorName);
        try {
            JasperPrint print = JasperFillManager.fillReport(template(), document.parameters(), new JRMapCollectionDataSource(
                    new ArrayList<Map<String, ?>>(document.blocks())));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            SimplePdfExporterConfiguration configuration = new SimplePdfExporterConfiguration();
            configuration.setMetadataTitle((String) document.parameters().get("titulo") + " — " + detail.patientAlias());
            configuration.setMetadataAuthor("Patient Progress Auditor");
            configuration.setMetadataSubject("Consulta " + detail.id());
            exporter.setConfiguration(configuration);
            exporter.exportReport();
            return new Pdf(out.toByteArray(), document.fileName());
        } catch (JRException e) {
            throw new IllegalStateException("No se pudo armar el PDF de la consulta " + id, e);
        }
    }

    private JasperReport template() throws JRException {
        JasperReport report = compiled;
        if (report == null) {
            synchronized (this) {
                if (compiled == null) {
                    try (InputStream in = ConsultationPdfService.class.getResourceAsStream(TEMPLATE)) {
                        compiled = JasperCompileManager.compileReport(in);
                    } catch (IOException e) {
                        throw new IllegalStateException("No se pudo leer la plantilla " + TEMPLATE, e);
                    }
                }
                report = compiled;
            }
        }
        return report;
    }
}
