<#--
  Avisos de Keycloak (info.ftl):
    página de confirmación de un enlace de acción (trae actionUri) -> "Continuar acción"
    emailVerifiedMessage                                           -> "Correo verificado"
    cualquier otro aviso                                           -> "Cuenta actualizada" (Contorno)
  "Contraseña creada" (diseño del alta): mismo mensaje que "Cuenta actualizada"; se elige en el
  navegador con la marca ppa-clave-creada (ver alternativaAlta en template.ftl).
-->
<#import "template.ftl" as layout>
<#assign resumen = (message.summary)!''>
<#-- Destino de "Volver a PGRP": la aplicación de la que vino la persona, si Keycloak la conoce. -->
<#assign volver = ''>
<#if pageRedirectUri?has_content><#assign volver = pageRedirectUri>
<#elseif (client.baseUrl)?has_content><#assign volver = client.baseUrl></#if>
<#if actionUri?has_content>
<@layout.pantalla marco="estado" titulo=msg('ppaAccionTitulo')>
<@layout.contorno marca=msg('ppaMarcaOk') glifo="llave" codigo=msg('ppaAccionCodigo') titulo=msg('ppaAccionTitulo') texto=msg('ppaAccionTexto') nAcciones=2>
<@layout.boton texto=msg('ppaBtnContinuarAccion') icono="entrar" principal=true href=actionUri/><@layout.boton texto=msg('ppaBtnCancelar') icono="atras" href=url.loginUrl/>
</@layout.contorno>
</@layout.pantalla>
<#elseif resumen == msg('emailVerifiedMessage')>
<@layout.pantalla marco="estado" titulo=msg('ppaCorreoVerifTitulo')>
<@layout.contorno marca=msg('ppaMarcaOk') glifo="ok" codigo=msg('ppaCorreoVerifCodigo') titulo=msg('ppaCorreoVerifTitulo') texto=msg('ppaCorreoVerifTexto') nAcciones=volver?has_content?then(1, 0)>
<#if volver?has_content><@layout.boton texto=msg('ppaBtnContinuarApp') icono="entrar" principal=true href=volver/></#if>
</@layout.contorno>
</@layout.pantalla>
<#else>
<#assign actualizada = resumen == msg('accountUpdatedMessage') || !resumen?has_content>
<#assign texto = actualizada?then(msg('ppaCuentaTexto'), resumen)>
<@layout.pantalla marco="estado" titulo=msg('ppaCuentaTitulo') alternativaAlta=actualizada>
<@layout.contorno marca=msg('ppaMarcaOk') glifo="ok" codigo=msg('ppaCuentaCodigo') titulo=msg('ppaCuentaTitulo') texto=texto nAcciones=volver?has_content?then(1, 0)>
<#if volver?has_content><@layout.boton texto=msg('ppaBtnVolverApp') icono="inicio" principal=true href=volver/></#if>
</@layout.contorno>
<#if actualizada>
<#-- "Contraseña creada" (diseño A de ppa_alta_propuestas.html). Reemplaza al contenido de arriba
     solo si el navegador viene de crear la contraseña (ver alternativaAlta en template.ftl). -->
<template id="ppa-creada" data-titulo="${msg('ppaTituloPestana', msg('ppaCreadaTitulo'))}"><@layout.alta estados=['done', 'done', 'cur'] nombres=[msg('ppaPasoCorreo'), msg('ppaPasoCrear'), msg('ppaPasoIngresar')]><div class="cen"><span class="ibox ok"><i data-icono="ok"></i></span><h1 style="margin-top:12px">${msg('ppaCreadaTitulo')}</h1><p class="sub" data-creada-texto="${msg('ppaCreadaTexto', '{0}')}">${msg('ppaCreadaTextoSinCorreo')}</p></div><div class="acts"><#if volver?has_content><a class="btn btn--primary" href="${volver}" data-creada><i data-icono="entrar"></i>${msg('ppaBtnIniciarSesion')}</a></#if><span class="slot" aria-hidden="true"></span></div></@layout.alta></template>
<script>
  if (document.documentElement.dataset.variante === 'alta') {
    document.body.dataset.marco = 'alta';
    const f = document.getElementById('frame'); f.removeAttribute('data-pal'); f.removeAttribute('data-layout');
    const t = document.getElementById('ppa-creada');
    document.title = t.dataset.titulo;
    document.getElementById('stage').replaceChildren(t.content.cloneNode(true));
  }
</script>
</#if>
</@layout.pantalla>
</#if>
