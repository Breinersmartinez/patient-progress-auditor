<#--
  "Verifica tu correo" de DOC-PGRP/pgrp_pantallas_sistema.html. "Reenviar correo" usa el reenvío
  propio de Keycloak (volver a cargar url.loginAction manda el correo otra vez).
-->
<#import "template.ftl" as layout>
<@layout.pantalla marco="estado" titulo=msg('ppaVerificarTitulo')>
<@layout.contorno marca=msg('ppaMarcaVerificar') glifo="correo" codigo=msg('ppaVerificarCodigo') titulo=msg('ppaVerificarTitulo') texto=msg('ppaVerificarTexto', (user.email)!'') nAcciones=2>
<a class="btn btn--primary" href="${url.loginAction}" data-ocupado="${msg('ppaVerificarReenviando')}"><i data-icono="correo"></i><span>${msg('ppaBtnReenviarCorreo')}</span></a><@layout.boton texto=msg('ppaBtnVolverIngresar') icono="entrar" href=url.loginRestartFlowUrl/>
</@layout.contorno>
</@layout.pantalla>
