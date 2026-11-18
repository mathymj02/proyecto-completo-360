// src/environments/environment.prod.ts
//
// Mismos campos que environment.ts, pero con los valores que usaras una
// vez desplegado en AWS. Angular intercambia automaticamente este archivo
// por el de arriba cuando compilas con "ng build" (produccion es el
// default de "ng build" desde Angular 17+, ver "fileReplacements" en angular.json).
export const environment = {
  paymentsEnabled: false,
  analyticsEnabled: false,
  // Vacio = mismo origen del frontend (requiere proxy WebSocket en Nginx).
  analyticsWsUrl: '',
  production: true,

  azureAd: {
    clientId: '0d5904de-0d7a-474d-ba0a-d8a8ea6d14f8',
    authority: 'https://login.microsoftonline.com/common',
    // El mismo build sirve desde EC2 o desde una ruta nueva del gateway.
    // Cada URL exacta debe estar registrada como SPA redirect URI en Entra.
    redirectUri: `${window.location.origin}/`,
    postLogoutRedirectUri: `${window.location.origin}/`,
  },

  apiScopes: ['api://0d5904de-0d7a-474d-ba0a-d8a8ea6d14f8/desarrollo/leer_y_escribir'],

  apiUrls: {
    auth: 'https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com/api/v1/auth',
    catalogo: 'https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com/api/v1/catalogo',
    carrito: 'https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com/api/v1/carritos',
  },
};
