<#--
  Página de error de Keycloak con el diseño "Contorno" de DOC-PGRP/pgrp_pantallas_sistema.html.
  La pantalla se elige por la clave del mensaje (comprobado en Keycloak 26.8.2, tarea 3 del plan):
    expiredActionTokenNoSessionMessage / expiredActionTokenSessionExistsMessage -> Activación vencida
    expiredActionMessage   -> Activación ya usada (el enlace se abre después de usarlo)
    accountDisabledMessage -> Cuenta desactivada
    cualquier otra         -> 500
  "Error al guardar" del prototipo no tiene un mensaje propio en Keycloak: sale el 500 (supuesto 2 de la spec).
-->
<#import "template.ftl" as layout>
<#assign resumen = (message.summary)!''>
<#if resumen == msg('expiredActionTokenNoSessionMessage') || resumen == msg('expiredActionTokenSessionExistsMessage')>
  <#assign caso = 'vencida'>
<#elseif resumen == msg('expiredActionMessage')>
  <#assign caso = 'usada'>
<#elseif resumen == msg('accountDisabledMessage')>
  <#assign caso = 'desactivada'>
<#else>
  <#assign caso = '500'>
</#if>
<#-- Inicio de la aplicación de la que vino la persona, si Keycloak la conoce. -->
<#assign inicio = (client.baseUrl)!''>
<#if caso == 'vencida'>
<@layout.pantalla marco="estado" titulo=msg('ppaVencidaTitulo')>
<@layout.contorno marca=msg('ppaMarcaVencida') glifo="reloj" codigo=msg('ppaVencidaCodigo') titulo=msg('ppaVencidaTitulo') texto=msg('ppaVencidaTexto') nAcciones=2>
<@layout.boton texto=msg('ppaBtnPedirEnlace') icono="correo" principal=true accion="soporte"/><@layout.boton texto=msg('ppaBtnVolverIngresar') icono="entrar" href=url.loginUrl/>
</@layout.contorno>
</@layout.pantalla>
<#elseif caso == 'usada'>
<@layout.pantalla marco="estado" titulo=msg('ppaUsadaTitulo')>
<@layout.contorno marca=msg('ppaMarcaUsada') glifo="enlace" codigo=msg('ppaUsadaCodigo') titulo=msg('ppaUsadaTitulo') texto=msg('ppaUsadaTexto') nAcciones=2>
<@layout.boton texto=msg('ppaBtnIniciarSesion') icono="entrar" principal=true href=url.loginUrl/><@layout.boton texto=msg('ppaBtnRestablecerContrasena') icono="llave" href=url.loginResetCredentialsUrl/>
</@layout.contorno>
</@layout.pantalla>
<#elseif caso == 'desactivada'>
<@layout.pantalla marco="estado" titulo=msg('ppaDesactivadaTitulo')>
<@layout.contorno marca=msg('ppaMarcaDesactivada') glifo="candado" codigo=msg('ppaDesactivadaCodigo') titulo=msg('ppaDesactivadaTitulo') texto=msg('ppaDesactivadaTexto') nAcciones=1>
<@layout.boton texto=msg('ppaBtnContactarSoporte') icono="soporte" principal=true accion="soporte"/>
</@layout.contorno>
</@layout.pantalla>
<#else>
<@layout.pantalla marco="estado" titulo=msg('ppa500Titulo')>
<@layout.contorno marca=msg('ppaMarca500') glifo="alerta" codigo=msg('ppa500Codigo') titulo=msg('ppa500Titulo') texto=msg('ppa500Texto') nAcciones=inicio?has_content?then(2, 1)>
<@layout.boton texto=msg('ppaBtnIntentarDeNuevo') icono="recargar" principal=true accion="recargar" ocupado=msg('ppaBtnReintentando')/><#if inicio?has_content><@layout.boton texto=msg('ppaBtnIrInicio') icono="inicio" href=inicio/></#if>
</@layout.contorno>
</@layout.pantalla>
</#if>
