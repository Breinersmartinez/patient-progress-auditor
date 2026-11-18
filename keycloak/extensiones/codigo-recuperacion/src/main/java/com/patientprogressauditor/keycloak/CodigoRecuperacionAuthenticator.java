package com.patientprogressauditor.keycloak;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Paso del flujo de "olvidé mi contraseña": genera un código de 6 dígitos, lo manda por correo y lo valida.
 *
 * <p>Todo el estado vive en las notas de la sesión de autenticación (del lado del servidor). El código nunca se
 * guarda en claro: solo su hash con una sal aleatoria. Si el correo escrito no existe, la pantalla se muestra igual
 * y ningún código sirve, para no revelar qué correos tienen cuenta.
 */
public class CodigoRecuperacionAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(CodigoRecuperacionAuthenticator.class);

    static final String PLANTILLA = "login-codigo.ftl";
    static final String PLANTILLA_CORREO = "ppa-codigo.ftl";
    static final String CAMPO_CODIGO = "codigo";
    static final String CAMPO_REENVIAR = "reenviar";

    private static final String NOTA_HASH = "ppa.codigo.hash";
    private static final String NOTA_SAL = "ppa.codigo.sal";
    private static final String NOTA_VENCE = "ppa.codigo.vence";
    private static final String NOTA_INTENTOS = "ppa.codigo.intentos";
    private static final String NOTA_ENVIADO = "ppa.codigo.enviado";
    private static final String NOTA_PARA = "ppa.codigo.para";

    private static final SecureRandom AZAR = new SecureRandom();

    private final long vigenciaMs;
    private final long esperaReenvioMs;
    private final int intentosMaximos;

    CodigoRecuperacionAuthenticator(int vigenciaSegundos, int esperaReenvioSegundos, int intentosMaximos) {
        this.vigenciaMs = vigenciaSegundos * 1000L;
        this.esperaReenvioMs = esperaReenvioSegundos * 1000L;
        this.intentosMaximos = intentosMaximos;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticationSessionModel sesion = context.getAuthenticationSession();
        String para = destinatario(context);
        // Recargar la página no manda otro código; uno nuevo solo si no hay, si cambió el correo o si está anulado.
        if (sesion.getAuthNote(NOTA_HASH) == null || !para.equals(sesion.getAuthNote(NOTA_PARA))) {
            if (!generarYEnviar(context, para)) {
                return;
            }
        }
        context.challenge(pantalla(context, anulado(sesion) ? "ppaCodigoNoSirve" : null));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        AuthenticationSessionModel sesion = context.getAuthenticationSession();
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        if (sesion.getAuthNote(NOTA_HASH) == null) {
            // Sin código en la sesión (por ejemplo, un envío viejo después de terminar): se empieza de nuevo.
            authenticate(context);
            return;
        }

        if (form.containsKey(CAMPO_REENVIAR)) {
            if (segundosParaReenviar(sesion) > 0) {
                // Petición adelantada (el botón está desactivado en la página): no se manda nada.
                context.challenge(pantalla(context, null));
                return;
            }
            if (generarYEnviar(context, destinatario(context))) {
                context.challenge(pantalla(context, null));
            }
            return;
        }

        if (anulado(sesion)) {
            context.challenge(pantalla(context, "ppaCodigoNoSirve"));
            return;
        }

        String escrito = form.getFirst(CAMPO_CODIGO);
        boolean correcto = context.getUser() != null && escrito != null && escrito.matches("\\d{6}")
                && MessageDigest.isEqual(hash(sesion.getAuthNote(NOTA_SAL), escrito).getBytes(StandardCharsets.UTF_8),
                        sesion.getAuthNote(NOTA_HASH).getBytes(StandardCharsets.UTF_8));
        if (correcto) {
            limpiar(sesion);
            context.success();
            return;
        }

        int intentos = Integer.parseInt(sesion.getAuthNote(NOTA_INTENTOS)) + 1;
        sesion.setAuthNote(NOTA_INTENTOS, String.valueOf(intentos));
        context.challenge(pantalla(context, anulado(sesion) ? "ppaCodigoNoSirve" : "ppaCodigoIncorrecto"));
    }

    /** Genera un código nuevo (el anterior deja de servir) y lo manda si el usuario existe. Devuelve false si falló el envío. */
    private boolean generarYEnviar(AuthenticationFlowContext context, String para) {
        AuthenticationSessionModel sesion = context.getAuthenticationSession();
        String codigo = String.format("%06d", AZAR.nextInt(1_000_000));
        String sal = sal();
        long ahora = System.currentTimeMillis();
        sesion.setAuthNote(NOTA_SAL, sal);
        sesion.setAuthNote(NOTA_HASH, hash(sal, codigo));
        sesion.setAuthNote(NOTA_VENCE, String.valueOf(ahora + vigenciaMs));
        sesion.setAuthNote(NOTA_INTENTOS, "0");
        sesion.setAuthNote(NOTA_ENVIADO, String.valueOf(ahora));
        sesion.setAuthNote(NOTA_PARA, para);

        UserModel usuario = context.getUser();
        if (usuario == null || usuario.getEmail() == null) {
            return true;
        }
        try {
            KeycloakSession session = context.getSession();
            RealmModel realm = context.getRealm();
            Map<String, Object> datos = new HashMap<>();
            datos.put("codigo", codigo);
            datos.put("minutos", vigenciaMs / 60_000);
            session.getProvider(EmailTemplateProvider.class)
                    .setRealm(realm)
                    .setUser(usuario)
                    .setAuthenticationSession(sesion)
                    .send("ppaCodigoAsunto", List.of(), PLANTILLA_CORREO, datos);
            return true;
        } catch (EmailException e) {
            LOG.errorf(e, "No se pudo enviar el código de recuperación al usuario %s", usuario.getId());
            limpiar(sesion);
            context.failure(org.keycloak.authentication.AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("ppaCodigoErrorEnvio").createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            return false;
        }
    }

    private Response pantalla(AuthenticationFlowContext context, String error) {
        AuthenticationSessionModel sesion = context.getAuthenticationSession();
        LoginFormsProvider form = context.form()
                .setAttribute("ppaCorreoOculto", ocultar(destinatario(context)))
                .setAttribute("ppaEsperaReenvio", segundosParaReenviar(sesion));
        if (error != null) {
            form.setError(error);
        }
        return form.createForm(PLANTILLA);
    }

    private boolean anulado(AuthenticationSessionModel sesion) {
        return System.currentTimeMillis() >= Long.parseLong(sesion.getAuthNote(NOTA_VENCE))
                || Integer.parseInt(sesion.getAuthNote(NOTA_INTENTOS)) >= intentosMaximos;
    }

    private long segundosParaReenviar(AuthenticationSessionModel sesion) {
        long falta = Long.parseLong(sesion.getAuthNote(NOTA_ENVIADO)) + esperaReenvioMs - System.currentTimeMillis();
        return Math.max(0, (falta + 999) / 1000);
    }

    /** El correo tal como lo escribió la persona (exista o no la cuenta). */
    private static String destinatario(AuthenticationFlowContext context) {
        String escrito = context.getAuthenticationSession().getAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME);
        if (escrito != null && !escrito.isBlank()) {
            return escrito.trim();
        }
        UserModel usuario = context.getUser();
        return usuario != null && usuario.getEmail() != null ? usuario.getEmail() : "";
    }

    /** "nombre@demo.local" -> "n…@vortexbird.com". */
    static String ocultar(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 0) {
            return correo.isEmpty() ? "" : correo.charAt(0) + "…";
        }
        return correo.charAt(0) + "…" + correo.substring(arroba);
    }

    private static void limpiar(AuthenticationSessionModel sesion) {
        for (String nota : new String[] {NOTA_HASH, NOTA_SAL, NOTA_VENCE, NOTA_INTENTOS, NOTA_ENVIADO, NOTA_PARA}) {
            sesion.removeAuthNote(nota);
        }
    }

    private static String sal() {
        byte[] b = new byte[16];
        AZAR.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private static String hash(String sal, String codigo) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(sha.digest((sal + codigo).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean requiresUser() {
        // En recuperación el usuario puede no existir: la pantalla se muestra igual.
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
