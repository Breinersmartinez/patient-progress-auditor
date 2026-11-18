package com.patientprogressauditor.keycloak;

import java.util.List;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Registra el paso "Auditor - Código de recuperación por correo" en la consola de Keycloak.
 *
 * <p>Los tiempos se pueden cambiar al arrancar Keycloak, sin recompilar (útil para probar el vencimiento):
 * {@code --spi-authenticator--ppa-codigo-recuperacion--vigencia-segundos=900},
 * {@code ...--espera-reenvio-segundos=120} y {@code ...--intentos=5}.
 */
public class CodigoRecuperacionAuthenticatorFactory implements AuthenticatorFactory {

    public static final String ID = "ppa-codigo-recuperacion";

    private CodigoRecuperacionAuthenticator instancia;

    @Override
    public void init(Config.Scope config) {
        instancia = new CodigoRecuperacionAuthenticator(
                config.getInt("vigencia-segundos", 900),
                config.getInt("espera-reenvio-segundos", 120),
                config.getInt("intentos", 5));
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return instancia;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Auditor - Código de recuperación por correo";
    }

    @Override
    public String getHelpText() {
        return "Manda un código de 6 dígitos al correo del usuario y lo valida antes de dejarlo cambiar la contraseña. "
                + "Reemplaza a 'Send Reset Email' en el flujo de reset credentials.";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[] {Requirement.REQUIRED};
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
