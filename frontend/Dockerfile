# -------------------------------------------------------------
# ETAPA 1: Compilación del código fuente (TypeScript -> HTML/JS)
# Usamos una imagen de Node 20 en Alpine (muy liviana) y le damos el alias 'builder'
# -------------------------------------------------------------
FROM node:20-alpine AS builder

# WORKDIR: Define la carpeta de trabajo dentro del contenedor
WORKDIR /app

# COPY package.json: Copiamos primero la lista de dependencias
# Buena práctica DevOps: Al copiar primero package.json, Docker guarda en cache las librerías
COPY package.json package-lock.json ./

# RUN npm ci: Instala exactamente las versiones del lockfile sin sorpresas
RUN npm ci --legacy-peer-deps

# COPY .: Copiamos todo el resto del código fuente (componentes, servicios, HTML, CSS)
COPY . .

# RUN npm run build: Ejecuta 'ng build', que convierte TypeScript a JavaScript minificado
RUN npm run build

# -------------------------------------------------------------
# ETAPA 2: Servidor Web de Producción con Nginx
# Descartamos Node.js para que no consuma memoria y usamos solo Nginx (servidor web rápido)
# -------------------------------------------------------------
FROM nginx:alpine

# COPY --from=builder: Traemos SOLO los archivos compilados de la etapa 1 a la carpeta pública de Nginx
COPY --from=builder /app/dist/pedidos360-frontend/browser /usr/share/nginx/html

# Copiamos nuestra configuración de Nginx para manejar rutas SPA (Single Page Application)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# EXPOSE 80: Indicamos que Nginx escuchará en el puerto web estándar HTTP
EXPOSE 80

# CMD: Arranca el servidor Nginx en primer plano (daemon off) para que el contenedor no se apague
CMD ["nginx", "-g", "daemon off;"]
