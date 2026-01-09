# Build stage
FROM maven:3.8.4-jdk-8-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM openjdk:8-jre-slim

# 安装技能运行所需的运行时和基础工具
RUN apt-get update && apt-get install -y \
    bash \
    python3 \
    coreutils \
    sed \
    grep \
    findutils \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app
COPY --from=build /app/target/file-sec-box-0.0.1-SNAPSHOT.jar app.jar

# 创建技能存储根目录 (对应 WebIDE 挂载点)
RUN mkdir -p /webIde/product/skill

# 暴露端口
EXPOSE 8080

# 以 Root 权限运行 (默认即为 Root)
ENTRYPOINT ["java", "-jar", "app.jar"]
