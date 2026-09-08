// src/environments/environment.ts
//
// Centraliza TODOS los valores que cambian entre entornos (local vs
// producción en AWS): datos de tu App Registration en Azure y las URLs
// de los 3 microservicios backend. Angular reemplaza este archivo por
// environment.prod.ts automáticamente cuando compilas con --configuration
// production (ver angular.json, sección "fileReplacements").
//
// IMPORTANTE: reemplaza los valores de aca con los de TU PROPIA App
// Registration (la que creaste con soporte multi-tenant), no la del profe.
export const environment = {
  production: false,

  azureAd: {
    // Application (client) ID de TU App Registration (matias-mena-holamundov1).
    clientId: '0d5904de-0d7a-474d-ba0a-d8a8ea6d14f8',
    authority: 'https://login.microsoftonline.com/common',
    redirectUri: 'http://localhost:4200/',
    postLogoutRedirectUri: 'http://localhost:4200/',
  },

  // Application ID URI real: api://0d5904de-.../desarrollo
  // Nombre del scope real: leer_y_escribir (no "write-read")
  apiScopes: ['api://0d5904de-0d7a-474d-ba0a-d8a8ea6d14f8/desarrollo/leer_y_escribir'],
  apiUrls: {
    auth: 'https://3jxo8ng50g.execute-api.us-east-1.amazonaws.com/api/v1/auth',
    catalogo: 'https://3jxo8ng50g.execute-api.us-east-1.amazonaws.com/api/v1/catalogo',
    carrito: 'https://3jxo8ng50g.execute-api.us-east-1.amazonaws.com/api/v1/carritos',
  },

  // URLs de tus 3 microservicios Spring Boot corriendo en local.

};
