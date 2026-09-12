# ================= 构建阶段：容器内用 Maven 编译（不依赖本地 target） =================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 配置阿里云 Maven 镜像，加速国内依赖下载
RUN mkdir -p /root/.m2 && printf '%s\n' \
  '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
  '  <mirrors><mirror>' \
  '    <id>aliyun</id><mirrorOf>central</mirrorOf>' \
  '    <name>Aliyun Maven Public</name>' \
  '    <url>https://maven.aliyun.com/repository/public</url>' \
  '  </mirror></mirrors></settings>' > /root/.m2/settings.xml

# 先拷 pom 预热依赖缓存（源码变更时这一层可复用）
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true

# 拷源码编译打包
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ================= 运行阶段：仅 JRE，体积小 =================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /build/target/yue-backend-1.0.0.jar /app/app.jar

EXPOSE 8080

# 适配 1.6~2G 单机：限制堆与元空间
ENV JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
