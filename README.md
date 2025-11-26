# App Móvil Perfumería - Documentación Completa

## 📱 Descripción del Proyecto

Aplicación móvil Android desarrollada en Kotlin para una plataforma de e-commerce de perfumes. La aplicación permite a usuarios registrarse, iniciar sesión, explorar catálogo de productos, gestionar carrito de compras y realizar pedidos. Incluye módulos separados para clientes y administradores.

## 🛠️ Tecnologías Utilizadas

- **Lenguaje:** Kotlin 100%
 - **Arquitectura:** Organización libre (Activities/Fragments, helpers, managers); sin MVVM estricto
- **Backend:** Xano (Plataforma low-code)
- **Networking:** Retrofit + OkHttp + Gson
- **Navegación:** Navigation Component
- **Asincronía:** Corrutinas de Kotlin
- **Almacenamiento:** SharedPreferences + Memoria
- **Build:** Gradle con Java 21

## 📋 Pasos de Configuración

### Configuración Android

1. **Requisitos Previos:**
   - Android Studio
   - JDK 21 instalado
   - Android SDK con API nivel 24+ 
   - Dispositivo físico o emulador Android

2. **Clonar y Configurar Proyecto:**
   ```bash
   git clone https://github.com/Xsebet12/AppMovilPerfumeria.git
   cd AppMovilPerfumeria
   ```

3. **Configurar Variables de Entorno:**
   - Asegurar que `local.properties` contiene la ruta del SDK:
   ```
   sdk.dir=C\\:\\Android\\Sdk
   ```
   - O definir variable de entorno `ANDROID_HOME`

4. **Sincronizar Dependencias:**
   - Abrir proyecto en Android Studio
   - Ejecutar `./gradlew build` o usar la opción "Sync Project with Gradle Files"

5. **Ejecutar la Aplicación:**
   - Seleccionar dispositivo/emulador
   - Ejecutar con `Run 'app'` o `./gradlew installDebug`

### Configuración Backend (Xano)

La aplicación utiliza Xano como backend. No se requiere configuración local del backend, ya que todas las APIs están alojadas en la plataforma Xano.

**URLs de Xano Configuradas:**
- **API Principal:** `https://x8ki-letl-twmt.n7.xano.io/api:cGjNNLgz/`
- **API Autenticación:** `https://x8ki-letl-twmt.n7.xano.io/api:NUzxXGzL/`

## 🔧 Variables/URLs Necesarias

### URLs de API (Configuradas en build.gradle)

```kotlin
buildConfigField("String", "XANO_BASE_URL", "\"https://x8ki-letl-twmt.n7.xano.io/api:cGjNNLgz/\"")
buildConfigField("String", "XANO_AUTH_BASE_URL", "\"https://x8ki-letl-twmt.n7.xano.io/api:NUzxXGzL/\"")
```

### Variables de Entorno (Si se requieren cambios)

Para desarrollo local, modificar en `app/build.gradle.kts`:

```kotlin
defaultConfig {
    // Cambiar URLs según entorno
    buildConfigField("String", "XANO_BASE_URL", "\"<nueva_url>\"")
    buildConfigField("String", "XANO_AUTH_BASE_URL", "\"<nueva_url_auth>\"")
}
```

## 👥 Usuarios de Prueba y Credenciales

### Usuario Administrador
- **Email:** admin@perfumeria.com
- **Contraseña:** admin123
- **Funcionalidades:** Gestión de productos, clientes, pedidos y imágenes

### Usuario Owner
- **Email:** owner@perfumeria.com
- **Contraseña:** owner123
- **Funcionalidades:** Gestión de productos, usuarios, pedidos y imágenes

### Usuario Cliente
- **Email:** cliente@demo.com  
- **Contraseña:** cliente123
- **Funcionalidades:** Compra, carrito, historial de pedidos

### Usuario Demo (Registro)
- Puede registrarse con cualquier email válido
- La aplicación valida formato de email y complejidad de contraseña
- Después del registro, login automático

## 🖼️ Almacenamiento de Imágenes

### Estrategia de Imágenes

1. **Almacenamiento Backend:**
   - Las imágenes de productos se almacenan en Xano
   - URLs generadas automáticamente por la plataforma
   - Formato: `https://x8ki-letl-twmt.n7.xano.io/vault/`
   - Subido a traves de api:
      -`https://x8ki-letl-twmt.n7.xano.io/api:cGjNNLgz/producInv`
      -`https://x8ki-letl-twmt.n7.xano.io/api:cGjNNLgz/producto_imagen`

2. **Caché Local:**
   - La aplicación utiliza `CatalogCache` para cachear imágenes
   - Mejora rendimiento y experiencia de usuario
   - Reducción de consumo de datos

3. **Gestión de Imágenes:**
   - Administradores pueden subir/editar imágenes desde la app
   - Validación de formatos y tamaños

### Estructura de Imágenes en Xano

- **Tabla:** `producto_imagen`
- **Relación:** Many-to-One con productos
- **Campos:** id, producto_id, imagen_url, imagen_principal, fecha_creacion

## 🚀 Funcionalidades Principales

### Módulo Cliente
- ✅ Registro y autenticación de usuarios
- ✅ Catálogo de productos con filtros
- ✅ Carrito de compras persistente
- ✅ Proceso de checkout completo
- ✅ Historial de pedidos
- ✅ Gestión de perfil usuario
- ✅ Seguimiento de pedidos

### Módulo Administrador  
- ✅ Gestión completa de productos (CRUD)
- ✅ Administración de imágenes de productos
- ✅ Gestión de usuarios/clientes
- ✅ Administración de pedidos
- ✅ Actualización de estados de pedidos

## 📁 Estructura del Proyecto

```
app/
├── src/main/java/com/example/apptest/
│   ├── cliente/           # Módulo cliente
│   │   ├── models/        # Modelos de datos cliente
│   │   └── services/       # Servicios API cliente
│   ├── core/              # Componentes core
│   │   ├── network/       # Configuración Red (Retrofit)
│   │   └── storage/       # Almacenamiento local
│   ├── empleado/          # Módulo administrador
│   │   └── services/      # Servicios admin
│   ├── pais/              # Datos geográficos
│   │   ├── models/        # Modelos regiones/comunas
│   │   └── services/      # Servicios geográficos
│   ├── ui/                # Interfaz de usuario
│   │   ├── cliente/       # Pantallas cliente
│   │   ├── comun/         # Pantallas comunes
│   │   └── empleado/      # Pantallas admin
│   └── user/              # Gestión usuarios
│       ├── models/        # Modelos usuario/auth
│       └── services/      # Servicios autenticación
└── res/                   # Recursos Android
    ├── layout/           # XML layouts
    ├── drawable/         # Imágenes/vectores
    └── values/           # Strings, colors, styles
```

## 🔌 APIs y Endpoints

### Autenticación (XANO_AUTH_BASE_URL)
- `POST /auth/login` - Login usuario
- `POST /auth/signup` - Registro usuario
- `GET /auth/me` - Perfil usuario actual

### Catálogo y Productos (XANO_BASE_URL)
- `GET /producInv` - Listar productos
- `GET /producInv/{id}` - Detalle producto
- `POST /producInv` - Crear producto con imágenes (admin)
- `PATCH /producInv/{producto_id}` - Editar producto parcial (admin)
- `POST /producInv/update` - Actualizar habilitado/disponible/stock (admin)
- `GET /ProducInvAdmin` - Catálogo completo (admin)
- `GET /ProducInvAdmin/{id}` - Detalle admin

### Pedidos y Seguimiento (XANO_BASE_URL)
- `POST /venta` - Crear pedido
- `GET /venta` - Listar pedidos usuario
- `GET /venta/{id}` - Detalle pedido
- `GET /seguimiento_pedido/venta/{venta_id}` - Obtener seguimiento por venta
- `PATCH /update_seguimiento_pedido` - Actualizar seguimiento

### Imágenes (XANO_BASE_URL)
- `POST /producto_imagen` - Subir imagen (admin)
- `DELETE /producto_imagen/{imagen_id}` - Eliminar imagen (admin)
- `PATCH /producto_imagen/{imagen_id}` - Marcar como principal
- `POST /producto_imagen/set_principal` - Establecer principal

## ⚙️ Configuración de Build

### Versiones Clave
```gradle
compileSdk = 36
minSdk = 24
targetSdk = 36

javaVersion = VERSION_21
jvmTarget = "21"
```

### Dependencias Principales
- AndroidX Core, AppCompat
- Material Components
- Navigation Component
- RecyclerView, ViewPager2, CardView
- Retrofit2 + Gson Converter
- OkHttp3 + Logging Interceptor
- Coroutines (core + android)
- Lifecycle (runtime + viewmodel)
- Activity KTX
- Coil (carga de imágenes)
- ViewBinding

## 🐛 Problemas Comunes

1. **Error SDK no encontrado:**
   ```bash
   # Crear/editar local.properties
   sdk.dir=C\\:\\Android\\Sdk
   ```

2. **Error de conexión con Xano:**
   - Verificar URLs en build.gradle
   - Revisar conectividad internet
   - Verificar que APIs de Xano estén activas

3. **Problemas de autenticación:**
   - Limpiar datos app: Settings → Apps → App → Storage → Clear Data
   - Verificar validez de tokens

4. **Imágenes no cargan:**
   - Verificar permisos internet
### Regiones y Comunas (XANO_BASE_URL)
- `GET /regComuna` - Listar regiones con comunas (misma base que API principal)
