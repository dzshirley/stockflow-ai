FROM node:24-alpine AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@11.25.0 --activate

ARG VITE_API_BASE_URL=""
ARG VITE_REGISTRATION_ENABLED=true
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_REGISTRATION_ENABLED=$VITE_REGISTRATION_ENABLED

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:1.29-alpine
COPY infra/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
