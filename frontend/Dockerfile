FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    --mount=type=secret,id=extra_ca \
    if [ -s /run/secrets/extra_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/extra_ca; fi \
    && npm ci --no-audit --no-fund

COPY tsconfig.json vite.config.ts index.html ./
COPY public public
COPY src src
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine

COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/snippets/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

ENV GATEWAY_URL=http://api-gateway:8080 \
    NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

EXPOSE 8080
