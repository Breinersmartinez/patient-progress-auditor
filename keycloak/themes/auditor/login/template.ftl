<#--
  Marco común del login de PGRP. Reproduce la estructura de docs/07-mockups/login-sanitas/auditor_login_sanitas.html:
  botón claro/oscuro, panel de marca (aside.pan), logo vertical, contenido (div.fwrap) y pie.

  ppa=true  -> la plantilla que llama (login.ftl, login-reset-password.ftl) dibuja todo el
                contenido de .fwrap en la sección "form".
  ppa=false -> pantallas heredadas de keycloak.v2: se envuelven con encabezado, aviso y formulario
                estándar dentro del marco.
-->
<#macro aviso tipo texto icono="">
  <#-- Aviso del prototipo: error (ícono alerta) o información (ícono info). <#nested> agrega una acción. -->
  <#local ic = icono?has_content?then(icono, (tipo == 'error')?then('alerta', 'info'))>
  <div class="aviso aviso--${tipo}" role="${(tipo == 'error')?then('alert', 'status')}"><i data-icono="${ic}"></i><div>${texto}<#nested></div></div>
</#macro>

<#--
  Scripts de sesión de keycloak.v2: recargan la página si el login terminó en otra pestaña.
  Los usan los dos marcos (login y pantallas).
-->
<#macro scriptsSesion>
    <script type="module">
        <#outputformat "JavaScript">
        import { startSessionPolling } from ${(url.resourcesPath + "/js/authChecker.js")?c};
        startSessionPolling(${url.ssoLoginInOtherTabsUrl?c});
        </#outputformat>
    </script>
    <#if authenticationSession??>
        <script type="module">
            <#outputformat "JavaScript">
            import { checkAuthSession } from ${(url.resourcesPath + "/js/authChecker.js")?c};
            checkAuthSession(${authenticationSession.authSessionIdHash?c});
            </#outputformat>
        </script>
    </#if>
</#macro>

<#--
  Marco de las pantallas que no son el login. Cada una carga SOLO el CSS de su prototipo, porque los
  tres prototipos usan los mismos nombres de clase (.frame, .btn, .card…) con reglas distintas.
    marco="estado" -> DOC-PGRP/pgrp_pantallas_sistema.html, diseño "Contorno" (errores, avisos, 401, 429)
    marco="alta"   -> DOC-PGRP/pgrp_alta_propuestas.html, diseño A "Pasos arriba" (crear contraseña)
    marco="otp"    -> DOC-PGRP/pgrp_otp.html (código de recuperación)
  titulo: texto de la pestaña del navegador ("PGRP - <titulo>").
  La plantilla que llama dibuja el contenido de .stage.
-->
<#macro pantalla marco titulo alternativaAlta=false>
<!DOCTYPE html>
<html lang="${lang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${msg('ppaTituloPestana', titulo)}</title>
    <link href="${url.resourcesPath}/css/ppa-${marco}.css" rel="stylesheet" id="ppa-css" />
    <#if alternativaAlta>
    <#-- info.ftl: Keycloak manda el mismo mensaje tras crear la contraseña y tras cualquier otro cambio
         de cuenta. Si el navegador viene de crear la contraseña (marca ppa-clave-creada), la página usa
         el diseño del alta. Se decide aquí, antes de pintar, para que no parpadee. -->
    <script>
      try { if (sessionStorage.getItem('ppa-clave-creada') !== null) { document.documentElement.dataset.variante = 'alta'; const l = document.getElementById('ppa-css'); l.href = l.href.replace('ppa-${marco}.css', 'ppa-alta.css'); } } catch (e) { /* sin almacenamiento */ }
    </script>
    </#if>
    <link href="${url.resourcesPath}/css/ppa-paleta.css" rel="stylesheet" />
    <link href="${url.resourcesPath}/css/ppa-keycloak.css" rel="stylesheet" />
    <script type="importmap">
        { "imports": { "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js" } }
    </script>
    <@scriptsSesion/>
    <script src="${url.resourcesPath}/js/ppa-pantallas.js" defer></script>
</head>
<body data-page-id="login-${pageId}" data-marco="${marco}">
<div id="ppa-textos" hidden
     data-mostrar-clave="${msg('ppaMostrarClave')}" data-ocultar-clave="${msg('ppaOcultarClave')}"
     data-soporte-no-disponible="${msg('ppaSoporteNoDisponible')}"></div>
<#if marco == 'otp'>
<button class="tbtn tfix" type="button" id="tema" aria-label="${msg('ppaTemaAria')}"></button>
<div class="frame" id="frame" data-pal="marino" data-mode="light">
  <main class="stage"><#nested></main>
</div>
<#else>
<div class="frame" id="frame"<#if marco == 'estado'> data-pal="marino" data-layout="2"</#if> data-mode="light">
  <div class="bar"><div class="seg" hidden></div><div class="tabs"></div><button class="tbtn" type="button" id="tema" aria-label="${msg('ppaTemaAria')}"></button></div>
  <main class="stage" id="stage"><#nested></main>
</div>
<div class="toast" id="toast" role="status" hidden></div>
</#if>
</body>
</html>
</#macro>

<#--
  Botón de una pantalla de estado o del alta. accion: "url" (enlace a href), "recargar" (vuelve a
  cargar la página), "soporte" (todavía no envía nada: muestra el aviso de "no disponible").
-->
<#macro boton texto icono principal=false accion="url" href="" ocupado="">
  <#local clase = "btn " + principal?then('btn--primary', 'btn--soft')>
  <#if accion == 'url'><a class="${clase}" href="${href}"><i data-icono="${icono}"></i><span>${texto}</span></a><#else><button class="${clase}" type="button" data-accion="${accion}"<#if ocupado?has_content> data-ocupado="${ocupado}"</#if>><i data-icono="${icono}"></i><span>${texto}</span></button></#if>
</#macro>

<#--
  Cuerpo "Contorno" de ppa_pantallas_sistema.html: logo, marca grande, arte con glifo, código,
  título, texto y acciones (<#nested>). nAcciones < 2 agrega el hueco (.slot) del prototipo.
-->
<#macro contorno marca glifo codigo titulo texto nAcciones>
<section class="c-wrap"><div><img class="hlogo hl" src="${url.resourcesPath}/img/logo-claro.png" alt="${msg('ppaLogoAlt')}"><img class="hlogo hd" src="${url.resourcesPath}/img/logo-oscuro.png" alt="" aria-hidden="true"></div><div class="c-mark big outline" aria-hidden="true">${marca}</div><div class="c-body view in" id="view"><svg class="art" data-arte="${glifo}" viewBox="0 0 320 150" fill="none" aria-hidden="true"></svg><p class="code">${codigo}</p><h1>${titulo}</h1><p class="desc">${texto}</p><div class="actions"><#nested><#if nAcciones < 2><span class="slot" aria-hidden="true"></span></#if></div></div><div class="foot">${msg('ppaPie')}</div></section>
</#macro>

<#--
  Marco del diseño A "Pasos arriba" de ppa_alta_propuestas.html (VISTAS.A): logo, pasos y tarjeta.
  estados: estado de cada paso ('done', 'cur', 'bad', 'todo'); nombres: texto de cada paso.
  La plantilla que llama dibuja el contenido de la tarjeta (.card).
-->
<#macro alta estados nombres>
<div class="A in"><img class="logo l" src="${url.resourcesPath}/img/logo-claro.png" alt="${msg('ppaLogoAlt')}"><img class="logo d" src="${url.resourcesPath}/img/logo-oscuro.png" alt="" aria-hidden="true"><div class="st"><#list nombres as n><#assign st = estados[n?index]><#if n?index gt 0><span class="l <#if estados[n?index - 1] == 'done'>done</#if>"></span></#if><span class="s <#if st == 'cur' || st == 'bad'>cur</#if>"><span class="sq ${st}"><#if st == 'done'><i data-icono="check"></i><#elseif st == 'bad'><i data-icono="alerta"></i><#else>${n?index + 1}</#if></span><span data-paso="${n?index}">${n}</span></span></#list></div>
      <section class="card"><#nested></section><div class="foot">${msg('ppaPie')}</div></div>
</#macro>

<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false ppa=false>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}" lang="${lang}"<#if realm.internationalizationEnabled> dir="${(locale.rtl)?then('rtl','ltr')}"</#if>>
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${msg("loginTitle")}</title>
    <#if properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <script type="importmap">
        { "imports": { "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js" } }
    </script>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <#-- Ojo de las pantallas heredadas de keycloak.v2 (las propias usan ppa.js). -->
    <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>
    <@scriptsSesion/>
    <script src="${url.resourcesPath}/js/ppa.js" defer></script>
</head>
<body data-page-id="login-${pageId}">
<#-- Textos que usa ppa.js: salen de messages_es.properties, no del JS. -->
<div id="ppa-textos" hidden
     data-img="${url.resourcesPath}/img"
     data-arte-movilidad="${msg('ppaArteMovilidad')}" data-arte-apetito="${msg('ppaArteApetito')}"
     data-arte-animo="${msg('ppaArteAnimo')}" data-arte-registro="${msg('ppaArteRegistro')}"
     data-arte-semana="${msg('ppaArteSemana')}" data-arte-evolucion="${msg('ppaArteEvolucion')}"
     data-arte-informe="${msg('ppaArteInforme')}"
     data-mostrar-clave="${msg('ppaMostrarClave')}" data-ocultar-clave="${msg('ppaOcultarClave')}"
     data-err-correo-vacio="${msg('ppaErrCorreoVacio')}" data-err-correo-invalido="${msg('ppaErrCorreoInvalido')}"
     data-err-clave-vacia="${msg('ppaErrClaveVacia')}"></div>
<button class="tbtn tfix" type="button" data-tema aria-label="${msg('ppaTemaAria')}"></button>
<main class="frame page l2" id="frame-1" data-pal="marino" data-mode="dark"><div class="sp cl bn">
  <aside class="pan" aria-label="${msg('ppaPanelAria')}">
    <div class="mid"><span class="acc" aria-hidden="true"></span>
      <h2>${msg('ppaPanelTitulo1')} <span class="c1">${msg('ppaPanelTitulo2')}</span> ${msg('ppaPanelTitulo3')} <span class="c2">${msg('ppaPanelTitulo4')}</span>${msg('ppaPanelTitulo5')}</h2>
      <p class="lead">${msg('ppaPanelLead')}</p>
      <svg class="arte" id="arte" viewBox="0 0 480 340" fill="none" aria-hidden="true"></svg>
      <ul class="vals"><li><span data-ic="registro"></span>${msg('ppaValorRegistro')}</li><li><span data-ic="ia"></span>${msg('ppaValorIa')}</li><li><span data-ic="revision"></span>${msg('ppaValorRevision')}</li><li><span data-ic="traza"></span>${msg('ppaValorTraza')}</li></ul></div>
  </aside>
  <div class="fside"><div class="top"></div>
    <div class="mid"><div class="box"><div class="blogo"><img class="vlogo vl" src="${url.resourcesPath}/img/logo-claro.png" alt="${msg('ppaLogoAlt')}"><img class="vlogo vd" src="${url.resourcesPath}/img/logo-oscuro.png" alt="" aria-hidden="true"></div><div id="host-1"><div class="fwrap">
<#if ppa>
      <#nested "form">
<#else>
      <div class="fhead" data-antetitulo="${msg('ppaAntetitulo')}"><h2><#nested "header"></h2></div>
      <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
        <@aviso tipo=(message.type == 'error')?then('error', 'info') texto=message.summary />
      </#if>
      <div class="ppa-heredada">
        <#nested "form">
        <#if auth?has_content && auth.showTryAnotherWayLink()>
          <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post" novalidate="novalidate">
              <input type="hidden" name="tryAnotherWay" value="on"/>
              <button class="link" type="submit">${msg("doTryAnotherWay")}</button>
          </form>
        </#if>
        <#nested "socialProviders">
        <#if displayInfo><div id="kc-info"><#nested "info"></div></#if>
      </div>
</#if>
    </div></div><div class="bfoot"><span>${msg('ppaPie')}</span></div></div></div></div>
</div></main>
<div class="toast" id="toast" role="status" hidden></div>
</body>
</html>
</#macro>
