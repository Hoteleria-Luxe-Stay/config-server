# Config Server - Sistema de Reservas de Hoteles

Servidor de configuración centralizada basado en **Spring Cloud Config Server**. Proporciona configuraciones externalizadas para todos los microservicios del sistema.

## Información del Servicio

| Propiedad | Valor |
|-----------|-------|
| Puerto | 8888 |
| Java | 21 |
| Spring Boot | 3.5.7 |
| Spring Cloud | 2024.0.1 |
| Modo | Native (archivos locales) |

## Estructura del Proyecto

```
config-server/
├── pom.xml
├── src/
│   └── main/
│       ├── java/com/hotel/config/
│       │   └── ConfigServerApplication.java
│       └── resources/
│           └── application.yml
└── target/
```

## Configuración

### application.yml

```yaml
server:
  port: ${SERVER_PORT:8888}

spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: ${CONFIG_REPO_PATH:file:../config-repo}
```

### Variables de Entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Puerto del servidor | `8888` |
| `CONFIG_REPO_PATH` | Ruta al repositorio de configuraciones | `file:../config-repo` |

## Endpoints

```bash
# Health Check
GET http://localhost:8888/actuator/health

# Obtener configuración de un servicio
GET http://localhost:8888/{service-name}/default
GET http://localhost:8888/{service-name}/{profile}

# Ejemplos
GET http://localhost:8888/api-gateway/default
GET http://localhost:8888/auth-service/default
GET http://localhost:8888/hotel-service/default
GET http://localhost:8888/reserva-service/default
GET http://localhost:8888/notificacion-service/default
```

---

## Docker

### Dockerfile

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copiar archivos de Maven
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Descargar dependencias
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar aplicación
RUN ./mvnw clean package -DskipTests

# Imagen de producción
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Crear usuario no-root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar JAR desde builder
COPY --from=builder /app/target/config-server-*.jar app.jar

# Puerto
EXPOSE 8888

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

# Ejecutar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Dockerfile (Simple)

```dockerfile
# Dockerfile.simple
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/config-server-*.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  config-server:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: config-server
    ports:
      - "8888:8888"
    environment:
      - SERVER_PORT=8888
      - SPRING_PROFILES_ACTIVE=native
      - SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS=file:/config-repo
    volumes:
      - ../config-repo:/config-repo:ro
    networks:
      - hotel-network
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8888/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    restart: unless-stopped

networks:
  hotel-network:
    external: true
```

### Comandos Docker

```bash
# Compilar el proyecto
./mvnw clean package -DskipTests

# Construir imagen
docker build -t config-server:latest .

# Ejecutar contenedor
docker run -d \
  --name config-server \
  -p 8888:8888 \
  -v $(pwd)/../config-repo:/config-repo:ro \
  -e SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS=file:/config-repo \
  --network hotel-network \
  config-server:latest

# Ver logs
docker logs -f config-server

# Verificar salud
curl http://localhost:8888/actuator/health

# Detener y eliminar
docker stop config-server && docker rm config-server
```

---

## Kubernetes

### Manifiestos

#### ConfigMap para config-repo

```yaml
# k8s/configmap-config-repo.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: config-repo-files
  namespace: hotel-system
data:
  api-gateway.yml: |
    # Contenido de api-gateway.yml
  auth-service.yml: |
    # Contenido de auth-service.yml
  hotel-service.yml: |
    # Contenido de hotel-service.yml
  reserva-service.yml: |
    # Contenido de reserva-service.yml
  notificacion-service.yml: |
    # Contenido de notificacion-service.yml
```

#### Deployment

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-server
  namespace: hotel-system
  labels:
    app: config-server
    version: v1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: config-server
  template:
    metadata:
      labels:
        app: config-server
        version: v1
    spec:
      containers:
        - name: config-server
          image: ${ACR_NAME}.azurecr.io/config-server:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8888
              name: http
          env:
            - name: SERVER_PORT
              value: "8888"
            - name: SPRING_PROFILES_ACTIVE
              value: "native"
            - name: SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS
              value: "file:/config-repo"
          volumeMounts:
            - name: config-repo
              mountPath: /config-repo
              readOnly: true
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8888
            initialDelaySeconds: 60
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8888
            initialDelaySeconds: 30
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /actuator/health
              port: 8888
            initialDelaySeconds: 10
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 30
      volumes:
        - name: config-repo
          configMap:
            name: config-repo-files
```

#### Service

```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: config-server
  namespace: hotel-system
  labels:
    app: config-server
spec:
  type: ClusterIP
  selector:
    app: config-server
  ports:
    - port: 8888
      targetPort: 8888
      protocol: TCP
      name: http
```

#### Horizontal Pod Autoscaler (Opcional)

```yaml
# k8s/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: config-server-hpa
  namespace: hotel-system
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: config-server
  minReplicas: 1
  maxReplicas: 3
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

### Comandos Kubernetes

```bash
# Crear namespace (si no existe)
kubectl create namespace hotel-system

# Aplicar ConfigMap con las configuraciones
kubectl apply -f k8s/configmap-config-repo.yaml

# Desplegar config-server
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Verificar despliegue
kubectl get pods -n hotel-system -l app=config-server
kubectl get svc -n hotel-system -l app=config-server

# Ver logs
kubectl logs -f deployment/config-server -n hotel-system

# Describir pod (para debug)
kubectl describe pod -l app=config-server -n hotel-system

# Port-forward para acceso local
kubectl port-forward svc/config-server 8888:8888 -n hotel-system

# Verificar endpoint
curl http://localhost:8888/actuator/health
curl http://localhost:8888/api-gateway/default
```

---

## Azure

### 1. Variables de Entorno

```bash
export RESOURCE_GROUP="rg-hotel-reservas"
export LOCATION="eastus"
export ACR_NAME="acrhotelreservas"
export AKS_CLUSTER="aks-hotel-reservas"
```

### 2. Construir y Subir Imagen a ACR

```bash
# Login en ACR
az acr login --name $ACR_NAME

# Opción 1: Build local y push
./mvnw clean package -DskipTests
docker build -t $ACR_NAME.azurecr.io/config-server:v1.0.0 .
docker push $ACR_NAME.azurecr.io/config-server:v1.0.0

# Opción 2: Build en ACR (recomendado)
az acr build \
  --registry $ACR_NAME \
  --image config-server:v1.0.0 \
  --image config-server:latest \
  .

# Verificar imagen
az acr repository show-tags \
  --name $ACR_NAME \
  --repository config-server \
  --output table
```

### 3. Crear ConfigMap desde Archivos Locales

```bash
# Crear ConfigMap con los archivos de config-repo
kubectl create configmap config-repo-files \
  --namespace hotel-system \
  --from-file=api-gateway.yml=../config-repo/api-gateway.yml \
  --from-file=auth-service.yml=../config-repo/auth-service.yml \
  --from-file=hotel-service.yml=../config-repo/hotel-service.yml \
  --from-file=reserva-service.yml=../config-repo/reserva-service.yml \
  --from-file=notificacion-service.yml=../config-repo/notificacion-service.yml
```

### 4. Deployment en AKS

```yaml
# k8s/azure-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-server
  namespace: hotel-system
spec:
  replicas: 2
  selector:
    matchLabels:
      app: config-server
  template:
    metadata:
      labels:
        app: config-server
    spec:
      containers:
        - name: config-server
          image: acrhotelreservas.azurecr.io/config-server:v1.0.0
          ports:
            - containerPort: 8888
          env:
            - name: SERVER_PORT
              value: "8888"
            - name: SPRING_PROFILES_ACTIVE
              value: "native"
            - name: SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS
              value: "file:/config-repo"
          volumeMounts:
            - name: config-repo
              mountPath: /config-repo
              readOnly: true
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8888
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8888
            initialDelaySeconds: 30
            periodSeconds: 5
      volumes:
        - name: config-repo
          configMap:
            name: config-repo-files
```

### 5. Desplegar

```bash
# Aplicar manifiestos
kubectl apply -f k8s/azure-deployment.yaml
kubectl apply -f k8s/service.yaml

# Verificar
kubectl get pods -n hotel-system -l app=config-server
kubectl get svc -n hotel-system -l app=config-server

# Ver logs
kubectl logs -f deployment/config-server -n hotel-system
```

### 6. Azure DevOps Pipeline

```yaml
# azure-pipelines.yml
trigger:
  branches:
    include:
      - main
  paths:
    include:
      - config-server/**

variables:
  dockerRegistryServiceConnection: 'acr-connection'
  imageRepository: 'config-server'
  containerRegistry: 'acrhotelreservas.azurecr.io'
  dockerfilePath: 'config-server/Dockerfile'
  tag: '$(Build.BuildId)'

pool:
  vmImage: 'ubuntu-latest'

stages:
  - stage: Build
    displayName: 'Build and Push'
    jobs:
      - job: Build
        steps:
          - task: Maven@3
            displayName: 'Maven Package'
            inputs:
              mavenPomFile: 'config-server/pom.xml'
              goals: 'clean package'
              options: '-DskipTests'
              javaHomeOption: 'JDKVersion'
              jdkVersionOption: '1.21'

          - task: Docker@2
            displayName: 'Build and Push Image'
            inputs:
              command: buildAndPush
              repository: $(imageRepository)
              dockerfile: $(dockerfilePath)
              containerRegistry: $(dockerRegistryServiceConnection)
              tags: |
                $(tag)
                latest

  - stage: Deploy
    displayName: 'Deploy to AKS'
    dependsOn: Build
    jobs:
      - deployment: Deploy
        environment: 'production'
        strategy:
          runOnce:
            deploy:
              steps:
                - task: KubernetesManifest@0
                  displayName: 'Deploy to Kubernetes'
                  inputs:
                    action: deploy
                    kubernetesServiceConnection: 'aks-connection'
                    namespace: hotel-system
                    manifests: |
                      config-server/k8s/deployment.yaml
                      config-server/k8s/service.yaml
                    containers: |
                      $(containerRegistry)/$(imageRepository):$(tag)
```

### 7. Alternativa: Azure App Configuration

Para entornos de producción en Azure, considera usar **Azure App Configuration** como alternativa al Config Server:

```bash
# Crear Azure App Configuration
az appconfig create \
  --name appconfig-hotel-reservas \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard

# Importar configuraciones
az appconfig kv import \
  --name appconfig-hotel-reservas \
  --source file \
  --path ../config-repo/api-gateway.yml \
  --format yaml \
  --prefix "api-gateway/" \
  --yes

# Obtener connection string
az appconfig credential list \
  --name appconfig-hotel-reservas \
  --resource-group $RESOURCE_GROUP
```

---

## Troubleshooting

### Problemas Comunes

**1. Config Server no encuentra archivos de configuración**
```bash
# Verificar volumen montado
kubectl exec -it deployment/config-server -n hotel-system -- ls -la /config-repo

# Verificar variable de entorno
kubectl exec -it deployment/config-server -n hotel-system -- env | grep CONFIG
```

**2. Servicios no pueden conectar al Config Server**
```bash
# Verificar que el servicio esté corriendo
kubectl get svc config-server -n hotel-system

# Probar conectividad desde otro pod
kubectl run test-curl --image=curlimages/curl -it --rm -- \
  curl http://config-server:8888/actuator/health
```

**3. Puerto ya en uso**
```bash
# Docker
docker ps | grep 8888
docker stop <container_id>

# Kubernetes
kubectl get svc -A | grep 8888
```

**4. OutOfMemory en contenedor**
```yaml
# Aumentar límites de memoria
resources:
  limits:
    memory: "1Gi"
```

### Logs y Debugging

```bash
# Docker
docker logs config-server --tail 100 -f

# Kubernetes
kubectl logs -f deployment/config-server -n hotel-system --tail=100

# Eventos de Kubernetes
kubectl get events -n hotel-system --field-selector involvedObject.name=config-server
```

---

## Ejecución Local

```bash
# Compilar
./mvnw clean package -DskipTests

# Ejecutar
java -jar target/config-server-1.0.0-SNAPSHOT.jar

# O con Maven
./mvnw spring-boot:run

# Verificar
curl http://localhost:8888/actuator/health
curl http://localhost:8888/api-gateway/default
```
