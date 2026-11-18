<#ftl output_format="plainText">
${msg('ppaInvitacionTitulo')}

${msg('ppaInvitacionTextoPlano', user.email!'')}

${msg('ppaInvitacionBoton')}: ${link}

${msg('ppaInvitacionVencePlano', linkExpirationFormatter(linkExpiration))}
${msg('ppaInvitacionIgnorar')}
