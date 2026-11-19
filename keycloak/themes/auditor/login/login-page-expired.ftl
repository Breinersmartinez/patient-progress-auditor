<#-- "La página expiró" de Keycloak con la pantalla 401 "Sesión vencida" de DOC-PGRP/pgrp_pantallas_sistema.html. -->
<#import "template.ftl" as layout>
<@layout.pantalla marco="estado" titulo=msg('ppa401Titulo')>
<@layout.contorno marca=msg('ppaMarca401') glifo="reloj" codigo=msg('ppa401Codigo') titulo=msg('ppa401Titulo') texto=msg('ppa401Texto') nAcciones=1>
<@layout.boton texto=msg('ppaBtnIniciarSesion') icono="entrar" principal=true href=url.loginRestartFlowUrl/>
</@layout.contorno>
</@layout.pantalla>
