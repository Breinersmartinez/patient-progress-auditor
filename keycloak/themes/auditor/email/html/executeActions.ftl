<#-- Invitación: el admin creó la cuenta y pidió "Update Password". -->
<#import "ppa-marco.ftl" as m>
<@m.correo>
<@m.titulo>${msg('ppaInvitacionTitulo')}</@m.titulo>
<@m.parrafo>${kcSanitize(msg('ppaInvitacionTexto', user.email!''))?no_esc}</@m.parrafo>
<p style="margin:16px 0 0;"><a href="${link}" style="display:inline-block;min-height:44px;line-height:44px;padding:0 28px;border-radius:6px;background:#0079C1;color:#FFFFFF;font-weight:600;text-decoration:none;">${msg('ppaInvitacionBoton')}</a></p>
<@m.parrafo chico=true>${kcSanitize(msg('ppaInvitacionVence', linkExpirationFormatter(linkExpiration)))?no_esc}</@m.parrafo>
<@m.parrafo chico=true>${msg('ppaInvitacionIgnorar')}</@m.parrafo>
</@m.correo>
