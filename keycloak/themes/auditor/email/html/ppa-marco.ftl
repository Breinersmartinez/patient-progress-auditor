<#--
  Marco común de los correos del auditor: reproduce la tarjeta de la pestaña "Correo" de
  DOC-PGRP/pgrp_alta_propuestas.html (diseño A, paleta de EPS Sanitas) (contenido centrado). Los estilos van en línea porque los clientes de correo
  ignoran las hojas de estilo.
-->
<#macro correo>
<!DOCTYPE html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body style="margin:0;padding:24px 12px;background:#E3F1FA;font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;line-height:1.5;color:#0B2A44;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;"><tr><td align="center">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:560px;border-collapse:collapse;background:#FFFFFF;border-radius:6px;box-shadow:0 3px 10px rgba(0,0,0,.12);">
<tr><td align="center" style="padding:32px 36px;text-align:center;">
<img src="${url.resourcesUrl}/img/logo-claro.png" alt="${msg('ppaLogoAlt')}" height="40" style="display:block;height:40px;width:auto;margin:0 auto;border:0;">
<#nested>
</td></tr></table>
</td></tr></table>
</body></html>
</#macro>
<#macro titulo><h1 style="margin:16px 0 0;font-size:26px;line-height:1.15;font-weight:700;color:#0B2A44;"><#nested></h1></#macro>
<#macro parrafo chico=false><p style="margin:16px 0 0;color:#4A6074;<#if chico>font-size:13px;</#if>"><#nested></p></#macro>
