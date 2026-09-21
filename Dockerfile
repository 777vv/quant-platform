# 1) 前端构建
FROM node:22-alpine AS frontend
WORKDIR /web
COPY web/package*.json ./
RUN npm install
COPY web/ ./
RUN npm run build:only

# 2) 后端构建（前端产物拷入 static；跳过 frontend-maven-plugin，容器内不再装 node）
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY pom.xml ./
COPY quant-common/pom.xml quant-common/
COPY quant-system/pom.xml quant-system/
COPY quant-fund/pom.xml quant-fund/
COPY quant-strategy/pom.xml quant-strategy/
COPY quant-ai/pom.xml quant-ai/
COPY quant-web/pom.xml quant-web/
RUN mvn -B dependency:go-offline
COPY . .
COPY --from=frontend /web/dist ./quant-web/src/main/resources/static
RUN mvn -B package -DskipTests -DskipFrontend=true

# 3) 运行
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/quant-web/target/quant-web-*.jar app.jar
ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms512m -Xmx1024m" LOG_PATH=/app/logs
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
