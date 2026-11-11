# syntax=docker/dockerfile:1
# Build stage. npm 10 has a bug on this lockfile ("edgesOut"), so npm 11 is used.
FROM node:22 AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npx --yes npm@11 ci
COPY . .
RUN npx ng build --configuration production

# Run stage: static files behind nginx
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
