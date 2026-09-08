// src/environments/environment.prod.ts
//
// Mismos campos que environment.ts, pero con los valores que usaras una
// vez desplegado en AWS. Angular intercambia automaticamente este archivo
// por el de arriba cuando compilas con "ng build" (produccion es el
// default de "ng build" desde Angular 17+, ver "fileReplacements" en angular.json).
export const environment = {
  production: true,

  azureAd: {
    clientId: '0d5904de-0d7a-474d-ba0a-d8a8ea6d14f8',
    authority: 'https://login.microsoftonline.com/common',
    redirectUri: 'https://main.diml48c4gyhqd.amplifyapp.com/',
    postLogoutRedirectUri: 'https://main.diml48c4gyhqd.amplifyapp.com/',
  },

  apiScopes: ['api://0d5904de-0d7a-474d-ba0a-d8a8ea6d14f8/desarrollo/leer_y_escribir'],

  apiUrls: {
    auth: 'https://3jxo8ng50g.execute-api.us-east-1.amazonaws.com/api/v1/auth',
    catalogo: 'https://3jxo8ng50g.execute-api.us-east-1.amazonaws.com/api/v1/catalogo',
    carrito: 'https://3jxo8ng50g.execute-api.us-east-1.amazonaws.com/api/v1/carritos',
  },
};
