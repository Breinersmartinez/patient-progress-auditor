<#--
  "Crea tu contraseña" con el diseño A "Pasos arriba" de DOC-PGRP/pgrp_alta_propuestas.html.
  Sirve para el alta (enlace del admin) y para la recuperación con código. Lo que cambia es el
  primer paso ("Correo recibido" o "Código verificado"): lo decide ppa-pantallas.js con la marca
  ppa-codigo-ok que deja la pantalla del código. Sin marca, quedan los pasos del alta.
  Errores del servidor: password-confirm -> "No coinciden"; password -> "Contraseña débil".
  logout-sessions=on conserva el comportamiento por defecto de Keycloak (cierra las otras sesiones).
-->
<#import "template.ftl" as layout>
<#assign correo = username!''>
<#-- Las reglas de la política llegan como mensaje general que empieza igual ("Contraseña incorrecta:"). -->
<#assign prefijoPolitica = msg('invalidPasswordMinLengthMessage')?keep_before(':')>
<#if messagesPerField.existsError('password-confirm') || ((message.summary)!'') == msg('notMatchPasswordMessage')>
  <#assign err = 'match'>
<#elseif messagesPerField.existsError('password') || ((message.summary)!'')?starts_with(prefijoPolitica)>
  <#assign err = 'weak'>
<#elseif (message.type)! == 'error'>
  <#assign err = 'otro'>
<#else>
  <#assign err = ''>
</#if>
<#assign errTexto = {'match': msg('ppaAltaNoCoinciden'), 'weak': msg('ppaAltaDebil'), 'otro': (message.summary)!'', '': ''}[err]>
<#macro campo id nombre etiqueta ph malo>
<div class="fld"><label class="lbl" for="${id}">${etiqueta}</label><div class="inp<#if malo> bad</#if>"><i data-icono="candado"></i><input id="${id}" name="${nombre}" type="password" autocomplete="new-password" placeholder="${ph}" value=""<#if malo> aria-invalid="true"</#if>><button class="eye" type="button" data-eye="${id}" aria-label="${msg('ppaMostrarClave')}"><i data-icono="ojo"></i></button></div></div>
</#macro>
<@layout.pantalla marco="alta" titulo=msg('ppaAltaTitulo')>
<@layout.alta estados=['done', 'cur', 'todo'] nombres=[msg('ppaPasoCorreo'), msg('ppaPasoCrear'), msg('ppaPasoIngresar')]><div class="cen" data-cabeza><h1 style="margin-top:0px">${msg('ppaAltaTitulo')}</h1><p class="sub">${msg('ppaAltaSubtitulo')}</p></div><form class="pform" id="pform" action="${url.loginAction}" method="post" novalidate data-paso-recuperar="${msg('ppaPasoCodigo')}" data-exito-titulo="${msg('ppaCodigoExito1')} ${msg('ppaCodigoExito2')}" data-exito-texto="${msg('ppaCodigoExitoTexto')}"><input type="hidden" name="username" value="${correo}" autocomplete="username"><input type="hidden" name="logout-sessions" value="on">
  <div class="acct"><span class="av">${correo?substring(0, [correo?length, 1]?min)?upper_case}</span><span>${correo}</span></div>
  <@campo id="pw1" nombre="password-new" etiqueta=msg('ppaAltaClaveNueva') ph=msg('ppaAltaClaveNuevaPh') malo=(err == 'weak')/>
  <div class="reqw" id="reqw"><ul class="req" id="req" aria-label="${msg('ppaAltaRequisitosAria')}" aria-live="polite"><#list ['ppaReqLargo', 'ppaReqMayMin', 'ppaReqNumero', 'ppaReqEspecial'] as r><li data-req="${r?index}"><span class="y"><i data-icono="ok"></i></span><span class="n"><i data-icono="vacio"></i></span>${msg(r)}</li></#list></ul></div>
  <@campo id="pw2" nombre="password-confirm" etiqueta=msg('ppaAltaRepetir') ph=msg('ppaAltaRepetirPh') malo=(err == 'match')/>
  <p class="ferr" id="ferr" role="alert"<#if !err?has_content> hidden</#if> data-match="${msg('ppaAltaNoCoinciden')}" data-weak="${msg('ppaAltaDebil')}"><i data-icono="alerta"></i><span>${errTexto}</span></p>
  <button class="btn btn--primary" type="submit" id="guardar" data-ocupado="${msg('ppaAltaGuardando')}"><i data-icono="guardar"></i><span>${msg('ppaAltaGuardar')}</span></button><span class="slot" aria-hidden="true"></span></form></@layout.alta>
</@layout.pantalla>
