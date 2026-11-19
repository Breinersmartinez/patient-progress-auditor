<#--
  Login de PGRP. Reproduce el formulario "equipo" de docs/07-mockups/login-sanitas/auditor_login_sanitas.html (render(), vista form).
  Los campos conservan los id del prototipo (correo, clave) y los name que espera Keycloak
  (username, password).
-->
<#import "template.ftl" as layout>
<#-- Primera carga: el prototipo arranca con "Recuérdame" marcado. Tras un intento, se respeta lo enviado. -->
<#assign primerIntento = !(login.username??)>
<#assign recordar = primerIntento || login.rememberMe??>
<#-- Aviso del servidor. Keycloak lo manda como mensaje general y, para credenciales, también como
     error de los campos username/password: se toma el general y, si no hay, el de los campos.
     El tipo de aviso se reconoce por el texto, que sale de messages_es.properties. -->
<#assign avisoTexto = (message.summary)!''>
<#assign avisoTipo = (message.type)!''>
<#if !avisoTexto?has_content && messagesPerField.existsError('username','password')>
  <#assign avisoTexto = messagesPerField.getFirstError('username','password')>
  <#assign avisoTipo = 'error'>
</#if>
<#assign bloqueada = avisoTexto == msg('accountTemporarilyDisabledMessage')>
<#assign expirada = avisoTexto == msg('loginTimeout')>
<#if bloqueada>
<#-- Bloqueo por intentos fallidos: pantalla completa "Demasiados intentos" (429) de
     DOC-PGRP/pgrp_pantallas_sistema.html en lugar del formulario. -->
<@layout.pantalla marco="estado" titulo=msg('ppa429Titulo')>
<@layout.contorno marca=msg('ppaMarca429') glifo="escudo" codigo=msg('ppa429Codigo') titulo=msg('ppa429Titulo') texto=msg('ppa429Texto') nAcciones=realm.resetPasswordAllowed?then(2, 1)>
<#if realm.resetPasswordAllowed><@layout.boton texto=msg('ppaBtnRestablecerMiContrasena') icono="llave" principal=true href=url.loginResetCredentialsUrl/></#if><@layout.boton texto=msg('ppaBtnContactarSoporte') icono="soporte" principal=!realm.resetPasswordAllowed accion="soporte"/>
</@layout.contorno>
</@layout.pantalla>
<#else>
<@layout.registrationLayout ppa=true; section>
<#if section = "form">
      <div class="fhead" data-antetitulo="${msg('ppaAntetitulo')}"><h2>${msg('ppaLoginTitulo')}</h2><p>${msg('ppaLoginSubtitulo')}</p></div><#if expirada><@layout.aviso tipo="info" texto=avisoTexto /><#elseif avisoTexto?has_content><@layout.aviso tipo=(avisoTipo == 'error')?then('error', 'info') texto=avisoTexto /></#if>
      <form class="form" novalidate data-form="equipo" id="kc-form-login" action="${url.loginAction}" method="post"<#if avisoTipo == 'error' && !expirada> data-foco="clave"</#if>>
        <fieldset>
          <div class="field"><label for="correo">${msg('ppaCorreo')}</label>
    <div class="input"><i data-icono="correo"></i>
      <input id="correo" name="username" type="email" value="${login.username!''}" placeholder="${msg('ppaCorreoPh')}" autocomplete="username">
    </div></div>
          <div class="field"><label for="clave">${msg('ppaClave')}</label>
    <div class="input"><i data-icono="candado"></i>
      <input id="clave" name="password" type="password" value="" placeholder="${msg('ppaClavePh')}" autocomplete="current-password">
      <button class="ibtn" type="button" data-act="ver" aria-label="${msg('ppaMostrarClave')}" aria-pressed="false"><i data-icono="ojo"></i></button>
    </div></div>
          <#if realm.rememberMe || realm.resetPasswordAllowed>
          <div class="row"><#if realm.rememberMe><label class="check"><input type="checkbox" name="rememberMe"<#if recordar> checked</#if>>${msg('ppaRecuerdame')}</label></#if>
          <#if realm.resetPasswordAllowed><a class="link" href="${url.loginResetCredentialsUrl}">${msg('ppaOlvidaste')}</a></#if></div>
          </#if>
        </fieldset>
        <input type="hidden" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>>
        <button class="btn btn--primary btn--block" type="submit"><span class="btn__icon"><i data-icono="ingresar"></i><i data-icono="anillo"></i></span><span class="btn__text"><span class="btn__idle">${msg('ppaIngresar')}</span><span class="btn__busy">${msg('ppaIngresando')}</span></span></button>
      </form>
</#if>
</@layout.registrationLayout>
</#if>
