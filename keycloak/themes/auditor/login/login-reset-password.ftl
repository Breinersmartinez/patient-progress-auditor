<#--
  Restablecer contraseña de PGRP. Reproduce la vista "recuperar" de docs/07-mockups/login-sanitas/auditor_login_sanitas.html.
  "Volver a ingresar" es un enlace a la URL de login que da Keycloak.
-->
<#import "template.ftl" as layout>
<#assign avisoTexto = (message.summary)!''>
<#if !avisoTexto?has_content && messagesPerField.existsError('username')>
  <#assign avisoTexto = messagesPerField.getFirstError('username')>
</#if>
<@layout.registrationLayout ppa=true; section>
<#if section = "form">
      <div class="fhead" data-antetitulo="${msg('ppaAntetitulo')}"><h2>${msg('ppaRecuperarTitulo')}</h2><p>${msg('ppaRecuperarSubtitulo')}</p></div><#if avisoTexto?has_content><@layout.aviso tipo=(((message.type)!'error') == 'error')?then('error', 'info') texto=avisoTexto /></#if>
      <form class="form" novalidate data-form="recuperar" id="kc-reset-password-form" action="${url.loginAction}" method="post" data-foco="correo">
        <div class="field"><label for="correo">${msg('ppaCorreo')}</label>
    <div class="input"><i data-icono="correo"></i>
      <input id="correo" name="username" type="email" value="${auth.attemptedUsername!''}" placeholder="${msg('ppaCorreoPh')}" autocomplete="email">
    </div></div>
        <button class="btn btn--primary btn--block" type="submit"><i data-icono="enviar"></i><span class="btn__text"><span class="btn__idle">${msg('ppaEnviarEnlace')}</span><span class="btn__busy">${msg('ppaEnviando')}</span></span></button>
        <a class="link" href="${url.loginUrl}"><i data-icono="volver"></i>${msg('ppaVolverIngresar')}</a>
      </form>
</#if>
</@layout.registrationLayout>
