ARG NODE_VERSION=20-alpine
ARG FRONTEND_PORT

FROM node:${NODE_VERSION} AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

FROM node:${NODE_VERSION} AS dev
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
EXPOSE ${FRONTEND_PORT}
CMD ["sh", "-c", "npm run start -- --host 0.0.0.0 --port ${FRONTEND_PORT}"]

FROM node:${NODE_VERSION} AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build -- --configuration production

FROM nginx:1.27-alpine AS prod
COPY --from=build /app/dist/frontend/browser/ /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE ${FRONTEND_PORT}
