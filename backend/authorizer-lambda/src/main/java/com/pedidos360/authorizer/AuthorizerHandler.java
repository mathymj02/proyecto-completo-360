package com.pedidos360.authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2CustomAuthorizerEvent;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.URL;
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
    private static final String TENANT_ID = System.getenv().getOrDefault(
            "AZURE_TENANT_ID", "e5372bf0-c5e3-4286-887c-79069f209c1f");
    
    // Client ID de tu aplicacion registrada
    private static final String CLIENT_ID = System.getenv().getOrDefault(
            "AZURE_CLIENT_ID", "0d5904de-0d7a-474d-ba0a-d8a8ea6d14f8");
    private static final String ISSUER = "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0";
    private static final ConfigurableJWTProcessor<SecurityContext> JWT_PROCESSOR = createProcessor();

    private static ConfigurableJWTProcessor<SecurityContext> createProcessor() {
        try {
            URL jwks = new URL("https://login.microsoftonline.com/" + TENANT_ID +
                    "/discovery/v2.0/keys");
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new RemoteJWKSet<>(jwks)));
            return processor;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo configurar JWKS de Entra ID", e);
        }
    }

    @Override
    public Map<String, Object> handleRequest(APIGatewayV2CustomAuthorizerEvent event, Context context) {
        // Obtenemos el logger de CloudWatch para ver los logs en tiempo real
        var logger = context.getLogger();
        logger.log("[AUTHORIZER] Iniciando validacion de autorizacion...");

        try {
            // 1. Extraer el encabezado Authorization de la peticion
            String authHeader = event.getHeaders() == null ? null :
                    event.getHeaders().entrySet().stream()
                            .filter(entry -> "authorization".equalsIgnoreCase(entry.getKey()))
                            .map(Map.Entry::getValue)
                            .findFirst().orElse(null);

            // Si no viene el header, o no empieza con 'Bearer ', denegamos de inmediato
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.log("[AUTHORIZER] Token ausente o formato incorrecto (falta 'Bearer ')");
                return generarRespuestaSimple(false, null, null);
            }

            // 2. Extraer el token puro quitando la palabra 'Bearer '
            String token = authHeader.substring(7).trim();

            // Nimbus verifica la firma RS256 con las claves publicas JWKS de Entra ID.
            JWTClaimsSet claims = JWT_PROCESSOR.process(token, null);

            // 4. Validar que el token no este expirado (claim 'exp')
            Date expirationTime = claims.getExpirationTime();
            Date now = new Date();
            if (expirationTime == null || !expirationTime.after(now)
                    || (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().after(now))) {
                logger.log("[AUTHORIZER] Token vencido o fuera de vigencia");
                return generarRespuestaSimple(false, null, null);
            }

            // 5. Validar el emisor (claim 'iss' de Microsoft Entra ID)
            String issuer = claims.getIssuer();
            if (!ISSUER.equals(issuer) || !claims.getAudience().contains(CLIENT_ID)) {
                logger.log("[AUTHORIZER] Issuer o audience invalido");
                return generarRespuestaSimple(false, null, null);
            }

            // 6. Extraer el usuario y sus roles asignados (App Roles)
            String username = claims.getStringClaim("preferred_username");
            if (username == null) {
                username = claims.getSubject();
            }

            List<String> roles = claims.getStringListClaim("roles");
            logger.log("[AUTHORIZER] Token valido para usuario: " + username + " con roles: " + roles);

            // 7. Firma y claims basicos validos: devolver autorizacion.
            return generarRespuestaSimple(true, username, roles != null ? String.join(",", roles) : "Usuario");

        } catch (Exception e) {
            logger.log("[AUTHORIZER] Error durante la validacion: " + e.getMessage());
            return generarRespuestaSimple(false, null, null);
        }
    }

    /**
     * Genera la respuesta en formato Simple de API Gateway HTTP API (Payload v2.0):
     * { "isAuthorized": true/false, "context": { ... } }
     */
    private Map<String, Object> generarRespuestaSimple(boolean autorizado, String usuario, String roles) {
        Map<String, Object> response = new HashMap<>();
        response.put("isAuthorized", autorizado);

        if (autorizado && usuario != null) {
            Map<String, Object> contextData = new HashMap<>();
            contextData.put("usuario", usuario);
            contextData.put("roles", roles);
            response.put("context", contextData);
        }

        return response;
    }
}
