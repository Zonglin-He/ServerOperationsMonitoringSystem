# 构建阶段：打包 Vite 前端
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE=""
ENV VITE_API_BASE=${VITE_API_BASE}
RUN npm run build

# 运行阶段：Nginx 托管静态 + 反代 /api
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
# 关键：把自定义 Nginx 配置复制进去，覆盖默认站点
COPY src/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
