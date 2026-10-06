package com.pedidos360.authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2CustomAuthorizerEvent;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AuthorizerHandler: Implementa RequestHandler de AWS Lambda.
 * API Gateway invoca handleRequest() cada vez que llega una peticion HTTP.
 */
public class AuthorizerHandler implements RequestHandler<APIGatewayV2CustomAuthorizerEvent, Map<String, Object>> {

    // Tenant ID oficial de Microsoft Entra ID de tu proyecto
    private static final String TENANT_ID = "e5372bf0-c5e3-4286-887c-79069f209c1f";
    
    // Client ID de tu aplicacion registrada
    private static final String CLIENT_ID = "0d5904de-0d7a-474d-ba0a-d8a8ea6d14f8";

    @Override
    public Map<String, Object> handleRequest(APIGatewayV2CustomAuthorizerEvent event, Context context) {
        // Obtenemos el logger de CloudWatch para ver los logs en tiempo real
        var logger = context.getLogger();
        logger.log(\"[AUTHORIZER] Iniciando validacion de autorizacion...\");

        try {
            // 1. Extraer el encabezado Authorization de la peticion
            String authHeader = null;
            if (event.getHeaders() != null) {
                authHeader = event.getHeaders().get(\"authorization\");
                if (authHeader == null) {
                    authHeader = event.getHeaders().get(\"Authorization\");
                }
            }

            // Si no viene el header, o no empieza con 'Bearer ', denegamos de inmediato
            if (authHeader == null || !authHeader.startsWith(\"Bearer \")) {
                logger.log(\"[AUTHORIZER] Token ausente o formato incorrecto (falta 'Bearer ')\");
                return generarRespuestaSimple(false, null, null);
            }

            // 2. Extraer el token puro quitando la palabra 'Bearer '
            String token = authHeader.substring(7).trim();

            // 3. Parsear el JWT criptograficamente
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // 4. Validar que el token no este expirado (claim 'exp')
            Date expirationTime = claims.getExpirationTime();
            Date now = new Date();
            if (expirationTime != null && now.after(expirationTime)) {
                logger.log(\"[AUTHORIZER] Token expirado en fecha: \" + expirationTime);
                return generarRespuestaSimple(false, null, null);
            }

            // 5. Validar el emisor (claim 'iss' de Microsoft Entra ID)
            String issuer = claims.getIssuer();
            if (issuer == null || !issuer.contains(TENANT_ID)) {
                logger.log(\"[AUTHORIZER] Issuer invalido: \" + issuer);
                return generarRespuestaSimple(false, null, null);
            }

            // 6. Extraer el usuario y sus roles asignados (App Roles)
            String username = claims.getStringClaim(\"preferred_username\");
            if (username == null) {
                username = claims.getSubject();
            }

            List<String> roles = claims.getStringListClaim(\"roles\");
            logger.log(\"[AUTHORIZER] Token valido para usuario: \" + username + \" con roles: \" + roles);

            // 7. Token 100% valido: Devolvemos autorizacion exitosa con contexto de usuario
            return generarRespuestaSimple(true, username, roles != null ? String.join(\",\", roles) : \"Usuario\");

        } catch (Exception e) {
            logger.log(\"[AUTHORIZER] Error durante la validacion: \" + e.getMessage());
            return generarRespuestaSimple(false, null, null);
        }
    }

    /**
     * Genera la respuesta en formato Simple de API Gateway HTTP API (Payload v2.0):
     * { \"isAuthorized\": true/false, \"context\": { ... } }
     */
    private Map<String, Object> generarRespuestaSimple(boolean autorizado, String usuario, String roles) {
        Map<String, Object> response = new HashMap<>();
        response.put(\"isAuthorized\", autorizado);

        if (autorizado && usuario != null) {
            Map<String, Object> contextData = new HashMap<>();
            contextData.put(\"usuario\", usuario);
            contextData.put(\"roles\", roles);
            response.put(\"context\", contextData);
        }

        return response;
    }
}
