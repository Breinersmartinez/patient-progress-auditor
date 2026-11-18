<#--
  Código de recuperación (lo dibuja la extensión ppa-codigo-recuperacion). Reproduce
  DOC-PGRP/pgrp_otp.html: 6 cajas, animación "verificando" al enviar y contador de reenvío.
  Datos que manda la extensión: ppaCorreoOculto ("n…@vortexbird.com") y ppaEsperaReenvio (segundos).
  El input real (#codigo) queda invisible sobre las cajas; ppa-pantallas.js copia cada dígito a su caja.
-->
<#import "template.ftl" as layout>
<#assign error = (message.summary)!''>
<@layout.pantalla marco="otp" titulo=msg('ppaCodigoPestana')>
<section class="c-wrap">
    <div><img class="hlogo hl" src="${url.resourcesPath}/img/logo-claro.png" alt="${msg('ppaLogoAlt')}"><img class="hlogo hd" src="${url.resourcesPath}/img/logo-oscuro.png" alt="" aria-hidden="true"></div>
    <div class="c-mark big outline otp" aria-hidden="true">${msg('ppaMarcaOtp')}</div>
    <form class="c-body otp" id="view" action="${url.loginAction}" method="post" novalidate data-espera="${ppaEsperaReenvio}"<#if error?has_content> data-error</#if>>
      <div class="ibox" id="ibox" aria-hidden="true"><span class="ic key" id="icKey"><svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="8" cy="15" r="4"/><path d="m11 12 8-8m-3 3 3 3m-6-1 2 2"/></svg></span><span class="ic ok" id="icOk"><svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3.5 5 6v5.5c0 4.2 2.9 7.3 7 9 4.1-1.7 7-4.8 7-9V6z"/><path d="m8.8 12.2 2.3 2.3 4.2-4.4"/></svg></span></div>
      <h1 class="swap" aria-hidden="true"><span id="tA">${msg('ppaCodigoTitulo1')} <b>${msg('ppaCodigoTitulo2')}</b></span><span id="tB" class="s">${msg('ppaCodigoExito1')} <b>${msg('ppaCodigoExito2')}</b></span></h1>
      <p class="desc swap" aria-hidden="true"><span id="sA">${msg('ppaCodigoTexto', ppaCorreoOculto)}</span><span id="sB">${msg('ppaCodigoExitoTexto')}</span></p>
      <span class="sr" aria-live="polite" id="sr">${msg('ppaCodigoTitulo1')} ${msg('ppaCodigoTitulo2')}</span>
      <#if error?has_content><p class="ppa-ferr" role="alert"><svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4 3 19h18z"/><path d="M12 10v4m0 3v.01"/></svg><span>${error}</span></p></#if>
      <div class="ostage" id="stage">
        <input class="ppa-codigo" id="codigo" name="codigo" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" aria-label="${msg('ppaCodigoAria')}"<#if error?has_content> aria-invalid="true"</#if>>
        <div class="halo" id="halo" aria-hidden="true"></div>
        <#list 1..6 as i><div class="box" aria-hidden="true"><span class="d"></span><i class="cur"></i></div>
        </#list><div class="core" id="core" aria-hidden="true"><span class="fill"></span><i class="dot"></i><i class="spk"></i><i class="spk"></i><i class="spk"></i><i class="spk"></i><svg viewBox="0 0 24 24"><path pathLength="1" d="m5.5 12.5 4.2 4.2 8.8-9.4"/></svg></div>
        <div class="vring" id="ring" aria-hidden="true"></div>
      </div>
      <p class="resend">${msg('ppaCodigoNoRecibiste')} <b id="cnt" data-texto="${msg('ppaCodigoReenviarEn')}"></b><button class="ppa-reenviar" type="submit" form="ppa-form-reenviar" id="reenviar" hidden>${msg('ppaCodigoReenviar')}</button></p>
      <div class="actions"><button class="btn btn--primary obtn" id="btn" type="submit" data-s="idle">
        <span class="l i">${msg('ppaCodigoVerificar')} <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6"/></svg></span>
        <span class="l v"><i class="spin"></i>${msg('ppaCodigoVerificando')}</span>
        <span class="l o"><svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3.5 5 6v5.5c0 4.2 2.9 7.3 7 9 4.1-1.7 7-4.8 7-9V6z"/><path d="m8.8 12.2 2.3 2.3 4.2-4.4"/></svg>${msg('ppaCodigoVerificado')}</span>
      </button></div>
    </form>
    <div class="foot">${msg('ppaPie')}</div>
</section>
<#-- Formulario aparte para "Reenviar": así Enter en el código siempre envía "Verificar y continuar". -->
<form id="ppa-form-reenviar" action="${url.loginAction}" method="post" hidden><input type="hidden" name="reenviar" value="1"></form>
</@layout.pantalla>
