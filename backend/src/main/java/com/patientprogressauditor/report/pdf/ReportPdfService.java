package com.patientprogressauditor.report.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.patientprogressauditor.report.Report;
import com.patientprogressauditor.report.ReportService;
import com.patientprogressauditor.report.ReportService.Detail;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.web.FieldException;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.SimplePdfExporterConfiguration;
import net.sf.jasperreports.pdf.JRPdfExporter;

// El PDF de un informe APROBADO (RF-37, CU-09, D13: JasperReports). La regla de quien puede verlo es la del informe
// (ReportService.detail: el medico solo ve lo aprobado y lo demas «no existe»), y ademas aqui solo se imprime un informe
// APROBADO: nada de borradores con marca. Imprime la version que se aprobo, no la que redacto el modelo.
@Service
public class ReportPdfService {

    private static final String TEMPLATE = "/reports/informe.jrxml";

    public record Pdf(byte[] bytes, String fileName) {
    }

    private final ReportService reports;
    // La plantilla se compila una vez (con el compilador JDT: la imagen final es un JRE, sin javac).
    private volatile JasperReport compiled;

    public ReportPdfService(ReportService reports) {
        this.reports = reports;
    }

    public Pdf render(UUID id, DemoPrincipal principal) {
        Detail detail = reports.detail(id, principal);
        if (detail.report().status() != Report.Status.APROBADO) {
            String state = switch (detail.report().status()) {
                case PENDIENTE_REVISION -> "pendiente de revisión";
                case INCOMPLETO -> "incompleto";
                case RECHAZADO -> "rechazado";
                case APROBADO -> "aprobado";
            };
            throw new FieldException(HttpStatus.CONFLICT, "status", "Solo se puede imprimir un informe aprobado. Este está " + state + ".");
        }
        if (detail.version().content() == null) {
            throw new FieldException(HttpStatus.CONFLICT, "status", "Este informe no tiene un contenido estructurado que se pueda imprimir.");
        }
        ReportPdfBlocks.Document document = ReportPdfBlocks.of(detail);
        try {
            JasperPrint print = JasperFillManager.fillReport(template(), document.parameters(), new JRMapCollectionDataSource(
                    new ArrayList<Map<String, ?>>(document.blocks())));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            SimplePdfExporterConfiguration configuration = new SimplePdfExporterConfiguration();
            configuration.setMetadataTitle((String) document.parameters().get("titulo") + " — " + detail.report().patientAlias());
            configuration.setMetadataAuthor("Patient Progress Auditor");
            configuration.setMetadataSubject("Informe " + detail.report().id());
            exporter.setConfiguration(configuration);
            exporter.exportReport();
            return new Pdf(out.toByteArray(), document.fileName());
        } catch (JRException e) {
            throw new IllegalStateException("No se pudo armar el PDF del informe " + id, e);
        }
    }

    private JasperReport template() throws JRException {
        JasperReport report = compiled;
        if (report == null) {
            synchronized (this) {
                if (compiled == null) {
                    try (InputStream in = ReportPdfService.class.getResourceAsStream(TEMPLATE)) {
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
