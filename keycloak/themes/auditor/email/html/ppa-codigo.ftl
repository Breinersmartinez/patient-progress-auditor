<#-- Código de recuperación: lo manda la extensión ppa-codigo-recuperacion. -->
<#import "ppa-marco.ftl" as m>
<@m.correo>
<@m.titulo>${msg('ppaCodigoTitulo')}</@m.titulo>
<@m.parrafo>${msg('ppaCodigoTexto')}</@m.parrafo>
<p style="margin:16px 0 0;display:inline-block;padding:12px 20px;border-radius:6px;background:#EAF0F3;font-size:32px;line-height:1.2;font-weight:700;letter-spacing:8px;color:#0B2A44;font-variant-numeric:tabular-nums;">${codigo}</p>
<@m.parrafo chico=true>${kcSanitize(msg('ppaCodigoVence', minutos))?no_esc}</@m.parrafo>
<@m.parrafo chico=true>${msg('ppaCodigoIgnorar')}</@m.parrafo>
</@m.correo>
